package com.petrick.vtt.feature.asset;

import net.minecraft.resources.ResourceLocation;

/**
 * Asset que aponta para uma textura já existente dentro dos resources do mod.
 *
 * Usado como fallback/teste no começo do Sprint 2.
 */
public record BuiltInTextureAssetRef(
        String id,
        ResourceLocation texture,
        int textureWidth,
        int textureHeight
) implements AssetRef {
}