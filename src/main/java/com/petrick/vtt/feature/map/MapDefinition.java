package com.petrick.vtt.feature.map;

/** Reusable, user-created map definition shown in the Map Catalog. */
public record MapDefinition(
        String id,
        String displayName,
        String assetId,
        int imageWidth,
        int imageHeight
) {
    public MapDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Map definition id cannot be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Map definition name cannot be blank");
        }
        if (assetId == null || assetId.isBlank()) {
            throw new IllegalArgumentException("Map definition asset id cannot be blank");
        }
        if (imageWidth <= 0 || imageWidth > 16_000
                || imageHeight <= 0 || imageHeight > 16_000) {
            throw new IllegalArgumentException("Invalid map image dimensions");
        }
    }
}
