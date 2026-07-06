package com.petrick.vtt.feature.canvas;

import com.petrick.vtt.feature.canvas.visual.CanvasVisual;

/**
 * Estado visual de um objeto do canvas.
 *
 * Exemplo futuro:
 * - normal
 * - ferido
 * - morto
 * - transformado
 */
public record CanvasObjectState(
        String id,
        String displayName,
        CanvasVisual visual
) {

    public CanvasObjectState {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("State id cannot be null or blank");
        }

        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("State display name cannot be null or blank");
        }

        if (visual == null) {
            throw new IllegalArgumentException("State visual cannot be null");
        }
    }
}