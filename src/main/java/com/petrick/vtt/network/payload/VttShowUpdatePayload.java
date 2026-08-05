package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-confirmed fullscreen presentation state. */
public record VttShowUpdatePayload(
        String relativePath, boolean active, boolean playing, boolean loop,
        long positionMillis, long changedAtMillis
)
        implements CustomPacketPayload {
    public static final Type<VttShowUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "show_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VttShowUpdatePayload>
            STREAM_CODEC = new StreamCodec<>() {
        @Override public VttShowUpdatePayload decode(RegistryFriendlyByteBuf buffer) {
            return new VttShowUpdatePayload(
                    buffer.readUtf(512), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readLong(), buffer.readLong());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, VttShowUpdatePayload value) {
            buffer.writeUtf(value.relativePath(), 512);
            buffer.writeBoolean(value.active());
            buffer.writeBoolean(value.playing());
            buffer.writeBoolean(value.loop());
            buffer.writeLong(value.positionMillis());
            buffer.writeLong(value.changedAtMillis());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
