package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VttTokenTransformRequestPayload(
        String sceneId, String objectId, double x, double y, double rotationDegrees,
        double scaleX, double scaleY, int layerIndex,
        boolean flippedHorizontally, String activeStateId, boolean bypassCollision
) implements CustomPacketPayload {
    public static final Type<VttTokenTransformRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "token_transform_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VttTokenTransformRequestPayload> STREAM_CODEC = codec();

    private static StreamCodec<RegistryFriendlyByteBuf, VttTokenTransformRequestPayload> codec() {
        return new StreamCodec<>() {
            @Override
            public VttTokenTransformRequestPayload decode(RegistryFriendlyByteBuf buffer) {
                return new VttTokenTransformRequestPayload(buffer.readUtf(128), buffer.readUtf(128),
                        buffer.readDouble(), buffer.readDouble(),
                        buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readVarInt(),
                        buffer.readBoolean(), buffer.readUtf(128), buffer.readBoolean());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, VttTokenTransformRequestPayload payload) {
                buffer.writeUtf(payload.sceneId(), 128);
                buffer.writeUtf(payload.objectId(), 128);
                buffer.writeDouble(payload.x());
                buffer.writeDouble(payload.y());
                buffer.writeDouble(payload.rotationDegrees());
                buffer.writeDouble(payload.scaleX());
                buffer.writeDouble(payload.scaleY());
                buffer.writeVarInt(payload.layerIndex());
                buffer.writeBoolean(payload.flippedHorizontally());
                buffer.writeUtf(payload.activeStateId(), 128);
                buffer.writeBoolean(payload.bypassCollision());
            }
        };
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
