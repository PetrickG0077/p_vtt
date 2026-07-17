package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VttTokenLifecycleUpdatePayload(
        String operation, String requestedObjectId, String objectId,
        String objectJson, String originPlayerId) implements CustomPacketPayload {
    public static final Type<VttTokenLifecycleUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "token_lifecycle_update"));
    public static final StreamCodec<ByteBuf, VttTokenLifecycleUpdatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, VttTokenLifecycleUpdatePayload::operation,
            ByteBufCodecs.STRING_UTF8, VttTokenLifecycleUpdatePayload::requestedObjectId,
            ByteBufCodecs.STRING_UTF8, VttTokenLifecycleUpdatePayload::objectId,
            ByteBufCodecs.STRING_UTF8, VttTokenLifecycleUpdatePayload::objectJson,
            ByteBufCodecs.STRING_UTF8, VttTokenLifecycleUpdatePayload::originPlayerId,
            VttTokenLifecycleUpdatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
