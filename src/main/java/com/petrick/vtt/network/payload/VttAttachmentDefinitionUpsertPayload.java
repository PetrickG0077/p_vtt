package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Master-only request to create or edit a server-owned attachment definition. */
public record VttAttachmentDefinitionUpsertPayload(
        String requestId, long authorityRevision, String attachmentJson, String folder)
        implements CustomPacketPayload {
    public static final int MAX_JSON_LENGTH = 4_096;
    public static final Type<VttAttachmentDefinitionUpsertPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "attachment_definition_upsert"));
    public static final StreamCodec<ByteBuf, VttAttachmentDefinitionUpsertPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64), VttAttachmentDefinitionUpsertPayload::requestId,
                    ByteBufCodecs.VAR_LONG, VttAttachmentDefinitionUpsertPayload::authorityRevision,
                    ByteBufCodecs.stringUtf8(MAX_JSON_LENGTH),
                    VttAttachmentDefinitionUpsertPayload::attachmentJson,
                    ByteBufCodecs.stringUtf8(256), VttAttachmentDefinitionUpsertPayload::folder,
                    VttAttachmentDefinitionUpsertPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
