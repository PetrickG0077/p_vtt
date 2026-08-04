package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Completion marker sent after every update belonging to one atomic paste. */
public record VttSceneClipboardPasteResultPayload(
        String requestId, boolean success, String message
) implements CustomPacketPayload {
    public static final Type<VttSceneClipboardPasteResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "scene_clipboard_paste_result"));
    public static final StreamCodec<ByteBuf, VttSceneClipboardPasteResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64),
                    VttSceneClipboardPasteResultPayload::requestId,
                    ByteBufCodecs.BOOL, VttSceneClipboardPasteResultPayload::success,
                    ByteBufCodecs.stringUtf8(256),
                    VttSceneClipboardPasteResultPayload::message,
                    VttSceneClipboardPasteResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
