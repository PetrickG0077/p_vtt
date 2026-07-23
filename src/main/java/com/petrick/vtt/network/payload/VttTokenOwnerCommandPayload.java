package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Master request to assign or clear the owner of one placed scene token. */
public record VttTokenOwnerCommandPayload(
        long authorityRevision,
        String sceneId,
        String objectId,
        String ownerId
) implements CustomPacketPayload {
    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_OWNER_ID_LENGTH = 64;
    public static final Type<VttTokenOwnerCommandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "token_owner_command")
    );
    public static final StreamCodec<ByteBuf, VttTokenOwnerCommandPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, VttTokenOwnerCommandPayload::authorityRevision,
                    ByteBufCodecs.stringUtf8(MAX_ID_LENGTH), VttTokenOwnerCommandPayload::sceneId,
                    ByteBufCodecs.stringUtf8(MAX_ID_LENGTH), VttTokenOwnerCommandPayload::objectId,
                    ByteBufCodecs.stringUtf8(MAX_OWNER_ID_LENGTH),
                    VttTokenOwnerCommandPayload::ownerId,
                    VttTokenOwnerCommandPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
