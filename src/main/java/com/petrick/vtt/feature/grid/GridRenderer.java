package com.petrick.vtt.feature.grid;

import com.petrick.vtt.core.math.Rectd;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.VttSceneGrid;
import com.petrick.vtt.platform.render.VRenderContext;

/** Procedural grid renderer configured by the active scene. */
public final class GridRenderer {

    public void render(VRenderContext context, VttSceneGrid grid) {
        if (grid == null || grid.getOpacity() <= 0.0) return;
        Rectd visibleWorldBounds = context.renderState().getVisibleWorldBounds();
        int gridColor = color(grid.getColorRgb(), grid.getOpacity());

        renderVerticalLines(context, visibleWorldBounds, grid, gridColor);
        renderHorizontalLines(context, visibleWorldBounds, grid, gridColor);
        renderAxisLines(context, visibleWorldBounds, grid, gridColor);
    }

    private void renderVerticalLines(
            VRenderContext context,
            Rectd visibleWorldBounds,
            VttSceneGrid grid,
            int color
    ) {
        double gridSize = grid.getGridSize();
        double startX = Math.floor(visibleWorldBounds.left() / gridSize) * gridSize;
        double endX = visibleWorldBounds.right();

        for (double x = startX; x <= endX; x += gridSize) {
            if (Math.abs(x) < 0.0001) continue;
            Vec2d top = context.renderState().worldToScreen(new Vec2d(x, visibleWorldBounds.top()));
            Vec2d bottom = context.renderState().worldToScreen(
                    new Vec2d(x, visibleWorldBounds.bottom()));
            drawVertical(context, (int) Math.round(top.x()), (int) Math.round(top.y()),
                    (int) Math.round(bottom.y()), grid.getLineWidth(), color);
        }
    }

    private void renderHorizontalLines(
            VRenderContext context,
            Rectd visibleWorldBounds,
            VttSceneGrid grid,
            int color
    ) {
        double gridSize = grid.getGridSize();
        double startY = Math.floor(visibleWorldBounds.top() / gridSize) * gridSize;
        double endY = visibleWorldBounds.bottom();

        for (double y = startY; y <= endY; y += gridSize) {
            if (Math.abs(y) < 0.0001) continue;
            Vec2d left = context.renderState().worldToScreen(
                    new Vec2d(visibleWorldBounds.left(), y));
            Vec2d right = context.renderState().worldToScreen(
                    new Vec2d(visibleWorldBounds.right(), y));
            drawHorizontal(context, (int) Math.round(left.x()), (int) Math.round(right.x()),
                    (int) Math.round(left.y()), grid.getLineWidth(), color);
        }
    }

    private void renderAxisLines(
            VRenderContext context,
            Rectd visibleWorldBounds,
            VttSceneGrid grid,
            int color
    ) {
        int axisWidth = grid.getLineWidth();
        if (visibleWorldBounds.left() <= 0.0 && visibleWorldBounds.right() >= 0.0) {
            Vec2d top = context.renderState().worldToScreen(
                    new Vec2d(0.0, visibleWorldBounds.top()));
            Vec2d bottom = context.renderState().worldToScreen(
                    new Vec2d(0.0, visibleWorldBounds.bottom()));
            drawVertical(context, (int) Math.round(top.x()), (int) Math.round(top.y()),
                    (int) Math.round(bottom.y()), axisWidth, color);
        }

        if (visibleWorldBounds.top() <= 0.0 && visibleWorldBounds.bottom() >= 0.0) {
            Vec2d left = context.renderState().worldToScreen(
                    new Vec2d(visibleWorldBounds.left(), 0.0));
            Vec2d right = context.renderState().worldToScreen(
                    new Vec2d(visibleWorldBounds.right(), 0.0));
            drawHorizontal(context, (int) Math.round(left.x()), (int) Math.round(right.x()),
                    (int) Math.round(left.y()), axisWidth, color);
        }
    }

    private void drawVertical(
            VRenderContext context, int x, int top, int bottom, int width, int color
    ) {
        int left = x - width / 2;
        context.graphics().fill(left, Math.min(top, bottom), left + width,
                Math.max(top, bottom) + 1, color);
    }

    private void drawHorizontal(
            VRenderContext context, int left, int right, int y, int width, int color
    ) {
        int top = y - width / 2;
        context.graphics().fill(Math.min(left, right), top,
                Math.max(left, right) + 1, top + width, color);
    }

    private int color(int rgb, double opacity) {
        int alpha = (int) Math.round(Math.max(0.0, Math.min(1.0, opacity)) * 255.0);
        return alpha << 24 | rgb & 0x00FFFFFF;
    }
}
