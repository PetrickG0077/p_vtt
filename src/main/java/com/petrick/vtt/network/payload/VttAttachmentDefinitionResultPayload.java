package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server acknowledgement for an attachment-definition lifecycle request. */
public record VttAttachmentDefinitionResultPayload(
        String requestId, boolean success, String code, String message,
        long authorityRevision, String definitionId
) implements CustomPacketPayload {
    public static final String OK = "OK";
    public static final String STALE_REVISION = "STALE_REVISION";
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String REJECTED = "REJECTED";

    public static final Type<VttAttachmentDefinitionResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "attachment_definition_result"));
    public static final StreamCodec<ByteBuf, VttAttachmentDefinitionResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64), VttAttachmentDefinitionResultPayload::requestId,
                    ByteBufCodecs.BOOL, VttAttachmentDefinitionResultPayload::success,
                    ByteBufCodecs.stringUtf8(32), VttAttachmentDefinitionResultPayload::code,
                    ByteBufCodecs.stringUtf8(256), VttAttachmentDefinitionResultPayload::message,
                    ByteBufCodecs.VAR_LONG, VttAttachmentDefinitionResultPayload::authorityRevision,
                    ByteBufCodecs.stringUtf8(256), VttAttachmentDefinitionResultPayload::definitionId,
                    VttAttachmentDefinitionResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
