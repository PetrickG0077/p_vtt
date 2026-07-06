package com.petrick.vtt.feature.canvas.visual;

/**
 * Representa o visual de um objeto no canvas.
 *
 * Por enquanto existem:
 * - ColorVisual: retângulo colorido
 * - TextureVisual: imagem/textura
 *
 * Futuramente isso pode apontar para assets carregados pelo usuário.
 */
public sealed interface CanvasVisual permits ColorVisual, TextureVisual {
}