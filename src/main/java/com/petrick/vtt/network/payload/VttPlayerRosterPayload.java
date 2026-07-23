package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Complete connected-player roster, sent by the authoritative server. */
public record VttPlayerRosterPayload(String rosterJson) implements CustomPacketPayload {
    public static final Type<VttPlayerRosterPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "player_roster")
    );
    public static final StreamCodec<ByteBuf, VttPlayerRosterPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    VttPlayerRosterPayload::rosterJson,
                    VttPlayerRosterPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
