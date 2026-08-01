package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Explicit server acknowledgement for a token-definition lifecycle request. */
public record VttTokenDefinitionResultPayload(
        String requestId, boolean success, String code, String message,
        long authorityRevision, String definitionId
) implements CustomPacketPayload {
    public static final String OK = "OK";
    public static final String STALE_REVISION = "STALE_REVISION";
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String REJECTED = "REJECTED";

    public static final Type<VttTokenDefinitionResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "token_definition_result"));
    public static final StreamCodec<ByteBuf, VttTokenDefinitionResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64), VttTokenDefinitionResultPayload::requestId,
                    ByteBufCodecs.BOOL, VttTokenDefinitionResultPayload::success,
                    ByteBufCodecs.stringUtf8(32), VttTokenDefinitionResultPayload::code,
                    ByteBufCodecs.stringUtf8(256), VttTokenDefinitionResultPayload::message,
                    ByteBufCodecs.VAR_LONG, VttTokenDefinitionResultPayload::authorityRevision,
                    ByteBufCodecs.stringUtf8(256), VttTokenDefinitionResultPayload::definitionId,
                    VttTokenDefinitionResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
