package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-confirmed music state broadcast to every connected client. */
public record VttMusicUpdatePayload(
        String operation, String relativePath, double positionSeconds,
        boolean playing, boolean paused, boolean loop,
        float trackVolume, float masterVolume
) implements CustomPacketPayload {
    public static final Type<VttMusicUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "music_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VttMusicUpdatePayload>
            STREAM_CODEC = new StreamCodec<>() {
        @Override
        public VttMusicUpdatePayload decode(RegistryFriendlyByteBuf buffer) {
            return new VttMusicUpdatePayload(buffer.readUtf(16), buffer.readUtf(512),
                    buffer.readDouble(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readFloat(), buffer.readFloat());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, VttMusicUpdatePayload payload) {
            buffer.writeUtf(payload.operation(), 16);
            buffer.writeUtf(payload.relativePath(), 512);
            buffer.writeDouble(payload.positionSeconds());
            buffer.writeBoolean(payload.playing());
            buffer.writeBoolean(payload.paused());
            buffer.writeBoolean(payload.loop());
            buffer.writeFloat(payload.trackVolume());
            buffer.writeFloat(payload.masterVolume());
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
