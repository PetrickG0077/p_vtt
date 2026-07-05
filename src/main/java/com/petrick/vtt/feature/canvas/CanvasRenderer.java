package com.petrick.vtt.feature.canvas;

import com.petrick.vtt.core.math.Rectd;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.platform.render.VRenderContext;

/**
 * Renderer inicial do canvas do VTT.
 *
 * Por enquanto, ele desenha apenas um quadrado de teste
 * usando coordenadas do mundo.
 */
public final class CanvasRenderer {

    private static final Rectd TEST_RECT = new Rectd(
            -50.0,
            -50.0,
            100.0,
            100.0
    );

    public void render(VRenderContext context) {
        renderTestRect(context);
    }

    private void renderTestRect(VRenderContext context) {
        Vec2d topLeft = context.renderState().worldToScreen(TEST_RECT.position());
        Vec2d bottomRight = context.renderState().worldToScreen(new Vec2d(
                TEST_RECT.right(),
                TEST_RECT.bottom()
        ));

        int x1 = (int) Math.round(topLeft.x());
        int y1 = (int) Math.round(topLeft.y());
        int x2 = (int) Math.round(bottomRight.x());
        int y2 = (int) Math.round(bottomRight.y());

        context.graphics().fill(
                x1,
                y1,
                x2,
                y2,
                0xFFFFFFFF
        );
    }
}