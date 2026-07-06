package com.petrick.vtt.feature.canvas.visual;

import net.minecraft.resources.ResourceLocation;

/**
 * Visual baseado em textura.
 *
 * Nesta fase inicial, aponta para uma textura interna do mod.
 * Futuramente isso será trocado por AssetRef/cache.
 */
public record TextureVisual(
        ResourceLocation texture,
        int textureWidth,
        int textureHeight
) implements CanvasVisual {
}