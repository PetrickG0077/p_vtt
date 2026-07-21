package com.petrick.vtt.network.client;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttReplicationResyncRequestPayload;
import com.petrick.vtt.network.payload.VttSceneSnapshotChunkPayload;
import com.petrick.vtt.network.payload.VttSceneSnapshotCompletePayload;
import com.petrick.vtt.network.payload.VttSceneSnapshotStartPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/** Reassembles and validates one authoritative scene snapshot before it is applied. */
public final class VttClientSceneSnapshotReceiver {
    private static final long TRANSFER_TIMEOUT_MS = 30_000L;
    private static final long RECOVERY_INTERVAL_MS = 2_000L;
    private static IncomingTransfer incoming;
    private static long lastRecoveryRequestAt;

    private VttClientSceneSnapshotReceiver() {}

    public static synchronized void begin(VttSceneSnapshotStartPayload payload) {
        if (!validStart(payload)) {
            reject("invalid snapshot metadata", payload == null ? -1L : payload.authorityRevision());
            return;
        }
        incoming = new IncomingTransfer(payload, System.currentTimeMillis());
        VTT.LOGGER.debug("Receiving VTT scene snapshot {} in {} chunk(s)",
                payload.transferId(), payload.chunkCount());
    }

    public static synchronized void accept(VttSceneSnapshotChunkPayload payload) {
        if (incoming == null || payload == null
                || !incoming.transferId.equals(payload.transferId())) return;
        if (expired(incoming, System.currentTimeMillis())) {
            reject("snapshot transfer timed out", incoming.authorityRevision);
            return;
        }
        if (!incoming.accept(payload, System.currentTimeMillis())) {
            reject("invalid or out-of-order snapshot chunk", incoming.authorityRevision);
        }
    }

    public static synchronized DecodedSnapshot finish(VttSceneSnapshotCompletePayload payload) {
        if (incoming == null || payload == null
                || !incoming.transferId.equals(payload.transferId())) return null;
        IncomingTransfer completed = incoming;
        incoming = null;
        try {
            if (expired(completed, System.currentTimeMillis()) || !completed.complete()) {
                throw new IOException("Incomplete or expired snapshot transfer");
            }
            byte[] compressed = completed.bytes();
            if (!sha256(compressed).equalsIgnoreCase(completed.sha256)) {
                throw new IOException("Snapshot SHA-256 mismatch");
            }
            byte[] uncompressed = gunzip(compressed, completed.uncompressedBytes);
            DecodedSnapshot snapshot = decode(uncompressed, completed.authorityRevision);
            VTT.LOGGER.info("Received VTT scene snapshot {}: {} -> {} bytes",
                    completed.transferId, compressed.length, uncompressed.length);
            return snapshot;
        } catch (IOException | RuntimeException exception) {
            VTT.LOGGER.error("Rejected VTT scene snapshot {}",
                    completed.transferId, exception);
            requestRecovery(completed.authorityRevision, "SNAPSHOT_INVALID");
            return null;
        }
    }

    public static synchronized void tick() {
        if (incoming != null && expired(incoming, System.currentTimeMillis())) {
            reject("snapshot transfer timed out", incoming.authorityRevision);
        }
    }

    public static synchronized void reset() {
        incoming = null;
        lastRecoveryRequestAt = 0L;
    }

    public static synchronized void recoverAfterApplyFailure(long authorityRevision) {
        requestRecovery(authorityRevision, "SNAPSHOT_APPLY_FAILED");
    }

    private static boolean validStart(VttSceneSnapshotStartPayload payload) {
        if (payload == null || payload.transferId() == null
                || payload.authorityRevision() <= 0L
                || payload.chunkCount() <= 0
                || payload.chunkCount() > VttSceneSnapshotStartPayload.MAX_CHUNK_COUNT
                || payload.compressedBytes() <= 0
                || payload.compressedBytes() > VttSceneSnapshotStartPayload.MAX_COMPRESSED_BYTES
                || payload.uncompressedBytes() < Integer.BYTES * 2
                || payload.uncompressedBytes() > VttSceneSnapshotStartPayload.MAX_UNCOMPRESSED_BYTES
                || payload.sha256() == null || !payload.sha256().matches("[0-9a-fA-F]{64}")) {
            return false;
        }
        int expectedChunks = Math.max(1,
                (payload.compressedBytes() + VttSceneSnapshotChunkPayload.MAX_CHUNK_BYTES - 1)
                        / VttSceneSnapshotChunkPayload.MAX_CHUNK_BYTES);
        if (payload.chunkCount() != expectedChunks) return false;
        try {
            UUID.fromString(payload.transferId());
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean expired(IncomingTransfer transfer, long now) {
        return now - transfer.lastActivityAt > TRANSFER_TIMEOUT_MS;
    }

    private static void reject(String reason, long authorityRevision) {
        incoming = null;
        VTT.LOGGER.warn("Rejected VTT scene snapshot: {}", reason);
        requestRecovery(authorityRevision, "SNAPSHOT_TRANSFER_FAILED");
    }

    private static void requestRecovery(long authorityRevision, String reason) {
        long now = System.currentTimeMillis();
        if (now - lastRecoveryRequestAt < RECOVERY_INTERVAL_MS
                || Minecraft.getInstance().getConnection() == null) return;
        lastRecoveryRequestAt = now;
        var session = VTT.getApplication().getActiveSession();
        String sceneId = session.getActiveScene() == null ? "" : session.getActiveScene().getId();
        PacketDistributor.sendToServer(new VttReplicationResyncRequestPayload(
                Math.max(0L, authorityRevision), sceneId, -1L, -1L, reason));
    }

    private static byte[] gunzip(byte[] compressed, int expectedBytes) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream output = new ByteArrayOutputStream(
                     Math.min(expectedBytes, 1024 * 1024))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = gzip.read(buffer)) >= 0) {
                if (read == 0) continue;
                if (output.size() + read > expectedBytes
                        || output.size() + read > VttSceneSnapshotStartPayload.MAX_UNCOMPRESSED_BYTES) {
                    throw new IOException("Snapshot expands beyond declared size");
                }
                output.write(buffer, 0, read);
            }
            if (output.size() != expectedBytes) {
                throw new IOException("Snapshot uncompressed size mismatch");
            }
            return output.toByteArray();
        }
    }

    private static DecodedSnapshot decode(byte[] bytes, long authorityRevision) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int tabletopLength = input.readInt();
            if (tabletopLength <= 0 || tabletopLength > input.available() - Integer.BYTES) {
                throw new IOException("Invalid tabletop JSON length");
            }
            byte[] tabletop = input.readNBytes(tabletopLength);
            int sceneLength = input.readInt();
            if (sceneLength <= 0 || sceneLength != input.available()) {
                throw new IOException("Invalid scene JSON length");
            }
            byte[] scene = input.readNBytes(sceneLength);
            if (input.available() != 0) throw new IOException("Trailing snapshot data");
            return new DecodedSnapshot(
                    new String(tabletop, StandardCharsets.UTF_8),
                    new String(scene, StandardCharsets.UTF_8), authorityRevision);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record DecodedSnapshot(
            String tabletopJson, String sceneJson, long authorityRevision
    ) {}

    private static final class IncomingTransfer {
        private final String transferId;
        private final long authorityRevision;
        private final int chunkCount;
        private final int compressedBytes;
        private final int uncompressedBytes;
        private final String sha256;
        private final ByteArrayOutputStream output;
        private int nextChunk;
        private long lastActivityAt;

        private IncomingTransfer(VttSceneSnapshotStartPayload payload, long now) {
            transferId = payload.transferId();
            authorityRevision = payload.authorityRevision();
            chunkCount = payload.chunkCount();
            compressedBytes = payload.compressedBytes();
            uncompressedBytes = payload.uncompressedBytes();
            sha256 = payload.sha256();
            output = new ByteArrayOutputStream(
                    Math.min(compressedBytes, VttSceneSnapshotChunkPayload.MAX_CHUNK_BYTES));
            lastActivityAt = now;
        }

        private boolean accept(VttSceneSnapshotChunkPayload payload, long now) {
            if (payload.data() == null || payload.data().length <= 0
                    || payload.data().length > VttSceneSnapshotChunkPayload.MAX_CHUNK_BYTES
                    || payload.chunkCount() != chunkCount || payload.chunkIndex() != nextChunk
                    || output.size() + payload.data().length > compressedBytes) return false;
            output.writeBytes(payload.data());
            nextChunk++;
            lastActivityAt = now;
            return true;
        }

        private boolean complete() {
            return nextChunk == chunkCount && output.size() == compressedBytes;
        }

        private byte[] bytes() {
            return output.toByteArray();
        }
    }
}
