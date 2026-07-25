package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Master request to enable or disable VTT spectator mode for a connected player. */
public record VttPlayerModeCommandPayload(
        long authorityRevision, String targetPlayerId, boolean spectator
) implements CustomPacketPayload {
    public static final Type<VttPlayerModeCommandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "player_mode_command")
    );
    public static final StreamCodec<ByteBuf, VttPlayerModeCommandPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, VttPlayerModeCommandPayload::authorityRevision,
                    ByteBufCodecs.stringUtf8(64), VttPlayerModeCommandPayload::targetPlayerId,
                    ByteBufCodecs.BOOL, VttPlayerModeCommandPayload::spectator,
                    VttPlayerModeCommandPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
