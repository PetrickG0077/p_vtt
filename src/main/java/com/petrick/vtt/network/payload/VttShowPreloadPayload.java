package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests every client to warm its local cached copy of a show video. */
public record VttShowPreloadPayload(String relativePath) implements CustomPacketPayload {
    public static final Type<VttShowPreloadPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "show_preload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VttShowPreloadPayload>
            STREAM_CODEC = new StreamCodec<>() {
        @Override public VttShowPreloadPayload decode(RegistryFriendlyByteBuf buffer) {
            return new VttShowPreloadPayload(buffer.readUtf(512));
        }

        @Override public void encode(RegistryFriendlyByteBuf buffer,
                                     VttShowPreloadPayload value) {
            buffer.writeUtf(value.relativePath(), 512);
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
