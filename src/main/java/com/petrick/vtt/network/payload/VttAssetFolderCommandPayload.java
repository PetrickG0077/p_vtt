package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Master request for authoritative physical Asset Manager folder operations. */
public record VttAssetFolderCommandPayload(
        long authorityRevision,
        String operation,
        String section,
        String source,
        String value
) implements CustomPacketPayload {
    public static final String CREATE_FOLDER = "CREATE_FOLDER";
    public static final String RENAME_FOLDER = "RENAME_FOLDER";
    public static final String DUPLICATE_FOLDER = "DUPLICATE_FOLDER";
    public static final String MOVE_FOLDER = "MOVE_FOLDER";
    public static final String MOVE_ITEM = "MOVE_ITEM";
    public static final String MOVE_SELECTION = "MOVE_SELECTION";
    public static final String DELETE_FOLDER = "DELETE_FOLDER";
    public static final String MOVE_CONTENTS_AND_DELETE_FOLDER =
            "FLATTEN_FOLDER";

    public static final Type<VttAssetFolderCommandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "asset_folder_command"));

    public static final StreamCodec<ByteBuf, VttAssetFolderCommandPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, VttAssetFolderCommandPayload::authorityRevision,
                    ByteBufCodecs.stringUtf8(24), VttAssetFolderCommandPayload::operation,
                    ByteBufCodecs.stringUtf8(16), VttAssetFolderCommandPayload::section,
                    ByteBufCodecs.stringUtf8(32767), VttAssetFolderCommandPayload::source,
                    ByteBufCodecs.stringUtf8(256), VttAssetFolderCommandPayload::value,
                    VttAssetFolderCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
