package com.petrick.vtt.feature.canvas;

import com.petrick.vtt.core.math.Rectd;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.grid.GridRenderer;
import com.petrick.vtt.feature.selection.SelectionManager;
import com.petrick.vtt.platform.render.VRenderContext;

/**
 * Renderer inicial do canvas do VTT.
 *
 * Por enquanto, ele desenha:
 * - grid procedural
 * - objetos temporários do canvas
 * - destaque de seleção
 */
public final class CanvasRenderer {

    private static final int SELECTION_BORDER_COLOR = 0xFF0099FF;

    private final GridRenderer gridRenderer;

    public CanvasRenderer() {
        this.gridRenderer = new GridRenderer();
    }

    public void render(
            VRenderContext context,
            CanvasScene scene,
            SelectionManager selectionManager
    ) {
        gridRenderer.render(context);
        renderObjects(context, scene, selectionManager);
    }

    private void renderObjects(
            VRenderContext context,
            CanvasScene scene,
            SelectionManager selectionManager
    ) {
        for (CanvasObject object : scene.getObjects()) {
            renderObject(context, object);

            if (selectionManager.isSelected(object.id())) {
                renderSelectionBorder(context, object.bounds());
            }
        }
    }

    private void renderObject(VRenderContext context, CanvasObject object) {
        Rectd bounds = object.bounds();

        Vec2d topLeft = context.renderState().worldToScreen(bounds.position());
        Vec2d bottomRight = context.renderState().worldToScreen(new Vec2d(
                bounds.right(),
                bounds.bottom()
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
                object.color()
        );
    }

    private void renderSelectionBorder(VRenderContext context, Rectd worldBounds) {
        Vec2d topLeft = context.renderState().worldToScreen(worldBounds.position());
        Vec2d bottomRight = context.renderState().worldToScreen(new Vec2d(
                worldBounds.right(),
                worldBounds.bottom()
        ));

        int left = (int) Math.round(topLeft.x());
        int top = (int) Math.round(topLeft.y());
        int right = (int) Math.round(bottomRight.x());
        int bottom = (int) Math.round(bottomRight.y());

        context.graphics().hLine(left, right, top, SELECTION_BORDER_COLOR);
        context.graphics().hLine(left, right, bottom, SELECTION_BORDER_COLOR);
        context.graphics().vLine(left, top, bottom, SELECTION_BORDER_COLOR);
        context.graphics().vLine(right, top, bottom, SELECTION_BORDER_COLOR);
    }
}