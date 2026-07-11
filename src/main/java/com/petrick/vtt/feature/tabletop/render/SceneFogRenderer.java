package com.petrick.vtt.feature.tabletop.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.VttFogArea;
import com.petrick.vtt.feature.tabletop.VttFogOfWar;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.platform.render.VRenderContext;

/** Renders localized rectangular fog areas as the final scene overlay. */
public final class SceneFogRenderer {
    private static final int MASTER_FOG_COLOR = 0x9908080C;
    private static final int PLAYER_FOG_COLOR = 0xFF08080C;

    public void render(VRenderContext context, VttScene scene, boolean masterView) {
        if (scene == null) return;
        VttFogOfWar fog = scene.getFogOfWar();
        if (!fog.isEnabled()) return;
        int fogColor = masterView ? MASTER_FOG_COLOR : PLAYER_FOG_COLOR;

        for (VttFogArea hiddenArea : fog.getHiddenAreas()) {
            if (hiddenArea == null || !hiddenArea.isVisible()) continue;
            renderArea(context, hiddenArea, fogColor);
        }
    }

    private void renderArea(VRenderContext context, VttFogArea area, int fogColor) {
        var transform = area.getTransform();
        Vec2d center = context.renderState().worldToScreen(new Vec2d(transform.getX(), transform.getY()));
        double zoom = context.renderState().getCamera().getZoom();
        int width = Math.max(1, (int) Math.round(
                Math.abs(area.getSize().getWidth() * transform.getScaleX()) * zoom));
        int height = Math.max(1, (int) Math.round(
                Math.abs(area.getSize().getHeight() * transform.getScaleY()) * zoom));
        PoseStack pose = context.graphics().pose();
        pose.pushPose();
        pose.translate(center.x(), center.y(), 0.0);
        pose.mulPose(Axis.ZP.rotationDegrees((float) transform.getRotationDegrees()));
        context.graphics().fill(-width / 2, -height / 2,
                -width / 2 + width, -height / 2 + height, fogColor);
        pose.popPose();
    }
}
