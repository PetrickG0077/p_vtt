package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Master-authored command for temporary presentation controls. */
public record VttPresentationCommandPayload(
        long authorityRevision, String operation, double cameraX, double cameraY, double cameraZoom
) implements CustomPacketPayload {
    public static final String TOGGLE_BLACKOUT = "BLACKOUT";
    public static final String SYNC_CAMERA = "CAMERA";

    public static final Type<VttPresentationCommandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "presentation_command")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VttPresentationCommandPayload>
            STREAM_CODEC = new StreamCodec<>() {
        @Override
        public VttPresentationCommandPayload decode(RegistryFriendlyByteBuf buffer) {
            return new VttPresentationCommandPayload(
                    buffer.readVarLong(), buffer.readUtf(16),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }

        @Override
        public void encode(
                RegistryFriendlyByteBuf buffer, VttPresentationCommandPayload payload
        ) {
            buffer.writeVarLong(payload.authorityRevision());
            buffer.writeUtf(payload.operation(), 16);
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
