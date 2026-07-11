package com.petrick.vtt.feature.tabletop.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttWall;
import com.petrick.vtt.platform.render.VRenderContext;

/** Renders persistent scene walls in world space. */
public final class SceneWallRenderer {
    private static final int BLOCKS_BOTH_COLOR = 0xFFFF8844;
    private static final int BLOCKS_VISION_COLOR = 0xFFCC66FF;
    private static final int BLOCKS_MOVEMENT_COLOR = 0xFF66AAFF;
    private static final int DECORATIVE_COLOR = 0xFFAAAAAA;

    public void render(VRenderContext context, VttScene scene) {
        if (scene == null) return;
        for (VttWall wall : scene.getWalls()) {
            if (wall != null && wall.isVisible()) renderWall(context, wall);
        }
    }

    private void renderWall(VRenderContext context, VttWall wall) {
        var transform = wall.getTransform();
        var size = wall.getSize();
        Vec2d center = context.renderState().worldToScreen(
                new Vec2d(transform.getX(), transform.getY()));
        double zoom = context.renderState().getCamera().getZoom();
        int width = Math.max(1, (int) Math.round(
                Math.abs(size.getWidth() * transform.getScaleX()) * zoom));
        int height = Math.max(1, (int) Math.round(
                Math.abs(size.getHeight() * transform.getScaleY()) * zoom));
        int left = -width / 2;
        int top = -height / 2;
        int right = left + width;
        int bottom = top + height;
        int borderColor = getWallColor(wall);
        int fillColor = (borderColor & 0x00FFFFFF) | 0x66000000;

        PoseStack pose = context.graphics().pose();
        pose.pushPose();
        pose.translate(center.x(), center.y(), 0.0);
        pose.mulPose(Axis.ZP.rotationDegrees((float) transform.getRotationDegrees()));
        context.graphics().fill(left, top, right, bottom, fillColor);
        context.graphics().hLine(left, right, top, borderColor);
        context.graphics().hLine(left, right, bottom, borderColor);
        context.graphics().vLine(left, top, bottom, borderColor);
        context.graphics().vLine(right, top, bottom, borderColor);
        pose.popPose();
    }

    private int getWallColor(VttWall wall) {
        if (wall.isBlocksVision() && wall.isBlocksMovement()) return BLOCKS_BOTH_COLOR;
        if (wall.isBlocksVision()) return BLOCKS_VISION_COLOR;
        if (wall.isBlocksMovement()) return BLOCKS_MOVEMENT_COLOR;
        return DECORATIVE_COLOR;
    }
}
