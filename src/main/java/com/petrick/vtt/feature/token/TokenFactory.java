package com.petrick.vtt.feature.token;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.transform.Transform2D;
import com.petrick.vtt.feature.canvas.CanvasObject;

/**
 * Cria instâncias de CanvasObject a partir de TokenDefinition.
 *
 * Ou seja:
 * TokenDefinition = modelo
 * CanvasObject = token colocado no canvas
 */
public final class TokenFactory {

    private TokenFactory() {
    }

    public static CanvasObject createCanvasObject(
            TokenDefinition definition,
            String objectId,
            Vec2d worldPosition
    ) {
        TokenStatePreset preset = definition.statePresets().get(definition.defaultStateId());
        double rotation = preset == null ? 0.0 : preset.appearance().getRotationDegrees();
        Vec2d scale = preset == null ? new Vec2d(1.0, 1.0)
                : new Vec2d(preset.appearance().getScaleX(), preset.appearance().getScaleY());
        return new CanvasObject(
                objectId,
                definition.displayName(),
                definition.id(),
                new Transform2D(
                        worldPosition,
                        rotation,
                        scale
                ),
                definition.defaultSize(),
                definition.states(),
                definition.defaultStateId(),
                true
        );
    }
}
