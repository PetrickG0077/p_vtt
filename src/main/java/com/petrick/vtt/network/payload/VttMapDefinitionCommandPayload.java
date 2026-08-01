package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Master-only lifecycle command for a server-owned map definition. */
public record VttMapDefinitionCommandPayload(String operation, String definitionId)
        implements CustomPacketPayload {
    public static final String DUPLICATE = "DUPLICATE";
    public static final String DELETE = "DELETE";
    public static final Type<VttMapDefinitionCommandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "map_definition_command"));
    public static final StreamCodec<ByteBuf, VttMapDefinitionCommandPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(16), VttMapDefinitionCommandPayload::operation,
                    ByteBufCodecs.stringUtf8(256), VttMapDefinitionCommandPayload::definitionId,
                    VttMapDefinitionCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
