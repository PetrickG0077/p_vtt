package com.petrick.vtt.network.client;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttTokenDefinitionUpsertPayload;
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
}
