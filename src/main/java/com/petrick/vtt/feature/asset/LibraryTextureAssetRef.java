package com.petrick.vtt.feature.asset;

import net.minecraft.resources.ResourceLocation;

/**
 * Asset de textura carregado a partir de uma imagem da biblioteca do usuário.
 */
public record LibraryTextureAssetRef(
        String id,
        ResourceLocation texture,
        int textureWidth,
        int textureHeight,
        String sourceRelativePath
) implements AssetRef {

    public LibraryTextureAssetRef {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Library texture asset id cannot be null or blank");
        }

        if (texture == null) {
            throw new IllegalArgumentException("Library texture ResourceLocation cannot be null");
        }

        if (textureWidth <= 0) {
            throw new IllegalArgumentException("Library texture width must be positive");
        }

        if (textureHeight <= 0) {
            throw new IllegalArgumentException("Library texture height must be positive");
        }

        if (sourceRelativePath == null || sourceRelativePath.isBlank()) {
            throw new IllegalArgumentException("Library texture source path cannot be null or blank");
        }
    }
}