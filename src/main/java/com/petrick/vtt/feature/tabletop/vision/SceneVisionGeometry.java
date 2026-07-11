package com.petrick.vtt.feature.tabletop.vision;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.VttDoor;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneTransform;
import com.petrick.vtt.feature.tabletop.VttWall;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Builds transient raycast geometry from persistent walls and doors. */
public final class SceneVisionGeometry {
    public List<VisionSegment> build(VttScene scene) {
        if (scene == null) return List.of();
        List<VisionSegment> segments = new ArrayList<>();
        for (VttWall wall : scene.getWalls()) {
            if (wall == null || !wall.isVisible() || !wall.isBlocksVision()) continue;
            addWallWithDoorOpenings(segments, scene, wall);
        }
        for (VttDoor door : scene.getDoors()) {
            if (door == null || !door.isVisible() || !door.blocksVision()) continue;
            addRectangleEdges(segments, "door:" + door.getId(), door.getTransform(),
                    actualWidth(door), actualHeight(door));
        }
        return List.copyOf(segments);
    }

    private void addWallWithDoorOpenings(List<VisionSegment> target, VttScene scene, VttWall wall) {
        double halfWidth = actualWidth(wall) / 2.0;
        double halfHeight = actualHeight(wall) / 2.0;
        List<Interval> openings = doorOpenings(scene, wall, halfWidth);
        if (openings.isEmpty()) {
            addRectangleEdges(target, "wall:" + wall.getId(), wall.getTransform(),
                    halfWidth * 2.0, halfHeight * 2.0);
            return;
        }
        String sourceId = "wall:" + wall.getId();
        double cursor = -halfWidth;
        for (Interval opening : openings) {
            if (opening.start() > cursor) {
                addLongWallEdges(target, sourceId, wall, cursor, opening.start(), halfHeight);
            }
            addLocalSegment(target, sourceId, wall.getTransform(),
                    opening.start(), -halfHeight, opening.start(), halfHeight);
            addLocalSegment(target, sourceId, wall.getTransform(),
                    opening.end(), -halfHeight, opening.end(), halfHeight);
            cursor = Math.max(cursor, opening.end());
        }
        if (cursor < halfWidth) addLongWallEdges(target, sourceId, wall, cursor, halfWidth, halfHeight);
        addLocalSegment(target, sourceId, wall.getTransform(),
                -halfWidth, -halfHeight, -halfWidth, halfHeight);
        addLocalSegment(target, sourceId, wall.getTransform(),
                halfWidth, -halfHeight, halfWidth, halfHeight);
    }

    private List<Interval> doorOpenings(VttScene scene, VttWall wall, double wallHalfWidth) {
        List<Interval> raw = new ArrayList<>();
        Vec2d wallCenter = position(wall.getTransform());
        Vec2d wallAxis = axis(wall.getTransform().getRotationDegrees());
        for (VttDoor door : scene.getDoors()) {
            if (door == null || !door.isVisible() || !wall.getId().equals(door.getWallId())) continue;
            double center = position(door.getTransform()).subtract(wallCenter).dot(wallAxis);
            double halfExtent = projectedHalfExtent(door, wallAxis);
            double start = Math.max(-wallHalfWidth, center - halfExtent);
            double end = Math.min(wallHalfWidth, center + halfExtent);
            if (end > start) raw.add(new Interval(start, end));
        }
        raw.sort(Comparator.comparingDouble(Interval::start));
        List<Interval> merged = new ArrayList<>();
        for (Interval current : raw) {
            if (merged.isEmpty() || current.start() > merged.get(merged.size() - 1).end()) {
                merged.add(current);
            } else {
                Interval previous = merged.remove(merged.size() - 1);
                merged.add(new Interval(previous.start(), Math.max(previous.end(), current.end())));
            }
        }
        return merged;
    }

    private void addLongWallEdges(List<VisionSegment> target, String sourceId, VttWall wall,
                                  double start, double end, double halfHeight) {
        addLocalSegment(target, sourceId, wall.getTransform(), start, -halfHeight, end, -halfHeight);
        addLocalSegment(target, sourceId, wall.getTransform(), start, halfHeight, end, halfHeight);
    }

    private void addRectangleEdges(List<VisionSegment> target, String sourceId,
                                   VttSceneTransform transform, double width, double height) {
        double halfWidth = width / 2.0;
        double halfHeight = height / 2.0;
        Vec2d[] corners = {
                localToWorld(transform, -halfWidth, -halfHeight),
                localToWorld(transform, halfWidth, -halfHeight),
                localToWorld(transform, halfWidth, halfHeight),
                localToWorld(transform, -halfWidth, halfHeight)
        };
        for (int index = 0; index < corners.length; index++) {
            target.add(new VisionSegment(sourceId, corners[index], corners[(index + 1) % corners.length]));
        }
    }

    private void addLocalSegment(List<VisionSegment> target, String sourceId,
                                 VttSceneTransform transform,
                                 double startX, double startY, double endX, double endY) {
        target.add(new VisionSegment(sourceId,
                localToWorld(transform, startX, startY), localToWorld(transform, endX, endY)));
    }

    private Vec2d localToWorld(VttSceneTransform transform, double localX, double localY) {
        double radians = Math.toRadians(transform.getRotationDegrees());
        double x = localX * Math.cos(radians) - localY * Math.sin(radians);
        double y = localX * Math.sin(radians) + localY * Math.cos(radians);
        return new Vec2d(transform.getX() + x, transform.getY() + y);
    }

    private double projectedHalfExtent(VttDoor door, Vec2d projectionAxis) {
        Vec2d doorX = axis(door.getTransform().getRotationDegrees());
        Vec2d doorY = new Vec2d(-doorX.y(), doorX.x());
        return Math.abs(doorX.dot(projectionAxis)) * actualWidth(door) / 2.0
                + Math.abs(doorY.dot(projectionAxis)) * actualHeight(door) / 2.0;
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

    private record Interval(double start, double end) {}
}
