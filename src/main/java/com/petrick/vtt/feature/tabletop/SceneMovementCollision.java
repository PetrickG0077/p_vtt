package com.petrick.vtt.feature.tabletop;

import com.petrick.vtt.VTT;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Clips token movement against walls and closed doors that block movement. */
public final class SceneMovementCollision {
    private static final double SWEEP_STEP = 1.0;
    private static final int BINARY_SEARCH_STEPS = 8;
    private static final double TOUCH_EPSILON = 0.001;
    private final ObstacleSpatialIndex sceneObjectObstacleIndex = new ObstacleSpatialIndex();

    public void rebuildObstacleIndex(VttScene scene) {
        sceneObjectObstacleIndex.rebuild(
                scene, scene == null ? List.of() : buildObstacles(scene));
    }

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

        List<RectShape> movingShapes = movingObjects.stream()
                .map(object -> tokenShape(tabletopScene, object, Vec2d.ZERO)).toList();
        return clipShapes(movingShapes, obstacles, requestedDelta);
    }

    /** Server-side variant operating only on persistent scene data. */
    public Vec2d clipSceneObjectMovement(
            VttScene tabletopScene, VttSceneObject movingObject, Vec2d requestedDelta
    ) {
        if (tabletopScene == null || movingObject == null || requestedDelta == null
                || requestedDelta.lengthSquared() <= 0.0) {
            return requestedDelta == null ? Vec2d.ZERO : requestedDelta;
        }
        if (!sceneObjectObstacleIndex.isBuiltFor(tabletopScene)) rebuildObstacleIndex(tabletopScene);
        RectShape movingShape = sceneTokenShape(movingObject);
        List<Obstacle> obstacles;
        try {
            obstacles = sceneObjectObstacleIndex.query(sweptBounds(movingShape, requestedDelta));
        } catch (RuntimeException exception) {
            VTT.LOGGER.warn("VTT movement obstacle query failed; using full collision fallback", exception);
            rebuildObstacleIndex(tabletopScene);
            obstacles = sceneObjectObstacleIndex.allObstacles();
        }
        if (obstacles.isEmpty()) return requestedDelta;
        return clipShapes(List.of(movingShape), obstacles, requestedDelta);
    }

    private Vec2d clipShapes(
            List<RectShape> movingShapes, List<Obstacle> obstacles, Vec2d requestedDelta
    ) {
        Vec2d direct = clipAlong(movingShapes, obstacles, Vec2d.ZERO, requestedDelta);
        if (nearlyEqual(direct, requestedDelta)) return requestedDelta;

        Vec2d xMovement = clipAlong(movingShapes, obstacles, Vec2d.ZERO,
                new Vec2d(requestedDelta.x(), 0.0));
        Vec2d yMovement = clipAlong(movingShapes, obstacles, xMovement,
                new Vec2d(0.0, requestedDelta.y()));
        return xMovement.add(yMovement);
    }

    private Vec2d clipAlong(
            List<RectShape> shapes, List<Obstacle> obstacles,
            Vec2d baseOffset, Vec2d delta
    ) {
        double length = delta.length();
        if (length <= 0.0) return Vec2d.ZERO;
        int samples = Math.max(1, (int) Math.ceil(length / SWEEP_STEP));
        double lastSafe = 0.0;
        for (int sample = 1; sample <= samples; sample++) {
            double progress = (double) sample / samples;
            if (collides(shapes, obstacles, baseOffset.add(delta.multiply(progress)))) {
                double low = lastSafe;
                double high = progress;
                for (int iteration = 0; iteration < BINARY_SEARCH_STEPS; iteration++) {
                    double middle = (low + high) / 2.0;
                    if (collides(shapes, obstacles, baseOffset.add(delta.multiply(middle)))) high = middle;
                    else low = middle;
                }
                return delta.multiply(low);
            }
            lastSafe = progress;
        }
        return delta;
    }

    private boolean collides(List<RectShape> shapes, List<Obstacle> obstacles, Vec2d offset) {
        for (RectShape shape : shapes) {
            RectShape token = moved(shape, offset);
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

    private RectShape tokenShape(VttScene scene, CanvasObject object, Vec2d offset) {
        VttSceneObject sceneObject = findSceneObject(scene, object.id());
        VttSceneCollisionBox collisionBox = sceneObject == null ? null : sceneObject.getCollisionBox();
        double baseWidth = collisionBox == null ? object.size().x() : collisionBox.getWidth();
        double baseHeight = collisionBox == null ? object.size().y() : collisionBox.getHeight();
        double localOffsetX = collisionBox == null ? 0.0 : collisionBox.getOffsetX();
        if (object.flippedHorizontally()) localOffsetX = -localOffsetX;
        double localOffsetY = collisionBox == null ? 0.0 : collisionBox.getOffsetY();
        double scaleX = object.transform().scale().x();
        double scaleY = object.transform().scale().y();
        Vec2d center = object.transform().position().add(offset).add(rotate(
                new Vec2d(localOffsetX * scaleX, localOffsetY * scaleY),
                object.transform().rotationDegrees()));
        return shape(center, object.transform().rotationDegrees(),
                Math.abs(baseWidth * scaleX), Math.abs(baseHeight * scaleY));
    }

    private VttSceneObject findSceneObject(VttScene scene, String objectId) {
        if (scene == null || objectId == null) return null;
        return scene.getObjects().stream()
                .filter(object -> object != null && objectId.equals(object.getId()))
                .findFirst().orElse(null);
    }

    private RectShape sceneTokenShape(VttSceneObject object) {
        VttSceneCollisionBox collisionBox = object.getCollisionBox();
        double baseWidth = collisionBox == null ? object.getSize().getWidth() : collisionBox.getWidth();
        double baseHeight = collisionBox == null ? object.getSize().getHeight() : collisionBox.getHeight();
        double localOffsetX = collisionBox == null ? 0.0 : collisionBox.getOffsetX();
        if (object.getState().isFlippedHorizontally()) localOffsetX = -localOffsetX;
        double localOffsetY = collisionBox == null ? 0.0 : collisionBox.getOffsetY();
        VttSceneTransform transform = object.getTransform();
        Vec2d scaledOffset = new Vec2d(localOffsetX * transform.getScaleX(),
                localOffsetY * transform.getScaleY());
        Vec2d center = position(transform).add(rotate(scaledOffset, transform.getRotationDegrees()));
        return shape(center, transform.getRotationDegrees(),
                Math.abs(baseWidth * transform.getScaleX()),
                Math.abs(baseHeight * transform.getScaleY()));
    }

    private RectShape moved(RectShape shape, Vec2d offset) {
        return new RectShape(shape.center().add(offset), shape.axisX(), shape.axisY(),
                shape.halfWidth(), shape.halfHeight());
    }

    private Bounds sweptBounds(RectShape shape, Vec2d delta) {
        double extentX = Math.abs(shape.axisX().x()) * shape.halfWidth()
                + Math.abs(shape.axisY().x()) * shape.halfHeight();
        double extentY = Math.abs(shape.axisX().y()) * shape.halfWidth()
                + Math.abs(shape.axisY().y()) * shape.halfHeight();
        return new Bounds(
                shape.center().x() + Math.min(0.0, delta.x()) - extentX,
                shape.center().y() + Math.min(0.0, delta.y()) - extentY,
                shape.center().x() + Math.max(0.0, delta.x()) + extentX,
                shape.center().y() + Math.max(0.0, delta.y()) + extentY);
    }

    private Bounds shapeBounds(RectShape shape) {
        return sweptBounds(shape, Vec2d.ZERO);
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

    private Vec2d rotate(Vec2d point, double degrees) {
        double radians = Math.toRadians(degrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Vec2d(point.x() * cosine - point.y() * sine,
                point.x() * sine + point.y() * cosine);
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

    private final class ObstacleSpatialIndex {
        private static final double CELL_SIZE = 256.0;
        private static final long MAX_CELLS_PER_OBSTACLE = 4_096L;

        private final Map<CellKey, LinkedHashSet<Integer>> cells = new HashMap<>();
        private final Set<Integer> globalObstacles = new LinkedHashSet<>();
        private List<Obstacle> obstacles = List.of();
        private List<Bounds> bounds = List.of();
        private String sceneId;

        private void rebuild(VttScene scene, List<Obstacle> newObstacles) {
            cells.clear();
            globalObstacles.clear();
            sceneId = scene == null ? null : scene.getId();
            obstacles = newObstacles == null ? List.of() : List.copyOf(newObstacles);
            List<Bounds> rebuiltBounds = new ArrayList<>(obstacles.size());
            for (int index = 0; index < obstacles.size(); index++) {
                Bounds obstacleBounds = shapeBounds(obstacles.get(index).shape());
                rebuiltBounds.add(obstacleBounds);
                indexObstacle(index, obstacleBounds);
            }
            bounds = List.copyOf(rebuiltBounds);
        }

        private List<Obstacle> query(Bounds query) {
            if (query == null || !query.finite()) return obstacles;
            long firstX = cellCoordinate(query.minX());
            long lastX = cellCoordinate(query.maxX());
            long firstY = cellCoordinate(query.minY());
            long lastY = cellCoordinate(query.maxY());
            long columns = lastX - firstX + 1L;
            long rows = lastY - firstY + 1L;
            Set<Integer> candidates = new LinkedHashSet<>(globalObstacles);
            if (columns > 0L && rows > 0L && columns <= 10_000L && rows <= 10_000L
                    && columns * rows <= Math.max(64L, cells.size() * 4L)) {
                for (long x = firstX; x <= lastX; x++) {
                    for (long y = firstY; y <= lastY; y++) {
                        Set<Integer> values = cells.get(new CellKey(x, y));
                        if (values != null) candidates.addAll(values);
                    }
                }
            } else {
                for (int index = 0; index < bounds.size(); index++) {
                    if (bounds.get(index).intersects(query)) candidates.add(index);
                }
            }
            List<Obstacle> result = new ArrayList<>(candidates.size());
            for (int index : candidates) {
                if (index >= 0 && index < obstacles.size() && bounds.get(index).intersects(query)) {
                    result.add(obstacles.get(index));
                }
            }
            return List.copyOf(result);
        }

        private void indexObstacle(int index, Bounds obstacle) {
            long firstX = cellCoordinate(obstacle.minX());
            long lastX = cellCoordinate(obstacle.maxX());
            long firstY = cellCoordinate(obstacle.minY());
            long lastY = cellCoordinate(obstacle.maxY());
            long columns = lastX - firstX + 1L;
            long rows = lastY - firstY + 1L;
            if (columns <= 0L || rows <= 0L || columns > MAX_CELLS_PER_OBSTACLE
                    || rows > MAX_CELLS_PER_OBSTACLE
                    || columns * rows > MAX_CELLS_PER_OBSTACLE) {
                globalObstacles.add(index);
                return;
            }
            for (long x = firstX; x <= lastX; x++) {
                for (long y = firstY; y <= lastY; y++) {
                    cells.computeIfAbsent(new CellKey(x, y), ignored -> new LinkedHashSet<>()).add(index);
                }
            }
        }

        private boolean isBuiltFor(VttScene scene) {
            return scene != null && scene.getId().equals(sceneId);
        }

        private List<Obstacle> allObstacles() {
            return obstacles;
        }

        private long cellCoordinate(double value) {
            return (long) Math.floor(value / CELL_SIZE);
        }
    }

    private record Interval(double start, double end) {}
    private record Obstacle(String sourceId, RectShape shape) {}
    private record RectShape(
            Vec2d center, Vec2d axisX, Vec2d axisY, double halfWidth, double halfHeight
    ) {}
    private record CellKey(long x, long y) {}
    private record Bounds(double minX, double minY, double maxX, double maxY) {
        private boolean intersects(Bounds other) {
            return maxX >= other.minX && minX <= other.maxX
                    && maxY >= other.minY && minY <= other.maxY;
        }

        private boolean finite() {
            return Double.isFinite(minX) && Double.isFinite(minY)
                    && Double.isFinite(maxX) && Double.isFinite(maxY);
        }
    }
}
