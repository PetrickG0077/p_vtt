package com.petrick.vtt.core.math;

/**
 * Representa um retângulo 2D imutável.
 */
public record Rectd(
        double x,
        double y,
        double width,
        double height
) {

    public static final Rectd EMPTY = new Rectd(0, 0, 0, 0);

    public double left() {
        return x;
    }

    public double top() {
        return y;
    }

    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }

    public Vec2d position() {
        return new Vec2d(x, y);
    }

    public Vec2d size() {
        return new Vec2d(width, height);
    }

    public Vec2d center() {
        return new Vec2d(
                x + width / 2.0,
                y + height / 2.0
        );
    }

    public boolean contains(Vec2d point) {
        return point.x() >= left()
                && point.x() <= right()
                && point.y() >= top()
                && point.y() <= bottom();
    }

    public boolean intersects(Rectd other) {
        return right() >= other.left()
                && left() <= other.right()
                && bottom() >= other.top()
                && top() <= other.bottom();
    }

    public Rectd translate(Vec2d offset) {
        return new Rectd(
                x + offset.x(),
                y + offset.y(),
                width,
                height
        );
    }

}