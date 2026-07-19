package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VttTokenTransformUpdatePayload(
        long authorityRevision, long entityRevision, long clientSequence,
        String sceneId, String objectId, double x, double y, double rotationDegrees,
        double scaleX, double scaleY, int layerIndex,
        boolean flippedHorizontally, String activeStateId,
        String originPlayerId, boolean accepted
) implements CustomPacketPayload {
    public static final Type<VttTokenTransformUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "token_transform_update")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VttTokenTransformUpdatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public VttTokenTransformUpdatePayload decode(RegistryFriendlyByteBuf buffer) {
                    return new VttTokenTransformUpdatePayload(
                            buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(),
                            buffer.readUtf(128), buffer.readUtf(128),
                            buffer.readDouble(), buffer.readDouble(),
                            buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readVarInt(),
                            buffer.readBoolean(), buffer.readUtf(128), buffer.readUtf(64), buffer.readBoolean());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, VttTokenTransformUpdatePayload payload) {
                    buffer.writeVarLong(payload.authorityRevision());
                    buffer.writeVarLong(payload.entityRevision());
                    buffer.writeVarLong(payload.clientSequence());
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
                    buffer.writeUtf(payload.originPlayerId(), 64);
                    buffer.writeBoolean(payload.accepted());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
