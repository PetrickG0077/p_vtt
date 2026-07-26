package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-confirmed presentation state sent to non-master clients. */
public record VttPresentationUpdatePayload(
        String operation, boolean blackout, double cameraX, double cameraY, double cameraZoom
) implements CustomPacketPayload {
    public static final Type<VttPresentationUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "presentation_update")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VttPresentationUpdatePayload>
            STREAM_CODEC = new StreamCodec<>() {
        @Override
        public VttPresentationUpdatePayload decode(RegistryFriendlyByteBuf buffer) {
            return new VttPresentationUpdatePayload(
                    buffer.readUtf(16), buffer.readBoolean(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }

        @Override
        public void encode(
                RegistryFriendlyByteBuf buffer, VttPresentationUpdatePayload payload
        ) {
            buffer.writeUtf(payload.operation(), 16);
            buffer.writeBoolean(payload.blackout());
            buffer.writeDouble(payload.cameraX());
            buffer.writeDouble(payload.cameraY());
            buffer.writeDouble(payload.cameraZoom());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
