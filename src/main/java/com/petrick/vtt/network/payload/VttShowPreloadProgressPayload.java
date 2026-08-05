package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client progress report for the currently prepared show video. */
public record VttShowPreloadProgressPayload(
        String relativePath, String status, float progress
) implements CustomPacketPayload {
    public static final Type<VttShowPreloadProgressPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "show_preload_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VttShowPreloadProgressPayload>
            STREAM_CODEC = new StreamCodec<>() {
        @Override public VttShowPreloadProgressPayload decode(RegistryFriendlyByteBuf buffer) {
            return new VttShowPreloadProgressPayload(
                    buffer.readUtf(512), buffer.readUtf(16), buffer.readFloat());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer,
                                     VttShowPreloadProgressPayload value) {
            buffer.writeUtf(value.relativePath(), 512);
            buffer.writeUtf(value.status(), 16);
            buffer.writeFloat(value.progress());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
