package com.petrick.vtt.feature.asset.animation;

import net.minecraft.resources.ResourceLocation;

/**
 * Um frame de uma textura animada carregada no Minecraft.
 */
public record AnimatedTextureFrame(
        ResourceLocation texture,
        int width,
        int height,
        int durationMs
) {

    public AnimatedTextureFrame {
        if (texture == null) {
            throw new IllegalArgumentException("Frame texture cannot be null");
        }

        if (width <= 0) {
            throw new IllegalArgumentException("Frame width must be positive");
        }

        if (height <= 0) {
            throw new IllegalArgumentException("Frame height must be positive");
        }

        if (durationMs <= 0) {
            durationMs = 100;
        }
    }
}