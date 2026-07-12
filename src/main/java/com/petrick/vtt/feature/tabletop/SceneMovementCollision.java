package com.petrick.vtt.feature.tabletop;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Clips token movement against walls and closed doors that block movement. */
public final class SceneMovementCollision {
    private static final double SWEEP_STEP = 1.0;
    private static final int BINARY_SEARCH_STEPS = 8;
    private static final double TOUCH_EPSILON = 0.001;

    public Vec2d clipMovement(
            VttScene tabletopScene, CanvasScene canvasScene,
            Set<String> movingObjectIds, Vec2d requestedDelta
    ) {
        if (tabletopScene == null || canvasScene == null || movingObjectIds == null
                || movingObjectIds.isEmpty() || requestedDelta == null
                || requestedDelta.lengthSquared() <= 0.0) return requestedDelta == null ? Vec2d.ZERO : requestedDelta;

        List<CanvasObject> movingObjects = canvasScene.getObjects().stream()
                .filter(object -> object.visible() && object.hasSourceTokenDefinition()
                        && movingObjectIds.contains(object.id()))
                .toList();
        List<Obstacle> obstacles = buildObstacles(tabletopScene);
        if (movingObjects.isEmpty() || obstacles.isEmpty()) return requestedDelta;

        Vec2d direct = clipAlong(movingObjects, obstacles, Vec2d.ZERO, requestedDelta);
        if (nearlyEqual(direct, requestedDelta)) return requestedDelta;

        Vec2d xMovement = clipAlong(movingObjects, obstacles, Vec2d.ZERO,
                new Vec2d(requestedDelta.x(), 0.0));
        Vec2d yMovement = clipAlong(movingObjects, obstacles, xMovement,
                new Vec2d(0.0, requestedDelta.y()));
        return xMovement.add(yMovement);
    }

    private Vec2d clipAlong(
            List<CanvasObject> objects, List<Obstacle> obstacles,
            Vec2d baseOffset, Vec2d delta
    ) {
        double length = delta.length();
        if (length <= 0.0) return Vec2d.ZERO;
        int samples = Math.max(1, (int) Math.ceil(length / SWEEP_STEP));
        double lastSafe = 0.0;
        for (int sample = 1; sample <= samples; sample++) {
            double progress = (double) sample / samples;
            if (collides(objects, obstacles, baseOffset.add(delta.multiply(progress)))) {
                double low = lastSafe;
                double high = progress;
                for (int iteration = 0; iteration < BINARY_SEARCH_STEPS; iteration++) {
                    double middle = (low + high) / 2.0;
                    if (collides(objects, obstacles, baseOffset.add(delta.multiply(middle)))) high = middle;
                    else low = middle;
                }
                return delta.multiply(low);
            }
            lastSafe = progress;
        }
        return delta;
    }

    private boolean collides(List<CanvasObject> objects, List<Obstacle> obstacles, Vec2d offset) {
        for (CanvasObject object : objects) {
            RectShape token = tokenShape(object, offset);
            for (Obstacle obstacle : obstacles) {
                if (overlaps(token, obstacle.shape())) return true;
            }
        }
        return false;
    }

    private boolean overlaps(RectShape first, RectShape second) {
        Vec2d[] axes = {first.axisX(), first.axisY(), second.axisX(), second.axisY()};
        Vec2d centerDelta = second.center().subtract(first.center());
        for (Vec2d axis : axes) {
            double distance = Math.abs(centerDelta.dot(axis));
            double firstExtent = projectedExtent(first, axis);
            double secondExtent = projectedExtent(second, axis);
            if (distance >= firstExtent + secondExtent - TOUCH_EPSILON) return false;
        }
        return true;
    }

    private double projectedExtent(RectShape rectangle, Vec2d axis) {
        return Math.abs(rectangle.axisX().dot(axis)) * rectangle.halfWidth()
                + Math.abs(rectangle.axisY().dot(axis)) * rectangle.halfHeight();
    }

    private RectShape tokenShape(CanvasObject object, Vec2d offset) {
        return shape(object.transform().position().add(offset), object.transform().rotationDegrees(),
                Math.abs(object.size().x() * object.transform().scale().x()),
                Math.abs(object.size().y() * object.transform().scale().y()));
    }

    private List<Obstacle> buildObstacles(VttScene scene) {
        List<Obstacle> obstacles = new ArrayList<>();
        for (VttWall wall : scene.getWalls()) {
            if (wall == null || !wall.isVisible() || !wall.isBlocksMovement()) continue;
            addWallPieces(obstacles, scene, wall);
        }
        for (VttDoor door : scene.getDoors()) {
            if (door == null || !door.isVisible() || !door.blocksMovement()) continue;
            obstacles.add(new Obstacle("door:" + door.getId(), shape(position(door.getTransform()),
                    door.getTransform().getRotationDegrees(), actualWidth(door), actualHeight(door))));
        }
        return obstacles;
    }

    private void addWallPieces(List<Obstacle> target, VttScene scene, VttWall wall) {
        double width = actualWidth(wall);
        double height = actualHeight(wall);
        double halfWidth = width / 2.0;
        List<Interval> openings = doorOpenings(scene, wall, halfWidth);
        if (openings.isEmpty()) {
            target.add(new Obstacle("wall:" + wall.getId(), shape(position(wall.getTransform()),
                    wall.getTransform().getRotationDegrees(), width, height)));
            return;
        }
        Vec2d wallCenter = position(wall.getTransform());
        Vec2d wallAxis = axis(wall.getTransform().getRotationDegrees());
        double cursor = -halfWidth;
        for (Interval opening : openings) {
            addWallPiece(target, wall, wallCenter, wallAxis, cursor, opening.start(), height);
            cursor = Math.max(cursor, opening.end());
        }
        addWallPiece(target, wall, wallCenter, wallAxis, cursor, halfWidth, height);
    }

    private void addWallPiece(
            List<Obstacle> target, VttWall wall, Vec2d wallCenter, Vec2d wallAxis,
            double start, double end, double height
    ) {
        if (end <= start) return;
        double localCenter = (start + end) / 2.0;
        Vec2d center = wallCenter.add(wallAxis.multiply(localCenter));
        target.add(new Obstacle("wall:" + wall.getId(), shape(center,
                wall.getTransform().getRotationDegrees(), end - start, height)));
    }

    private List<Interval> doorOpenings(VttScene scene, VttWall wall, double halfWidth) {
        List<Interval> raw = new ArrayList<>();
        Vec2d center = position(wall.getTransform());
        Vec2d wallAxis = axis(wall.getTransform().getRotationDegrees());
        for (VttDoor door : scene.getDoors()) {
            if (door == null || !door.isVisible() || !wall.getId().equals(door.getWallId())) continue;
            double localCenter = position(door.getTransform()).subtract(center).dot(wallAxis);
            double halfExtent = projectedDoorHalfExtent(door, wallAxis);
            double start = Math.max(-halfWidth, localCenter - halfExtent);
            double end = Math.min(halfWidth, localCenter + halfExtent);
            if (end > start) raw.add(new Interval(start, end));
        }
        raw.sort(Comparator.comparingDouble(Interval::start));
        List<Interval> merged = new ArrayList<>();
        for (Interval interval : raw) {
            if (merged.isEmpty() || interval.start() > merged.get(merged.size() - 1).end()) merged.add(interval);
            else {
                Interval previous = merged.remove(merged.size() - 1);
                merged.add(new Interval(previous.start(), Math.max(previous.end(), interval.end())));
            }
        }
        return merged;
    }

    private double projectedDoorHalfExtent(VttDoor door, Vec2d projectionAxis) {
        Vec2d doorX = axis(door.getTransform().getRotationDegrees());
        Vec2d doorY = new Vec2d(-doorX.y(), doorX.x());
        return Math.abs(doorX.dot(projectionAxis)) * actualWidth(door) / 2.0
                + Math.abs(doorY.dot(projectionAxis)) * actualHeight(door) / 2.0;
    }

    private RectShape shape(Vec2d center, double rotationDegrees, double width, double height) {
        Vec2d axisX = axis(rotationDegrees);
        return new RectShape(center, axisX, new Vec2d(-axisX.y(), axisX.x()), width / 2.0, height / 2.0);
    }

    private Vec2d axis(double degrees) {
        double radians = Math.toRadians(degrees);
        return new Vec2d(Math.cos(radians), Math.sin(radians));
    }

    private Vec2d position(VttSceneTransform transform) {
        return new Vec2d(transform.getX(), transform.getY());
    }

    private double actualWidth(VttWall wall) {
        return Math.abs(wall.getSize().getWidth() * wall.getTransform().getScaleX());
    }

    private double actualHeight(VttWall wall) {
        return Math.abs(wall.getSize().getHeight() * wall.getTransform().getScaleY());
    }

    private double actualWidth(VttDoor door) {
        return Math.abs(door.getSize().getWidth() * door.getTransform().getScaleX());
    }

    private double actualHeight(VttDoor door) {
        return Math.abs(door.getSize().getHeight() * door.getTransform().getScaleY());
    }

    private boolean nearlyEqual(Vec2d first, Vec2d second) {
        return first.subtract(second).lengthSquared() <= 0.0000001;
    }

    private record Interval(double start, double end) {}
    private record Obstacle(String sourceId, RectShape shape) {}
    private record RectShape(
            Vec2d center, Vec2d axisX, Vec2d axisY, double halfWidth, double halfHeight
    ) {}
}
