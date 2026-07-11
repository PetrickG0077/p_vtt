package com.petrick.vtt.feature.tabletop.vision;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttWall;

import java.util.ArrayList;
import java.util.List;

/** Builds transient raycast geometry from persistent tabletop objects. */
public final class SceneVisionGeometry {
    public List<VisionSegment> build(VttScene scene) {
        if (scene == null) return List.of();
        List<VisionSegment> segments = new ArrayList<>();
        for (VttWall wall : scene.getWalls()) {
            if (wall == null || !wall.isVisible() || !wall.isBlocksVision()) continue;
            addWallEdges(segments, wall);
        }
        return List.copyOf(segments);
    }

    private void addWallEdges(List<VisionSegment> target, VttWall wall) {
        Vec2d[] corners = corners(wall);
        for (int index = 0; index < corners.length; index++) {
            target.add(new VisionSegment(wall.getId(), corners[index], corners[(index + 1) % corners.length]));
        }
    }

    private Vec2d[] corners(VttWall wall) {
        double halfWidth = Math.abs(wall.getSize().getWidth() * wall.getTransform().getScaleX()) / 2.0;
        double halfHeight = Math.abs(wall.getSize().getHeight() * wall.getTransform().getScaleY()) / 2.0;
        return new Vec2d[]{
                localToWorld(wall, -halfWidth, -halfHeight),
                localToWorld(wall, halfWidth, -halfHeight),
                localToWorld(wall, halfWidth, halfHeight),
                localToWorld(wall, -halfWidth, halfHeight)
        };
    }

    private Vec2d localToWorld(VttWall wall, double localX, double localY) {
        double radians = Math.toRadians(wall.getTransform().getRotationDegrees());
        double x = localX * Math.cos(radians) - localY * Math.sin(radians);
        double y = localX * Math.sin(radians) + localY * Math.cos(radians);
        return new Vec2d(wall.getTransform().getX() + x, wall.getTransform().getY() + y);
    }
}
