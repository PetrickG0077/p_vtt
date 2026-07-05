package com.petrick.vtt.feature.canvas;

import com.petrick.vtt.core.math.Rectd;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.transform.Transform2D;

/**
 * Objeto temporário do canvas.
 *
 * Futuramente isso será substituído por entidades/tokens do ECS.
 */
public record CanvasObject(
        String id,
        Transform2D transform,
        Vec2d size,
        int color
) {

    public CanvasObject movedBy(Vec2d delta) {
        return new CanvasObject(
                id,
                transform.movedBy(delta),
                size,
                color
        );
    }

    /**
     * Retorna os limites atuais do objeto no mundo.
     *
     * Por enquanto isso ignora rotação.
     * Quando implementarmos rotação visual de verdade, este método será
     * substituído por uma lógica de bounding box rotacionada.
     */
    public Rectd bounds() {
        Vec2d scaledSize = new Vec2d(
                size.x() * transform.scale().x(),
                size.y() * transform.scale().y()
        );

        return new Rectd(
                transform.position().x() - scaledSize.x() / 2.0,
                transform.position().y() - scaledSize.y() / 2.0,
                scaledSize.x(),
                scaledSize.y()
        );
    }
}