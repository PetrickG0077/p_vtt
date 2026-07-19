package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-confirmed environment mutation broadcast to every client. */
public record VttEnvironmentCommandUpdatePayload(
        long authorityRevision, long entityRevision, long clientSequence,
        String operation, String sceneId, String entityType, String entityId,
        String entityJson, String originPlayerId)
        implements CustomPacketPayload {
    public static final Type<VttEnvironmentCommandUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "environment_command_update")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VttEnvironmentCommandUpdatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public VttEnvironmentCommandUpdatePayload decode(RegistryFriendlyByteBuf buffer) {
                    return new VttEnvironmentCommandUpdatePayload(
                            buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(),
                            buffer.readUtf(16), buffer.readUtf(128), buffer.readUtf(24),
                            buffer.readUtf(128), buffer.readUtf(VttEnvironmentCommandPayload.MAX_JSON_LENGTH),
                            buffer.readUtf(64));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                                   VttEnvironmentCommandUpdatePayload payload) {
                    buffer.writeVarLong(payload.authorityRevision());
                    buffer.writeVarLong(payload.entityRevision());
                    buffer.writeVarLong(payload.clientSequence());
                    buffer.writeUtf(payload.operation(), 16);
                    buffer.writeUtf(payload.sceneId(), 128);
                    buffer.writeUtf(payload.entityType(), 24);
                    buffer.writeUtf(payload.entityId(), 128);
                    buffer.writeUtf(payload.entityJson(), VttEnvironmentCommandPayload.MAX_JSON_LENGTH);
                    buffer.writeUtf(payload.originPlayerId(), 64);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
