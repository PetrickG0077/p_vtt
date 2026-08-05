package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-aggregated preload state, primarily displayed to masters. */
public record VttShowPreloadStatusPayload(
        String relativePath, String statusesJson
) implements CustomPacketPayload {
    public static final Type<VttShowPreloadStatusPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "show_preload_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VttShowPreloadStatusPayload>
            STREAM_CODEC = new StreamCodec<>() {
        @Override public VttShowPreloadStatusPayload decode(RegistryFriendlyByteBuf buffer) {
            return new VttShowPreloadStatusPayload(
                    buffer.readUtf(512), buffer.readUtf(32_767));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer,
                                     VttShowPreloadStatusPayload value) {
            buffer.writeUtf(value.relativePath(), 512);
            buffer.writeUtf(value.statusesJson(), 32_767);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
