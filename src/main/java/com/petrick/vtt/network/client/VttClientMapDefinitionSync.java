package com.petrick.vtt.network.client;

import com.google.gson.Gson;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.map.MapDefinition;
import com.petrick.vtt.feature.map.persistence.CreatedMapSaveData;
import com.petrick.vtt.network.payload.VttMapDefinitionCommandPayload;
import com.petrick.vtt.network.payload.VttMapDefinitionUpsertPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client requests for authoritative server map-definition lifecycle operations. */
public final class VttClientMapDefinitionSync {
    private static final Gson GSON = new Gson();

    private VttClientMapDefinitionSync() {}

    public static boolean sendUpsert(MapDefinition definition, String folder) {
        if (definition == null) return false;
        CreatedMapSaveData data = new CreatedMapSaveData(
                CreatedMapSaveData.CURRENT_SCHEMA_VERSION, definition.id(),
                definition.displayName(), definition.assetId(), definition.imageWidth(),
                definition.imageHeight(), definition.textureMode());
        String json = GSON.toJson(data);
        if (json.length() > VttMapDefinitionUpsertPayload.MAX_JSON_LENGTH) {
            VTT.LOGGER.warn("Could not send VTT map definition: JSON is too large");
            return false;
        }
        PacketDistributor.sendToServer(new VttMapDefinitionUpsertPayload(
                json, folder == null ? "" : folder));
        return true;
    }

    public static boolean sendDuplicate(String definitionId) {
        return sendCommand(VttMapDefinitionCommandPayload.DUPLICATE, definitionId);
    }

    public static boolean sendDelete(String definitionId) {
        return sendCommand(VttMapDefinitionCommandPayload.DELETE, definitionId);
    }

    private static boolean sendCommand(String operation, String definitionId) {
        if (definitionId == null || !definitionId.startsWith("user/maps/")
                || definitionId.length() > 256) return false;
        PacketDistributor.sendToServer(new VttMapDefinitionCommandPayload(
                operation, definitionId));
        return true;
    }
}
