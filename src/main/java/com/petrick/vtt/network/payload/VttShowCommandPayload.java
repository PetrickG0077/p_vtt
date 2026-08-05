package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Master command for fullscreen image presentations. */
public record VttShowCommandPayload(String operation, String relativePath, long positionMillis)
        implements CustomPacketPayload {
    public static final String SHOW = "SHOW";
    public static final String CLOSE = "CLOSE";
    public static final String PLAY = "PLAY";
    public static final String PAUSE = "PAUSE";
    public static final String SEEK = "SEEK";
    public static final String PRELOAD = "PRELOAD";
    public static final String RESET = "RESET";
    public static final String TOGGLE_LOOP = "TOGGLE_LOOP";
    public static final String END = "END";
    public static final String RESTART = "RESTART";
    public static final String FORCE_SHOW = "FORCE_SHOW";
    public static final Type<VttShowCommandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "show_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VttShowCommandPayload>
            STREAM_CODEC = new StreamCodec<>() {
        @Override public VttShowCommandPayload decode(RegistryFriendlyByteBuf buffer) {
            return new VttShowCommandPayload(buffer.readUtf(16), buffer.readUtf(512), buffer.readLong());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, VttShowCommandPayload value) {
            buffer.writeUtf(value.operation(), 16);
            buffer.writeUtf(value.relativePath(), 512);
            buffer.writeLong(value.positionMillis());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
