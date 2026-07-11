package com.petrick.vtt.feature.tabletop.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.VttDoor;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.platform.render.VRenderContext;

/** Renders persistent doors above scene walls. */
public final class SceneDoorRenderer {
    public void render(VRenderContext context, VttScene scene) {
        if (scene == null) return;
        for (VttDoor door : scene.getDoors()) {
            if (door != null && door.isVisible()) renderDoor(context, door);
        }
    }

    private void renderDoor(VRenderContext context, VttDoor door) {
        var transform = door.getTransform();
        var size = door.getSize();
        Vec2d center = context.renderState().worldToScreen(new Vec2d(transform.getX(), transform.getY()));
        double zoom = context.renderState().getCamera().getZoom();
        int width = Math.max(1, (int) Math.round(Math.abs(size.getWidth() * transform.getScaleX()) * zoom));
        int height = Math.max(1, (int) Math.round(Math.abs(size.getHeight() * transform.getScaleY()) * zoom));
        int left = -width / 2;
        int top = -height / 2;
        int right = left + width;
        int bottom = top + height;
        int border = door.isLocked() ? 0xFFFF55FF : door.isOpen() ? 0xFF55FF88 : 0xFFFFCC44;
        int fill = (border & 0x00FFFFFF) | (door.isOpen() ? 0x22000000 : 0x88000000);

        PoseStack pose = context.graphics().pose();
        pose.pushPose();
        pose.translate(center.x(), center.y(), 0.0);
        pose.mulPose(Axis.ZP.rotationDegrees((float) transform.getRotationDegrees()));
        context.graphics().fill(left, top, right, bottom, fill);
        context.graphics().hLine(left, right, top, border);
        context.graphics().hLine(left, right, bottom, border);
        context.graphics().vLine(left, top, bottom, border);
        context.graphics().vLine(right, top, bottom, border);
        if (door.isOpen()) context.graphics().hLine(0, right, 0, border);
        pose.popPose();
    }
}
