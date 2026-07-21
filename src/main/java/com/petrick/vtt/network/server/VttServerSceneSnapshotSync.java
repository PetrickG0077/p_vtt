package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttSceneSnapshotChunkPayload;
import com.petrick.vtt.network.payload.VttSceneSnapshotCompletePayload;
import com.petrick.vtt.network.payload.VttSceneSnapshotStartPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/** Encodes and sends an authoritative scene snapshot as bounded compressed chunks. */
public final class VttServerSceneSnapshotSync {
    private VttServerSceneSnapshotSync() {}

    public static boolean sendToPlayer(ServerPlayer player, VttServerTabletopState state) {
        if (player == null || state == null) return false;
        VttServerTabletopState.SceneSnapshotData snapshot = state.createSnapshotData(player);
        try {
            byte[] uncompressed = encode(snapshot.tabletopJson(), snapshot.sceneJson());
            if (uncompressed.length > VttSceneSnapshotStartPayload.MAX_UNCOMPRESSED_BYTES) {
                VTT.LOGGER.error("Skipped oversized VTT scene snapshot for {}: {} uncompressed bytes",
                        player.getGameProfile().getName(), uncompressed.length);
                return false;
            }
            byte[] compressed = gzip(uncompressed);
            if (compressed.length > VttSceneSnapshotStartPayload.MAX_COMPRESSED_BYTES) {
                VTT.LOGGER.error("Skipped oversized VTT scene snapshot for {}: {} compressed bytes",
                        player.getGameProfile().getName(), compressed.length);
                return false;
            }

            int chunkCount = Math.max(1,
                    (compressed.length + VttSceneSnapshotChunkPayload.MAX_CHUNK_BYTES - 1)
                            / VttSceneSnapshotChunkPayload.MAX_CHUNK_BYTES);
            if (chunkCount > VttSceneSnapshotStartPayload.MAX_CHUNK_COUNT) {
                VTT.LOGGER.error("Skipped VTT scene snapshot with too many chunks for {}: {}",
                        player.getGameProfile().getName(), chunkCount);
                return false;
            }

            String transferId = UUID.randomUUID().toString();
            PacketDistributor.sendToPlayer(player, new VttSceneSnapshotStartPayload(
                    transferId, snapshot.authorityRevision(), chunkCount,
                    compressed.length, uncompressed.length, sha256(compressed)));
            for (int index = 0; index < chunkCount; index++) {
                int from = index * VttSceneSnapshotChunkPayload.MAX_CHUNK_BYTES;
                int to = Math.min(compressed.length,
                        from + VttSceneSnapshotChunkPayload.MAX_CHUNK_BYTES);
                PacketDistributor.sendToPlayer(player, new VttSceneSnapshotChunkPayload(
                        transferId, index, chunkCount, Arrays.copyOfRange(compressed, from, to)));
            }
            PacketDistributor.sendToPlayer(
                    player, new VttSceneSnapshotCompletePayload(transferId));
            VTT.LOGGER.debug(
                    "Sent VTT scene snapshot {} to {}: {} -> {} bytes in {} chunk(s)",
                    transferId, player.getGameProfile().getName(), uncompressed.length,
                    compressed.length, chunkCount);
            return true;
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to encode VTT scene snapshot for {}",
                    player.getGameProfile().getName(), exception);
            return false;
        }
    }

    private static byte[] encode(String tabletopJson, String sceneJson) throws IOException {
        byte[] tabletop = tabletopJson.getBytes(StandardCharsets.UTF_8);
        byte[] scene = sceneJson.getBytes(StandardCharsets.UTF_8);
        long totalBytes = (long) Integer.BYTES * 2L + tabletop.length + scene.length;
        if (totalBytes > VttSceneSnapshotStartPayload.MAX_UNCOMPRESSED_BYTES) {
            throw new IOException("Scene snapshot exceeds the uncompressed size limit");
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(
                (int) totalBytes);
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(tabletop.length);
            output.write(tabletop);
            output.writeInt(scene.length);
            output.write(scene);
            output.flush();
            return bytes.toByteArray();
        }
    }

    private static byte[] gzip(byte[] bytes) throws IOException {
        try (ByteArrayOutputStream compressed = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(bytes);
            gzip.finish();
            return compressed.toByteArray();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
