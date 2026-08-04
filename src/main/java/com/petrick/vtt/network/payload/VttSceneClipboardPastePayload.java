package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Atomic request for pasting a forest of tokens, attachments and lights. */
public record VttSceneClipboardPastePayload(
        long authorityRevision, String sceneId, String clipboardJson
) implements CustomPacketPayload {
    public static final int MAX_JSON_LENGTH = 2_000_000;
    public static final Type<VttSceneClipboardPastePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "scene_clipboard_paste"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            VttSceneClipboardPastePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public VttSceneClipboardPastePayload decode(RegistryFriendlyByteBuf buffer) {
            return new VttSceneClipboardPastePayload(buffer.readVarLong(),
                    buffer.readUtf(128), buffer.readUtf(MAX_JSON_LENGTH));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer,
                           VttSceneClipboardPastePayload payload) {
            buffer.writeVarLong(payload.authorityRevision());
            buffer.writeUtf(payload.sceneId(), 128);
            buffer.writeUtf(payload.clipboardJson(), MAX_JSON_LENGTH);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
