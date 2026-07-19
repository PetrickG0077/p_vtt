package com.petrick.vtt.network.client;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttTokenDefinitionUpsertPayload;
import com.petrick.vtt.network.payload.VttTokenDefinitionCommandPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VttClientTokenDefinitionSync {
    private VttClientTokenDefinitionSync() {}

    public static boolean sendUpsert(String json) {
        if (json == null || json.isBlank() || json.length() > VttTokenDefinitionUpsertPayload.MAX_JSON_LENGTH) {
            VTT.LOGGER.warn("Could not send VTT token definition: JSON is empty or too large");
            return false;
        }
        PacketDistributor.sendToServer(new VttTokenDefinitionUpsertPayload(json));
        return true;
    }

    public static boolean sendDuplicate(String definitionId) {
        return sendCommand(VttTokenDefinitionCommandPayload.DUPLICATE, definitionId);
    }

    public static boolean sendDelete(String definitionId) {
        return sendCommand(VttTokenDefinitionCommandPayload.DELETE, definitionId);
    }

    private static boolean sendCommand(String operation, String definitionId) {
        if (definitionId == null || definitionId.isBlank() || definitionId.length() > 256
                || !definitionId.startsWith("user/tokens/")) {
            VTT.LOGGER.warn("Could not send VTT token definition command for invalid ID: {}", definitionId);
            return false;
        }
        PacketDistributor.sendToServer(new VttTokenDefinitionCommandPayload(operation, definitionId));
        return true;
    }
}
