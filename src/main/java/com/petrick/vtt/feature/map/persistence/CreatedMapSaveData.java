package com.petrick.vtt.feature.map.persistence;

/** Stable JSON representation of a user-created map definition. */
public record CreatedMapSaveData(
        int schemaVersion,
        String mapDefinitionId,
        String displayName,
        String assetId,
        int imageWidth,
        int imageHeight
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}
