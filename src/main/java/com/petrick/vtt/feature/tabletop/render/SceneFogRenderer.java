package com.petrick.vtt.feature.tabletop.render;

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
            ScreenRect rect = screenBounds(context, hiddenArea);
            if (rect != null) fillRect(context, rect, fogColor);
        }
    }

    private ScreenRect screenBounds(VRenderContext context, VttFogArea area) {
        var transform = area.getTransform();
        double halfWidth = Math.abs(area.getSize().getWidth() * transform.getScaleX()) / 2.0;
        double halfHeight = Math.abs(area.getSize().getHeight() * transform.getScaleY()) / 2.0;
        double radians = Math.toRadians(transform.getRotationDegrees());
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        double[][] corners = {
                {-halfWidth, -halfHeight}, {halfWidth, -halfHeight},
                {halfWidth, halfHeight}, {-halfWidth, halfHeight}
        };
        for (double[] corner : corners) {
            double worldX = transform.getX() + corner[0] * cos - corner[1] * sin;
            double worldY = transform.getY() + corner[0] * sin + corner[1] * cos;
            Vec2d screen = context.renderState().worldToScreen(new Vec2d(worldX, worldY));
            minX = Math.min(minX, screen.x());
            minY = Math.min(minY, screen.y());
            maxX = Math.max(maxX, screen.x());
            maxY = Math.max(maxY, screen.y());
        }

        int left = Math.max(0, (int) Math.floor(minX));
        int top = Math.max(0, (int) Math.floor(minY));
        int right = Math.min(context.screenWidth(), (int) Math.ceil(maxX));
        int bottom = Math.min(context.screenHeight(), (int) Math.ceil(maxY));
        if (right <= left || bottom <= top) return null;
        return new ScreenRect(left, top, right, bottom);
    }

    private void fillRect(VRenderContext context, ScreenRect rect, int fogColor) {
        context.graphics().fill(rect.left(), rect.top(), rect.right(), rect.bottom(), fogColor);
    }

    private record ScreenRect(int left, int top, int right, int bottom) {}
}
