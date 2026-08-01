package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Explicit server acknowledgement for an Asset Manager filesystem mutation. */
public record VttAssetFolderResultPayload(
        String requestId,
        boolean success,
        String code,
        String message,
        long authorityRevision
) implements CustomPacketPayload {
    public static final String OK = "OK";
    public static final String STALE_REVISION = "STALE_REVISION";
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String REJECTED = "REJECTED";

    public static final Type<VttAssetFolderResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "asset_folder_result"));

    public static final StreamCodec<ByteBuf, VttAssetFolderResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64), VttAssetFolderResultPayload::requestId,
                    ByteBufCodecs.BOOL, VttAssetFolderResultPayload::success,
                    ByteBufCodecs.stringUtf8(32), VttAssetFolderResultPayload::code,
                    ByteBufCodecs.stringUtf8(256), VttAssetFolderResultPayload::message,
                    ByteBufCodecs.VAR_LONG, VttAssetFolderResultPayload::authorityRevision,
                    VttAssetFolderResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
