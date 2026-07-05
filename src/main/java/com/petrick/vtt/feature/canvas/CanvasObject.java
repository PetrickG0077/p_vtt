package com.petrick.vtt.feature.canvas;

import com.petrick.vtt.core.math.Rectd;

/**
 * Objeto temporário do canvas.
 *
 * Futuramente isso será substituído por entidades/tokens do ECS.
 */
public record CanvasObject(
        String id,
        Rectd bounds,
        int color
) {
}