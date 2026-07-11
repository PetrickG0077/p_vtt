package com.petrick.vtt.feature.tabletop.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttWall;
import com.petrick.vtt.feature.tabletop.VttDoor;
import com.petrick.vtt.platform.render.VRenderContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Renders persistent scene walls in world space. */
public final class SceneWallRenderer {
    private static final int BLOCKS_BOTH_COLOR = 0xFFFF8844;
    private static final int BLOCKS_VISION_COLOR = 0xFFCC66FF;
    private static final int BLOCKS_MOVEMENT_COLOR = 0xFF66AAFF;
    private static final int DECORATIVE_COLOR = 0xFFAAAAAA;

    public void render(VRenderContext context, VttScene scene) {
        if (scene == null) return;
        for (VttWall wall : scene.getWalls()) {
            if (wall != null && wall.isVisible()) renderWall(context, scene, wall);
        }
    }

    private void renderWall(VRenderContext context, VttScene scene, VttWall wall) {
        var transform = wall.getTransform();
        var size = wall.getSize();
        Vec2d center = context.renderState().worldToScreen(
                new Vec2d(transform.getX(), transform.getY()));
        double zoom = context.renderState().getCamera().getZoom();
        double worldWidth = Math.abs(size.getWidth() * transform.getScaleX());
        int height = Math.max(1, (int) Math.round(
                Math.abs(size.getHeight() * transform.getScaleY()) * zoom));
        int top = -height / 2;
        int bottom = top + height;
        int borderColor = getWallColor(wall);
        int fillColor = (borderColor & 0x00FFFFFF) | 0x66000000;

        PoseStack pose = context.graphics().pose();
        pose.pushPose();
        pose.translate(center.x(), center.y(), 0.0);
        pose.mulPose(Axis.ZP.rotationDegrees((float) transform.getRotationDegrees()));
        for (Segment segment : visibleSegments(scene, wall, worldWidth)) {
            int left = (int) Math.round(segment.start() * zoom);
            int right = (int) Math.round(segment.end() * zoom);
            if (right <= left) continue;
            context.graphics().fill(left, top, right, bottom, fillColor);
            context.graphics().hLine(left, right, top, borderColor);
            context.graphics().hLine(left, right, bottom, borderColor);
            context.graphics().vLine(left, top, bottom, borderColor);
            context.graphics().vLine(right, top, bottom, borderColor);
        }
        pose.popPose();
    }

    private List<Segment> visibleSegments(VttScene scene, VttWall wall, double wallWidth) {
        double halfWidth = wallWidth / 2.0;
        List<Segment> openings = new ArrayList<>();
        double radians = Math.toRadians(wall.getTransform().getRotationDegrees());
        Vec2d wallAxis = new Vec2d(Math.cos(radians), Math.sin(radians));
        Vec2d wallCenter = new Vec2d(wall.getTransform().getX(), wall.getTransform().getY());

        for (VttDoor door : scene.getDoors()) {
            if (door == null || !door.isVisible() || !wall.getId().equals(door.getWallId())) continue;
            Vec2d doorCenter = new Vec2d(door.getTransform().getX(), door.getTransform().getY());
            double localCenter = dot(doorCenter.subtract(wallCenter), wallAxis);
            double openingHalfWidth = projectedHalfExtent(door, wallAxis);
            double start = Math.max(-halfWidth, localCenter - openingHalfWidth);
            double end = Math.min(halfWidth, localCenter + openingHalfWidth);
            if (end > start) openings.add(new Segment(start, end));
        }

        openings.sort(Comparator.comparingDouble(Segment::start));
        List<Segment> merged = new ArrayList<>();
        for (Segment opening : openings) {
            if (merged.isEmpty() || opening.start() > merged.get(merged.size() - 1).end()) {
                merged.add(opening);
            } else {
                Segment previous = merged.remove(merged.size() - 1);
                merged.add(new Segment(previous.start(), Math.max(previous.end(), opening.end())));
            }
        }

        List<Segment> visible = new ArrayList<>();
        double cursor = -halfWidth;
        for (Segment opening : merged) {
            if (opening.start() > cursor) visible.add(new Segment(cursor, opening.start()));
            cursor = Math.max(cursor, opening.end());
        }
        if (cursor < halfWidth) visible.add(new Segment(cursor, halfWidth));
        return visible;
    }

    private double projectedHalfExtent(VttDoor door, Vec2d axis) {
        double radians = Math.toRadians(door.getTransform().getRotationDegrees());
        Vec2d doorX = new Vec2d(Math.cos(radians), Math.sin(radians));
        Vec2d doorY = new Vec2d(-Math.sin(radians), Math.cos(radians));
        double halfWidth = Math.abs(door.getSize().getWidth() * door.getTransform().getScaleX()) / 2.0;
        double halfHeight = Math.abs(door.getSize().getHeight() * door.getTransform().getScaleY()) / 2.0;
        return Math.abs(dot(doorX, axis)) * halfWidth + Math.abs(dot(doorY, axis)) * halfHeight;
    }

    private double dot(Vec2d first, Vec2d second) {
        return first.x() * second.x() + first.y() * second.y();
    }

    private int getWallColor(VttWall wall) {
        if (wall.isBlocksVision() && wall.isBlocksMovement()) return BLOCKS_BOTH_COLOR;
        if (wall.isBlocksVision()) return BLOCKS_VISION_COLOR;
        if (wall.isBlocksMovement()) return BLOCKS_MOVEMENT_COLOR;
        return DECORATIVE_COLOR;
    }

    private record Segment(double start, double end) {}
}
