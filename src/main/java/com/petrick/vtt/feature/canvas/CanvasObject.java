package com.petrick.vtt.feature.canvas;

import com.petrick.vtt.core.math.Rectd;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.transform.Transform2D;
import com.petrick.vtt.feature.canvas.visual.CanvasVisual;

/**
 * Objeto temporário do canvas.
 *
 * Futuramente isso será substituído por entidades/tokens do ECS.
 */
public record CanvasObject(
        String id,
        Transform2D transform,
        Vec2d size,
        CanvasVisual visual,
        boolean visible
) {

    public CanvasObject movedBy(Vec2d delta) {
        return new CanvasObject(
                id,
                transform.movedBy(delta),
                size,
                visual,
                visible
        );
    }

    public CanvasObject scaledBy(double factor) {
        return new CanvasObject(
                id,
                transform.withScale(transform.scale().multiply(factor)),
                size,
                visual,
                visible
        );
    }

    public CanvasObject rotatedBy(double deltaDegrees) {
        return new CanvasObject(
                id,
                transform.rotatedBy(deltaDegrees),
                size,
                visual,
                visible
        );
    }

    public CanvasObject duplicatedAs(String newId, Vec2d offset) {
        return new CanvasObject(
                newId,
                transform.movedBy(offset),
                size,
                visual,
                visible
        );
    }

    public CanvasObject withVisible(boolean visible) {
        return new CanvasObject(
                id,
                transform,
                size,
                visual,
                visible
        );
    }

    public CanvasObject toggledVisibility() {
        return withVisible(!visible);
    }

    public CanvasObject resetScaleAndRotation() {
        return new CanvasObject(
                id,
                transform
                        .withRotation(0.0)
                        .withScale(new Vec2d(1.0, 1.0)),
                size,
                visual,
                visible
        );
    }

    public Vec2d scaledSize() {
        return new Vec2d(
                size.x() * transform.scale().x(),
                size.y() * transform.scale().y()
        );
    }

    public Vec2d worldTopLeft() {
        Vec2d scaledSize = scaledSize();
        return localToWorld(new Vec2d(-scaledSize.x() / 2.0, -scaledSize.y() / 2.0));
    }

    public Vec2d worldTopRight() {
        Vec2d scaledSize = scaledSize();
        return localToWorld(new Vec2d(scaledSize.x() / 2.0, -scaledSize.y() / 2.0));
    }

    public Vec2d worldBottomLeft() {
        Vec2d scaledSize = scaledSize();
        return localToWorld(new Vec2d(-scaledSize.x() / 2.0, scaledSize.y() / 2.0));
    }

    public Vec2d worldBottomRight() {
        Vec2d scaledSize = scaledSize();
        return localToWorld(new Vec2d(scaledSize.x() / 2.0, scaledSize.y() / 2.0));
    }

    public boolean containsWorldPoint(Vec2d worldPoint) {
        Vec2d localPoint = worldToLocal(worldPoint);
        Vec2d scaledSize = scaledSize();

        return Math.abs(localPoint.x()) <= scaledSize.x() / 2.0
                && Math.abs(localPoint.y()) <= scaledSize.y() / 2.0;
    }

    public Rectd bounds() {
        Vec2d topLeft = worldTopLeft();
        Vec2d topRight = worldTopRight();
        Vec2d bottomLeft = worldBottomLeft();
        Vec2d bottomRight = worldBottomRight();

        double minX = Math.min(
                Math.min(topLeft.x(), topRight.x()),
                Math.min(bottomLeft.x(), bottomRight.x())
        );

        double minY = Math.min(
                Math.min(topLeft.y(), topRight.y()),
                Math.min(bottomLeft.y(), bottomRight.y())
        );

        double maxX = Math.max(
                Math.max(topLeft.x(), topRight.x()),
                Math.max(bottomLeft.x(), bottomRight.x())
        );

        double maxY = Math.max(
                Math.max(topLeft.y(), topRight.y()),
                Math.max(bottomLeft.y(), bottomRight.y())
        );

        return new Rectd(
                minX,
                minY,
                maxX - minX,
                maxY - minY
        );
    }

    private Vec2d localToWorld(Vec2d localPoint) {
        Vec2d rotated = rotate(localPoint, transform.rotationDegrees());
        return transform.position().add(rotated);
    }

    private Vec2d worldToLocal(Vec2d worldPoint) {
        Vec2d translated = worldPoint.subtract(transform.position());
        return rotate(translated, -transform.rotationDegrees());
    }

    private static Vec2d rotate(Vec2d point, double degrees) {
        double radians = Math.toRadians(degrees);

        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        return new Vec2d(
                point.x() * cos - point.y() * sin,
                point.x() * sin + point.y() * cos
        );
    }
}