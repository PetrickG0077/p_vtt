package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Per-player token interest delta. Spawns and despawns only affect the client replica. */
public record VttPlayerReplicationPayload(
        long authorityRevision, long replicationRevision, String sceneId,
        String spawnedObjectsJson, List<String> despawnObjectIds
) implements CustomPacketPayload {
    public static final int MAX_OBJECTS_JSON_LENGTH = 8 * 1024 * 1024;
    private static final int MAX_DESPAWN_COUNT = 4_096;

    public static final Type<VttPlayerReplicationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "player_replication"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VttPlayerReplicationPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public VttPlayerReplicationPayload decode(RegistryFriendlyByteBuf buffer) {
                    long authorityRevision = buffer.readVarLong();
                    long replicationRevision = buffer.readVarLong();
                    String sceneId = buffer.readUtf(128);
                    String objectsJson = buffer.readUtf(MAX_OBJECTS_JSON_LENGTH);
                    int despawnCount = buffer.readVarInt();
                    if (despawnCount < 0 || despawnCount > MAX_DESPAWN_COUNT) {
                        throw new IllegalArgumentException("Invalid VTT despawn count: " + despawnCount);
                    }
                    List<String> despawnIds = new ArrayList<>(despawnCount);
                    for (int index = 0; index < despawnCount; index++) {
                        despawnIds.add(buffer.readUtf(128));
                    }
                    return new VttPlayerReplicationPayload(
                            authorityRevision, replicationRevision, sceneId,
                            objectsJson, despawnIds);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, VttPlayerReplicationPayload payload) {
                    if (payload.despawnObjectIds().size() > MAX_DESPAWN_COUNT) {
                        throw new IllegalArgumentException("Too many VTT despawns");
                    }
                    buffer.writeVarLong(payload.authorityRevision());
                    buffer.writeVarLong(payload.replicationRevision());
                    buffer.writeUtf(payload.sceneId(), 128);
                    buffer.writeUtf(payload.spawnedObjectsJson(), MAX_OBJECTS_JSON_LENGTH);
                    buffer.writeVarInt(payload.despawnObjectIds().size());
                    for (String objectId : payload.despawnObjectIds()) buffer.writeUtf(objectId, 128);
                }
            };

    public VttPlayerReplicationPayload {
        spawnedObjectsJson = spawnedObjectsJson == null ? "[]" : spawnedObjectsJson;
        despawnObjectIds = despawnObjectIds == null ? List.of() : List.copyOf(despawnObjectIds);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
