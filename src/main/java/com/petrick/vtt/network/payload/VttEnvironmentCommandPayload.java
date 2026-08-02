package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A single master-authored environment mutation sent to the server. */
public record VttEnvironmentCommandPayload(
        long authorityRevision, long clientSequence,
        String operation, String sceneId, String entityType, String entityId, String entityJson)
        implements CustomPacketPayload {
    public static final String UPSERT = "UPSERT";
    public static final String DELETE = "DELETE";
    public static final String MAP = "MAP";
    public static final String WALL = "WALL";
    public static final String DOOR = "DOOR";
    public static final String FOG_HIDDEN = "FOG_HIDDEN";
    public static final String FOG_REVEALED = "FOG_REVEALED";
    public static final String FOG_CONFIG = "FOG_CONFIG";
    public static final String GRID_CONFIG = "GRID_CONFIG";
    public static final String LIGHTING_CONFIG = "LIGHTING_CONFIG";
    public static final String BACKGROUND_CONFIG = "BACKGROUND_CONFIG";
    public static final String CAMERA_CONFIG = "CAMERA_CONFIG";
    public static final String VISION = "VISION";
    public static final int MAX_JSON_LENGTH = 65_536;

    public static final Type<VttEnvironmentCommandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "environment_command")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VttEnvironmentCommandPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public VttEnvironmentCommandPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new VttEnvironmentCommandPayload(buffer.readVarLong(), buffer.readVarLong(),
                            buffer.readUtf(16), buffer.readUtf(128), buffer.readUtf(24),
                            buffer.readUtf(128), buffer.readUtf(MAX_JSON_LENGTH));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, VttEnvironmentCommandPayload payload) {
                    buffer.writeVarLong(payload.authorityRevision());
                    buffer.writeVarLong(payload.clientSequence());
                    buffer.writeUtf(payload.operation(), 16);
                    buffer.writeUtf(payload.sceneId(), 128);
                    buffer.writeUtf(payload.entityType(), 24);
                    buffer.writeUtf(payload.entityId(), 128);
                    buffer.writeUtf(payload.entityJson(), MAX_JSON_LENGTH);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
