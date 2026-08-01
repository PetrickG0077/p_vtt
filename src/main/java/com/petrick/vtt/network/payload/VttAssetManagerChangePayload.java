package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Announces an authoritative Asset Manager change to other connected masters. */
public record VttAssetManagerChangePayload(
        long authorityRevision,
        String operation,
        String section,
        String actorName,
        String message
) implements CustomPacketPayload {
    public static final Type<VttAssetManagerChangePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "asset_manager_change"));

    public static final StreamCodec<ByteBuf, VttAssetManagerChangePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, VttAssetManagerChangePayload::authorityRevision,
                    ByteBufCodecs.stringUtf8(24), VttAssetManagerChangePayload::operation,
                    ByteBufCodecs.stringUtf8(16), VttAssetManagerChangePayload::section,
                    ByteBufCodecs.stringUtf8(64), VttAssetManagerChangePayload::actorName,
                    ByteBufCodecs.stringUtf8(256), VttAssetManagerChangePayload::message,
                    VttAssetManagerChangePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
