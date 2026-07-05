package com.petrick.vtt.feature.canvas;

import com.petrick.vtt.core.math.Rectd;
import com.petrick.vtt.core.math.Vec2d;

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

    public CanvasObject movedBy(Vec2d delta) {
        return new CanvasObject(
                id,
                bounds.translate(delta),
                color
        );
    }
}