package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VttEnvironmentStateUpdatePayload(String doorsJson, String fogJson)
        implements CustomPacketPayload {
    public static final Type<VttEnvironmentStateUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "environment_state_update"));
    public static final StreamCodec<ByteBuf, VttEnvironmentStateUpdatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, VttEnvironmentStateUpdatePayload::doorsJson,
            ByteBufCodecs.STRING_UTF8, VttEnvironmentStateUpdatePayload::fogJson,
            VttEnvironmentStateUpdatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
