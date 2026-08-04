package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Atomic request to remove clipboard roots and their complete hierarchy. */
public record VttSceneClipboardCutPayload(
        String requestId, long authorityRevision, String sceneId,
        String objectIdsJson, String lightIdsJson
) implements CustomPacketPayload {
    public static final int MAX_IDS_JSON_LENGTH = 262_144;
    public static final Type<VttSceneClipboardCutPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "scene_clipboard_cut"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            VttSceneClipboardCutPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public VttSceneClipboardCutPayload decode(RegistryFriendlyByteBuf buffer) {
            return new VttSceneClipboardCutPayload(buffer.readUtf(64), buffer.readVarLong(),
                    buffer.readUtf(128), buffer.readUtf(MAX_IDS_JSON_LENGTH),
                    buffer.readUtf(MAX_IDS_JSON_LENGTH));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer,
                           VttSceneClipboardCutPayload payload) {
            buffer.writeUtf(payload.requestId(), 64);
            buffer.writeVarLong(payload.authorityRevision());
            buffer.writeUtf(payload.sceneId(), 128);
            buffer.writeUtf(payload.objectIdsJson(), MAX_IDS_JSON_LENGTH);
            buffer.writeUtf(payload.lightIdsJson(), MAX_IDS_JSON_LENGTH);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
