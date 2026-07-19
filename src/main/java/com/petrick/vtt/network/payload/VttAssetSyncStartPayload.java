package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VttAssetSyncStartPayload(int fileCount, boolean clearExisting) implements CustomPacketPayload {
    public static final Type<VttAssetSyncStartPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "asset_sync_start")
    );
    public static final StreamCodec<ByteBuf, VttAssetSyncStartPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VttAssetSyncStartPayload::fileCount,
            ByteBufCodecs.BOOL, VttAssetSyncStartPayload::clearExisting,
            VttAssetSyncStartPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
