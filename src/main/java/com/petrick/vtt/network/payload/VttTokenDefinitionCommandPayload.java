package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Master-only lifecycle command for a server-owned token definition. */
public record VttTokenDefinitionCommandPayload(
        String requestId, long authorityRevision, String operation, String definitionId)
        implements CustomPacketPayload {
    public static final String DUPLICATE = "DUPLICATE";
    public static final String DELETE = "DELETE";

    public static final Type<VttTokenDefinitionCommandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "token_definition_command")
    );

    public static final StreamCodec<ByteBuf, VttTokenDefinitionCommandPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64), VttTokenDefinitionCommandPayload::requestId,
                    ByteBufCodecs.VAR_LONG, VttTokenDefinitionCommandPayload::authorityRevision,
                    ByteBufCodecs.stringUtf8(16), VttTokenDefinitionCommandPayload::operation,
                    ByteBufCodecs.stringUtf8(256), VttTokenDefinitionCommandPayload::definitionId,
                    VttTokenDefinitionCommandPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
