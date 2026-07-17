package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VttTokenLifecycleRequestPayload(
        String operation, String objectId, String objectJson) implements CustomPacketPayload {
    public static final Type<VttTokenLifecycleRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "token_lifecycle_request"));
    public static final StreamCodec<ByteBuf, VttTokenLifecycleRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, VttTokenLifecycleRequestPayload::operation,
            ByteBufCodecs.STRING_UTF8, VttTokenLifecycleRequestPayload::objectId,
            ByteBufCodecs.STRING_UTF8, VttTokenLifecycleRequestPayload::objectJson,
            VttTokenLifecycleRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
