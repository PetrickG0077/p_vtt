package com.petrick.vtt.feature.canvas.visual;

/**
 * Representa o visual de um objeto no canvas.
 *
 * Tipos atuais:
 * - ColorVisual: retângulo colorido
 * - TextureVisual: imagem/textura estática
 * - AnimatedTextureVisual: imagem animada, como GIF/APNG
 */
public sealed interface CanvasVisual permits ColorVisual, TextureVisual, AnimatedTextureVisual {
}