package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Master-only lifecycle command for a server-owned attachment definition. */
public record VttAttachmentDefinitionCommandPayload(
        String requestId, long authorityRevision, String operation, String definitionId)
        implements CustomPacketPayload {
    public static final String DUPLICATE = "DUPLICATE";
    public static final String DELETE = "DELETE";
    public static final Type<VttAttachmentDefinitionCommandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "attachment_definition_command"));
    public static final StreamCodec<ByteBuf, VttAttachmentDefinitionCommandPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64), VttAttachmentDefinitionCommandPayload::requestId,
                    ByteBufCodecs.VAR_LONG, VttAttachmentDefinitionCommandPayload::authorityRevision,
                    ByteBufCodecs.stringUtf8(16), VttAttachmentDefinitionCommandPayload::operation,
                    ByteBufCodecs.stringUtf8(256), VttAttachmentDefinitionCommandPayload::definitionId,
                    VttAttachmentDefinitionCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
