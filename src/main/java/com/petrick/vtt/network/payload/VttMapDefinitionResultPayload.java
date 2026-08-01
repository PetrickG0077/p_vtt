package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Explicit server acknowledgement for a map-definition lifecycle request. */
public record VttMapDefinitionResultPayload(
        String requestId, boolean success, String code, String message,
        long authorityRevision, String definitionId
) implements CustomPacketPayload {
    public static final String OK = "OK";
    public static final String STALE_REVISION = "STALE_REVISION";
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String REJECTED = "REJECTED";

    public static final Type<VttMapDefinitionResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "map_definition_result"));
    public static final StreamCodec<ByteBuf, VttMapDefinitionResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64), VttMapDefinitionResultPayload::requestId,
                    ByteBufCodecs.BOOL, VttMapDefinitionResultPayload::success,
                    ByteBufCodecs.stringUtf8(32), VttMapDefinitionResultPayload::code,
                    ByteBufCodecs.stringUtf8(256), VttMapDefinitionResultPayload::message,
                    ByteBufCodecs.VAR_LONG, VttMapDefinitionResultPayload::authorityRevision,
                    ByteBufCodecs.stringUtf8(256), VttMapDefinitionResultPayload::definitionId,
                    VttMapDefinitionResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
