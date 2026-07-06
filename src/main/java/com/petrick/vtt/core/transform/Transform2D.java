package com.petrick.vtt.core.transform;

import com.petrick.vtt.core.math.Vec2d;

/**
 * Representa a transformação 2D de um objeto no mundo do VTT.
 *
 * Por enquanto contém:
 * - posição
 * - rotação em graus
 * - escala
 *
 * Futuramente será usado por tokens, mapas, imagens e objetos interativos.
 */
public record Transform2D(
        Vec2d position,
        double rotationDegrees,
        Vec2d scale
) {

    public static Transform2D identity() {
        return new Transform2D(
                Vec2d.ZERO,
                0.0,
                new Vec2d(1.0, 1.0)
        );
    }

    public Transform2D movedBy(Vec2d delta) {
        return new Transform2D(
                position.add(delta),
                rotationDegrees,
                scale
        );
    }

    public Transform2D rotatedBy(double deltaDegrees) {
        return new Transform2D(
                position,
                rotationDegrees + deltaDegrees,
                scale
        );
    }

    public Transform2D withPosition(Vec2d position) {
        return new Transform2D(
                position,
                rotationDegrees,
                scale
        );
    }

    public Transform2D withRotation(double rotationDegrees) {
        return new Transform2D(
                position,
                rotationDegrees,
                scale
        );
    }

    public Transform2D withScale(Vec2d scale) {
        return new Transform2D(
                position,
                rotationDegrees,
                scale
        );
    }
}