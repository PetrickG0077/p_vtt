package com.petrick.vtt.feature.attachment.persistence;

import com.petrick.vtt.feature.attachment.AttachmentStateDefinition;
import java.util.Map;

/** Stable JSON representation of a user-created attachment definition. */
public record CreatedAttachmentSaveData(
        int schemaVersion,
        String attachmentDefinitionId,
        String displayName,
        String assetId,
        double defaultWidth,
        double defaultHeight,
        Map<String, AttachmentStateDefinition> states,
        String defaultStateId
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;
}
