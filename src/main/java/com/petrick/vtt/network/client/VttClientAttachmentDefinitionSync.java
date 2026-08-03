package com.petrick.vtt.network.client;

import com.google.gson.Gson;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.attachment.AttachmentDefinition;
import com.petrick.vtt.feature.attachment.persistence.CreatedAttachmentSaveData;
import com.petrick.vtt.network.payload.VttAttachmentDefinitionCommandPayload;
import com.petrick.vtt.network.payload.VttAttachmentDefinitionUpsertPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client requests for authoritative server attachment-definition operations. */
public final class VttClientAttachmentDefinitionSync {
    private static final Gson GSON = new Gson();

    private VttClientAttachmentDefinitionSync() {}

    public static boolean sendUpsert(
            String requestId, long authorityRevision,
            AttachmentDefinition definition, String folder
    ) {
        if (!validRequestId(requestId) || definition == null) return false;
        CreatedAttachmentSaveData data = new CreatedAttachmentSaveData(
                CreatedAttachmentSaveData.CURRENT_SCHEMA_VERSION, definition.id(),
                definition.displayName(), definition.assetId(),
                definition.defaultWidth(), definition.defaultHeight());
        String json = GSON.toJson(data);
        if (json.length() > VttAttachmentDefinitionUpsertPayload.MAX_JSON_LENGTH) {
            VTT.LOGGER.warn("Could not send VTT attachment definition: JSON is too large");
            return false;
        }
        PacketDistributor.sendToServer(new VttAttachmentDefinitionUpsertPayload(
                requestId, authorityRevision, json, folder == null ? "" : folder));
        return true;
    }

    public static boolean sendDuplicate(String requestId, long revision, String definitionId) {
        return sendCommand(requestId, revision,
                VttAttachmentDefinitionCommandPayload.DUPLICATE, definitionId);
    }

    public static boolean sendDelete(String requestId, long revision, String definitionId) {
        return sendCommand(requestId, revision,
                VttAttachmentDefinitionCommandPayload.DELETE, definitionId);
    }

    private static boolean sendCommand(
            String requestId, long revision, String operation, String definitionId
    ) {
        if (!validRequestId(requestId) || definitionId == null
                || !definitionId.startsWith("user/attachments/")
                || definitionId.length() > 256) return false;
        PacketDistributor.sendToServer(new VttAttachmentDefinitionCommandPayload(
                requestId, revision, operation, definitionId));
        return true;
    }

    private static boolean validRequestId(String requestId) {
        return requestId != null && !requestId.isBlank() && requestId.length() <= 64;
    }
}
