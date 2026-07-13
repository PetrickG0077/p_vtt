package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VttAssetSyncCompletePayload() implements CustomPacketPayload {
    public static final Type<VttAssetSyncCompletePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "asset_sync_complete")
    );
    public static final StreamCodec<ByteBuf, VttAssetSyncCompletePayload> STREAM_CODEC = StreamCodec.unit(
            new VttAssetSyncCompletePayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
