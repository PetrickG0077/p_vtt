package com.petrick.vtt.feature.canvas.visual;

/**
 * Visual simples baseado em cor sólida.
 *
 * Usado como compatibilidade com os objetos temporários do Sprint 1.
 */
public record ColorVisual(
        int color
) implements CanvasVisual {
}