package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One ordered fragment of a compressed scene snapshot. */
public record VttSceneSnapshotChunkPayload(
        String transferId, int chunkIndex, int chunkCount, byte[] data
) implements CustomPacketPayload {
    public static final int MAX_CHUNK_BYTES = 256 * 1024;

    public static final Type<VttSceneSnapshotChunkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "scene_snapshot_chunk"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VttSceneSnapshotChunkPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public VttSceneSnapshotChunkPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new VttSceneSnapshotChunkPayload(
                            buffer.readUtf(VttSceneSnapshotStartPayload.MAX_TRANSFER_ID_LENGTH),
                            buffer.readVarInt(), buffer.readVarInt(),
                            buffer.readByteArray(MAX_CHUNK_BYTES));
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer, VttSceneSnapshotChunkPayload payload
                ) {
                    buffer.writeUtf(payload.transferId(),
                            VttSceneSnapshotStartPayload.MAX_TRANSFER_ID_LENGTH);
                    buffer.writeVarInt(payload.chunkIndex());
                    buffer.writeVarInt(payload.chunkCount());
                    buffer.writeByteArray(payload.data());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
