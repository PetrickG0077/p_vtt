package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One client request that places an entire attachment hierarchy atomically. */
public record VttCompositeAttachmentPlacementPayload(
        long authorityRevision, String sceneId, String placementJson
) implements CustomPacketPayload {
    public static final int MAX_JSON_LENGTH = 1_000_000;
    public static final Type<VttCompositeAttachmentPlacementPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "composite_attachment_placement"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            VttCompositeAttachmentPlacementPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public VttCompositeAttachmentPlacementPayload decode(RegistryFriendlyByteBuf buffer) {
            return new VttCompositeAttachmentPlacementPayload(
                    buffer.readVarLong(), buffer.readUtf(128),
                    buffer.readUtf(MAX_JSON_LENGTH));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer,
                           VttCompositeAttachmentPlacementPayload payload) {
            buffer.writeVarLong(payload.authorityRevision());
            buffer.writeUtf(payload.sceneId(), 128);
            buffer.writeUtf(payload.placementJson(), MAX_JSON_LENGTH);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
