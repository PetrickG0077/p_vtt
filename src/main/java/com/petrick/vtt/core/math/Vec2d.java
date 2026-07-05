package com.petrick.vtt.core.math;

import java.util.Objects;

/**
 * Vetor 2D imutável utilizando double.
 */
public record Vec2d(double x, double y) {

    public static final Vec2d ZERO = new Vec2d(0.0, 0.0);

    public Vec2d add(Vec2d other) {
        return new Vec2d(
                x + other.x,
                y + other.y
        );
    }

    public Vec2d subtract(Vec2d other) {
        return new Vec2d(
                x - other.x,
                y - other.y
        );
    }

    public Vec2d multiply(double scalar) {
        return new Vec2d(
                x * scalar,
                y * scalar
        );
    }

    public Vec2d divide(double scalar) {
        return new Vec2d(
                x / scalar,
                y / scalar
        );
    }

    public double length() {
        return Math.sqrt(x * x + y * y);
    }

    public double distance(Vec2d other) {
        return subtract(other).length();
    }

    public Vec2d normalize() {

        double length = length();

        if (length == 0.0)
            return ZERO;

        return divide(length);
    }

    public double lengthSquared() {
        return x * x + y * y;
    }

    public double dot(Vec2d other) {
        return x * other.x() + y * other.y();
    }

}