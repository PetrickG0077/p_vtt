package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client request for a private authoritative snapshot after detecting a revision gap. */
public record VttReplicationResyncRequestPayload(
        long authorityRevision, String sceneId,
        long lastVisionRevision, long lastReplicationRevision, String reason
) implements CustomPacketPayload {
    public static final Type<VttReplicationResyncRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "replication_resync_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VttReplicationResyncRequestPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public VttReplicationResyncRequestPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new VttReplicationResyncRequestPayload(
                            buffer.readVarLong(), buffer.readUtf(128), buffer.readLong(),
                            buffer.readLong(), buffer.readUtf(64));
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer, VttReplicationResyncRequestPayload payload
                ) {
                    buffer.writeVarLong(payload.authorityRevision());
                    buffer.writeUtf(payload.sceneId(), 128);
                    buffer.writeLong(payload.lastVisionRevision());
                    buffer.writeLong(payload.lastReplicationRevision());
                    buffer.writeUtf(payload.reason(), 64);
                }
            };

    public VttReplicationResyncRequestPayload {
        sceneId = sceneId == null ? "" : sceneId;
        reason = reason == null || reason.isBlank() ? "UNKNOWN" : reason;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
