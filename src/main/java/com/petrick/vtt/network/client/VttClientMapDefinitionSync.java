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

    public static boolean sendUpsert(
            String requestId, long authorityRevision,
            MapDefinition definition, String folder
    ) {
        if (!validRequestId(requestId) || definition == null) return false;
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
                requestId, authorityRevision, json, folder == null ? "" : folder));
        return true;
    }

    public static boolean sendDuplicate(
            String requestId, long authorityRevision, String definitionId
    ) {
        return sendCommand(requestId, authorityRevision,
                VttMapDefinitionCommandPayload.DUPLICATE, definitionId);
    }

    public static boolean sendDelete(
            String requestId, long authorityRevision, String definitionId
    ) {
        return sendCommand(requestId, authorityRevision,
                VttMapDefinitionCommandPayload.DELETE, definitionId);
    }

    private static boolean sendCommand(
            String requestId, long authorityRevision,
            String operation, String definitionId
    ) {
        if (!validRequestId(requestId) || definitionId == null
                || !definitionId.startsWith("user/maps/")
                || definitionId.length() > 256) return false;
        PacketDistributor.sendToServer(new VttMapDefinitionCommandPayload(
                requestId, authorityRevision, operation, definitionId));
        return true;
    }

    private static boolean validRequestId(String requestId) {
        return requestId != null && !requestId.isBlank() && requestId.length() <= 64;
    }
}
