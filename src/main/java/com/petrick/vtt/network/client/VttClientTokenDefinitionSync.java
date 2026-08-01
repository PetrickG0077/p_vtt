package com.petrick.vtt.network.client;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttTokenDefinitionUpsertPayload;
import com.petrick.vtt.network.payload.VttTokenDefinitionCommandPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VttClientTokenDefinitionSync {
    private VttClientTokenDefinitionSync() {}

    public static boolean sendUpsert(String requestId, long authorityRevision, String json) {
        if (!validRequestId(requestId) || json == null || json.isBlank()
                || json.length() > VttTokenDefinitionUpsertPayload.MAX_JSON_LENGTH) {
            VTT.LOGGER.warn("Could not send VTT token definition: JSON is empty or too large");
            return false;
        }
        PacketDistributor.sendToServer(new VttTokenDefinitionUpsertPayload(
                requestId, authorityRevision, json));
        return true;
    }

    public static boolean sendDuplicate(
            String requestId, long authorityRevision, String definitionId) {
        return sendCommand(requestId, authorityRevision,
                VttTokenDefinitionCommandPayload.DUPLICATE, definitionId);
    }

    public static boolean sendDelete(
            String requestId, long authorityRevision, String definitionId) {
        return sendCommand(requestId, authorityRevision,
                VttTokenDefinitionCommandPayload.DELETE, definitionId);
    }

    private static boolean sendCommand(
            String requestId, long authorityRevision, String operation, String definitionId) {
        if (!validRequestId(requestId) || definitionId == null || definitionId.isBlank()
                || definitionId.length() > 256
                || !definitionId.startsWith("user/tokens/")) {
            VTT.LOGGER.warn("Could not send VTT token definition command for invalid ID: {}", definitionId);
            return false;
        }
        PacketDistributor.sendToServer(new VttTokenDefinitionCommandPayload(
                requestId, authorityRevision, operation, definitionId));
        return true;
    }

    private static boolean validRequestId(String requestId) {
        return requestId != null && !requestId.isBlank() && requestId.length() <= 64;
    }
}
