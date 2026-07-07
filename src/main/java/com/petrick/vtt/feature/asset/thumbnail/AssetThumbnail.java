package com.petrick.vtt.feature.asset.thumbnail;

import net.minecraft.resources.ResourceLocation;

/**
 * Representa uma miniatura carregada como textura do Minecraft.
 */
public record AssetThumbnail(
        String id,
        ResourceLocation texture,
        int width,
        int height
) {

    public AssetThumbnail {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Thumbnail id cannot be null or blank");
        }

        if (texture == null) {
            throw new IllegalArgumentException("Thumbnail texture cannot be null");
        }

        if (width <= 0) {
            throw new IllegalArgumentException("Thumbnail width must be positive");
        }

        if (height <= 0) {
            throw new IllegalArgumentException("Thumbnail height must be positive");
        }
    }
}