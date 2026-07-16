package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VttEnvironmentStateRequestPayload(
        String wallsJson, String doorsJson, String fogJson, String visionJson)
        implements CustomPacketPayload {
    public static final Type<VttEnvironmentStateRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "environment_state_request"));
    public static final StreamCodec<ByteBuf, VttEnvironmentStateRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, VttEnvironmentStateRequestPayload::wallsJson,
            ByteBufCodecs.STRING_UTF8, VttEnvironmentStateRequestPayload::doorsJson,
            ByteBufCodecs.STRING_UTF8, VttEnvironmentStateRequestPayload::fogJson,
            ByteBufCodecs.STRING_UTF8, VttEnvironmentStateRequestPayload::visionJson,
            VttEnvironmentStateRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
