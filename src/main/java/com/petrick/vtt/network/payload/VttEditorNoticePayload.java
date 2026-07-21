package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Short server-authored editor feedback shown above the opaque VTT screen. */
public record VttEditorNoticePayload(String message) implements CustomPacketPayload {
    public static final int MAX_MESSAGE_LENGTH = 256;
    public static final Type<VttEditorNoticePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "editor_notice"));
    public static final StreamCodec<ByteBuf, VttEditorNoticePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_MESSAGE_LENGTH),
                    VttEditorNoticePayload::message,
                    VttEditorNoticePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
