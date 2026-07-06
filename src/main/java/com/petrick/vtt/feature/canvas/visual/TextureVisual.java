package com.petrick.vtt.feature.canvas.visual;

import com.petrick.vtt.feature.asset.AssetRef;

/**
 * Visual baseado em textura.
 *
 * Nesta fase, ele aponta para um AssetRef.
 * O AssetRef ainda pode ser uma textura interna do mod,
 * mas futuramente poderá apontar para imagens importadas/cacheadas.
 */
public record TextureVisual(
        AssetRef assetRef
) implements CanvasVisual {
}