package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Master-only request to create or edit a server-owned map definition. */
public record VttMapDefinitionUpsertPayload(
        String requestId, long authorityRevision, String mapJson, String folder)
        implements CustomPacketPayload {
    public static final int MAX_JSON_LENGTH = 4_096;
    public static final Type<VttMapDefinitionUpsertPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "map_definition_upsert"));
    public static final StreamCodec<ByteBuf, VttMapDefinitionUpsertPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64), VttMapDefinitionUpsertPayload::requestId,
                    ByteBufCodecs.VAR_LONG, VttMapDefinitionUpsertPayload::authorityRevision,
                    ByteBufCodecs.stringUtf8(MAX_JSON_LENGTH),
                    VttMapDefinitionUpsertPayload::mapJson,
                    ByteBufCodecs.stringUtf8(256), VttMapDefinitionUpsertPayload::folder,
                    VttMapDefinitionUpsertPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
