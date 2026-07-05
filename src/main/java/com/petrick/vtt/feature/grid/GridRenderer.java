package com.petrick.vtt.feature.grid;

import com.petrick.vtt.core.math.Rectd;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.platform.render.VRenderContext;

/**
 * Renderer procedural do grid do VTT.
 *
 * Ele desenha apenas as linhas visíveis na tela,
 * com base na câmera e no viewport atual.
 */
public final class GridRenderer {

    private static final double GRID_SIZE = 64.0;

    private static final int GRID_COLOR = 0x33FFFFFF;

    private static final int AXIS_COLOR = 0xAAFFFFFF;

    public void render(VRenderContext context) {
        Rectd visibleWorldBounds = context.renderState().getVisibleWorldBounds();

        renderVerticalLines(context, visibleWorldBounds);
        renderHorizontalLines(context, visibleWorldBounds);
        renderAxisLines(context, visibleWorldBounds);
    }

    private void renderVerticalLines(VRenderContext context, Rectd visibleWorldBounds) {
        double startX = Math.floor(visibleWorldBounds.left() / GRID_SIZE) * GRID_SIZE;
        double endX = visibleWorldBounds.right();

        for (double x = startX; x <= endX; x += GRID_SIZE) {
            Vec2d top = context.renderState().worldToScreen(new Vec2d(x, visibleWorldBounds.top()));
            Vec2d bottom = context.renderState().worldToScreen(new Vec2d(x, visibleWorldBounds.bottom()));

            context.graphics().vLine(
                    (int) Math.round(top.x()),
                    (int) Math.round(top.y()),
                    (int) Math.round(bottom.y()),
                    GRID_COLOR
            );
        }
    }

    private void renderHorizontalLines(VRenderContext context, Rectd visibleWorldBounds) {
        double startY = Math.floor(visibleWorldBounds.top() / GRID_SIZE) * GRID_SIZE;
        double endY = visibleWorldBounds.bottom();

        for (double y = startY; y <= endY; y += GRID_SIZE) {
            Vec2d left = context.renderState().worldToScreen(new Vec2d(visibleWorldBounds.left(), y));
            Vec2d right = context.renderState().worldToScreen(new Vec2d(visibleWorldBounds.right(), y));

            context.graphics().hLine(
                    (int) Math.round(left.x()),
                    (int) Math.round(right.x()),
                    (int) Math.round(left.y()),
                    GRID_COLOR
            );
        }
    }

    private void renderAxisLines(VRenderContext context, Rectd visibleWorldBounds) {
        if (visibleWorldBounds.left() <= 0.0 && visibleWorldBounds.right() >= 0.0) {
            renderVerticalAxis(context, visibleWorldBounds);
        }

        if (visibleWorldBounds.top() <= 0.0 && visibleWorldBounds.bottom() >= 0.0) {
            renderHorizontalAxis(context, visibleWorldBounds);
        }
    }

    private void renderVerticalAxis(VRenderContext context, Rectd visibleWorldBounds) {
        Vec2d top = context.renderState().worldToScreen(new Vec2d(0.0, visibleWorldBounds.top()));
        Vec2d bottom = context.renderState().worldToScreen(new Vec2d(0.0, visibleWorldBounds.bottom()));

        int x = (int) Math.round(top.x());

        context.graphics().vLine(
                x,
                (int) Math.round(top.y()),
                (int) Math.round(bottom.y()),
                AXIS_COLOR
        );
    }

    private void renderHorizontalAxis(VRenderContext context, Rectd visibleWorldBounds) {
        Vec2d left = context.renderState().worldToScreen(new Vec2d(visibleWorldBounds.left(), 0.0));
        Vec2d right = context.renderState().worldToScreen(new Vec2d(visibleWorldBounds.right(), 0.0));

        int y = (int) Math.round(left.y());

        context.graphics().hLine(
                (int) Math.round(left.x()),
                (int) Math.round(right.x()),
                y,
                AXIS_COLOR
        );
    }
}