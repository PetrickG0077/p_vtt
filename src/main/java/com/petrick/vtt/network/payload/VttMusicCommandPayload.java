package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Master-authored music transport command. */
public record VttMusicCommandPayload(
        String operation, String relativePath, double positionSeconds,
        boolean loop, float trackVolume, float masterVolume
) implements CustomPacketPayload {
    public static final String PLAY = "PLAY";
    public static final String PAUSE = "PAUSE";
    public static final String STOP = "STOP";
    public static final String SEEK = "SEEK";
    public static final String LOOP = "LOOP";
    public static final String VOLUME = "VOLUME";

    public static final Type<VttMusicCommandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "music_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VttMusicCommandPayload>
            STREAM_CODEC = new StreamCodec<>() {
        @Override
        public VttMusicCommandPayload decode(RegistryFriendlyByteBuf buffer) {
            return new VttMusicCommandPayload(buffer.readUtf(16), buffer.readUtf(512),
                    buffer.readDouble(), buffer.readBoolean(), buffer.readFloat(),
                    buffer.readFloat());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, VttMusicCommandPayload payload) {
            buffer.writeUtf(payload.operation(), 16);
            buffer.writeUtf(payload.relativePath(), 512);
            buffer.writeDouble(payload.positionSeconds());
            buffer.writeBoolean(payload.loop());
            buffer.writeFloat(payload.trackVolume());
            buffer.writeFloat(payload.masterVolume());
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
