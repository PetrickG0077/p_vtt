package com.petrick.vtt.feature.canvas.visual;

import com.petrick.vtt.feature.asset.AssetRef;

/**
 * Visual baseado em textura estática.
 *
 * Use este visual para imagens normais:
 * - PNG
 * - JPG/JPEG
 * - WEBP estático
 *
 * Para GIF/APNG, use AnimatedTextureVisual.
 */
public record TextureVisual(
        AssetRef assetRef
) implements CanvasVisual {
}