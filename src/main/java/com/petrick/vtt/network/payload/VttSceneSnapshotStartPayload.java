package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Announces one bounded, compressed scene snapshot transfer. */
public record VttSceneSnapshotStartPayload(
        String transferId, long authorityRevision, int chunkCount,
        int compressedBytes, int uncompressedBytes, String sha256
) implements CustomPacketPayload {
    public static final int MAX_TRANSFER_ID_LENGTH = 36;
    public static final int MAX_CHUNK_COUNT = 128;
    public static final int MAX_COMPRESSED_BYTES = 32 * 1024 * 1024;
    public static final int MAX_UNCOMPRESSED_BYTES = 128 * 1024 * 1024;

    public static final Type<VttSceneSnapshotStartPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "scene_snapshot_start"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VttSceneSnapshotStartPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public VttSceneSnapshotStartPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new VttSceneSnapshotStartPayload(
                            buffer.readUtf(MAX_TRANSFER_ID_LENGTH), buffer.readVarLong(),
                            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                            buffer.readUtf(64));
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer, VttSceneSnapshotStartPayload payload
                ) {
                    buffer.writeUtf(payload.transferId(), MAX_TRANSFER_ID_LENGTH);
                    buffer.writeVarLong(payload.authorityRevision());
                    buffer.writeVarInt(payload.chunkCount());
                    buffer.writeVarInt(payload.compressedBytes());
                    buffer.writeVarInt(payload.uncompressedBytes());
                    buffer.writeUtf(payload.sha256(), 64);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
