package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VttTokenDefinitionUpsertPayload(
        String requestId, long authorityRevision, String tokenJson)
        implements CustomPacketPayload {
    public static final int MAX_JSON_LENGTH = 262_144;
    public static final Type<VttTokenDefinitionUpsertPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "token_definition_upsert")
    );
    public static final StreamCodec<ByteBuf, VttTokenDefinitionUpsertPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), VttTokenDefinitionUpsertPayload::requestId,
            ByteBufCodecs.VAR_LONG, VttTokenDefinitionUpsertPayload::authorityRevision,
            ByteBufCodecs.stringUtf8(MAX_JSON_LENGTH), VttTokenDefinitionUpsertPayload::tokenJson,
            VttTokenDefinitionUpsertPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
