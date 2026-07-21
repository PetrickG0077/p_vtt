package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VttAssetChunkPayload(
        String syncId, int fileIndex, int chunkIndex, int chunkCount, byte[] data
) implements CustomPacketPayload {
    public static final int MAX_CHUNK_BYTES = 512 * 1024;
    public static final Type<VttAssetChunkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "asset_chunk")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VttAssetChunkPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public VttAssetChunkPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new VttAssetChunkPayload(
                            buffer.readUtf(36), buffer.readVarInt(), buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readByteArray(MAX_CHUNK_BYTES)
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, VttAssetChunkPayload payload) {
                    buffer.writeUtf(payload.syncId(), 36);
                    buffer.writeVarInt(payload.fileIndex());
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
