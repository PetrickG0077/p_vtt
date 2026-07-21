package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VttAssetSyncCompletePayload(String syncId, int transferredFiles)
        implements CustomPacketPayload {
    public static final Type<VttAssetSyncCompletePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "asset_sync_complete")
    );
    public static final StreamCodec<ByteBuf, VttAssetSyncCompletePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(36), VttAssetSyncCompletePayload::syncId,
                    ByteBufCodecs.VAR_INT, VttAssetSyncCompletePayload::transferredFiles,
                    VttAssetSyncCompletePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
