package com.petrick.vtt.feature.attachment.persistence;

/** Stable JSON representation of a user-created attachment definition. */
public record CreatedAttachmentSaveData(
        int schemaVersion,
        String attachmentDefinitionId,
        String displayName,
        String assetId,
        double defaultWidth,
        double defaultHeight
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}
