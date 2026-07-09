package com.petrick.vtt.feature.canvas.visual;

import com.petrick.vtt.feature.asset.AssetRef;

/**
 * Visual baseado em textura animada.
 *
 * Nesta primeira etapa, ele ainda não renderiza a animação real.
 * Ele serve para o sistema saber que aquele state veio de um asset animado,
 * como GIF ou APNG.
 *
 * Futuramente o renderer vai usar esse tipo para tocar frames animados.
 */
public record AnimatedTextureVisual(
        AssetRef assetRef
) implements CanvasVisual {
}