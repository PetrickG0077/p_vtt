package com.petrick.vtt.feature.canvas;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.grid.GridRenderer;
import com.petrick.vtt.feature.canvas.visual.CanvasVisualRenderer;
import com.petrick.vtt.feature.selection.SelectionManager;
import com.petrick.vtt.platform.render.VRenderContext;

/**
 * Renderer inicial do canvas do VTT.
 *
 * Por enquanto, ele desenha:
 * - grid procedural
 * - objetos temporários do canvas
 * - borda de seleção rotacionada
 * - handles visuais nos cantos reais
 */
public final class CanvasRenderer {

    private static final int SELECTION_BORDER_COLOR = 0xFF0099FF;

    private static final int HANDLE_FILL_COLOR = 0xFFFFFFFF;

    private static final int HANDLE_BORDER_COLOR = 0xFF0099FF;

    private static final int HANDLE_SIZE = 6;

    private final CanvasVisualRenderer visualRenderer;

    private final GridRenderer gridRenderer;

    public CanvasRenderer() {
        this.gridRenderer = new GridRenderer();
        this.visualRenderer = new CanvasVisualRenderer();
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
            if (!object.visible()) {
                continue;
            }

            renderObject(context, object);

            if (selectionManager.isSelected(object.id())) {
                renderSelectionBorder(context, object);
                renderSelectionHandles(context, object);
            }
        }
    }

    private void renderObject(VRenderContext context, CanvasObject object) {
        Vec2d screenCenter = context.renderState().worldToScreen(object.transform().position());

        double zoom = context.renderState().getCamera().getZoom();

        double width = object.size().x() * object.transform().scale().x() * zoom;
        double height = object.size().y() * object.transform().scale().y() * zoom;

        int left = (int) Math.round(-width / 2.0);
        int top = (int) Math.round(-height / 2.0);
        int right = (int) Math.round(width / 2.0);
        int bottom = (int) Math.round(height / 2.0);

        PoseStack poseStack = context.graphics().pose();

        poseStack.pushPose();

        poseStack.translate(
                screenCenter.x(),
                screenCenter.y(),
                0.0
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees((float) object.transform().rotationDegrees())
        );

        visualRenderer.render(
                context,
                object.visual(),
                left,
                top,
                right,
                bottom
        );

        poseStack.popPose();
    }

    private void renderSelectionBorder(VRenderContext context, CanvasObject object) {
        Vec2d screenCenter = context.renderState().worldToScreen(object.transform().position());

        double zoom = context.renderState().getCamera().getZoom();

        double width = object.size().x() * object.transform().scale().x() * zoom;
        double height = object.size().y() * object.transform().scale().y() * zoom;

        int left = (int) Math.round(-width / 2.0);
        int top = (int) Math.round(-height / 2.0);
        int right = (int) Math.round(width / 2.0);
        int bottom = (int) Math.round(height / 2.0);

        PoseStack poseStack = context.graphics().pose();

        poseStack.pushPose();

        poseStack.translate(
                screenCenter.x(),
                screenCenter.y(),
                0.0
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees((float) object.transform().rotationDegrees())
        );

        context.graphics().hLine(left, right, top, SELECTION_BORDER_COLOR);
        context.graphics().hLine(left, right, bottom, SELECTION_BORDER_COLOR);
        context.graphics().vLine(left, top, bottom, SELECTION_BORDER_COLOR);
        context.graphics().vLine(right, top, bottom, SELECTION_BORDER_COLOR);

        poseStack.popPose();
    }

    private void renderSelectionHandles(VRenderContext context, CanvasObject object) {
        renderHandle(context, context.renderState().worldToScreen(object.worldTopLeft()));
        renderHandle(context, context.renderState().worldToScreen(object.worldTopRight()));
        renderHandle(context, context.renderState().worldToScreen(object.worldBottomLeft()));
        renderHandle(context, context.renderState().worldToScreen(object.worldBottomRight()));
    }

    private void renderHandle(VRenderContext context, Vec2d screenCenter) {
        int centerX = (int) Math.round(screenCenter.x());
        int centerY = (int) Math.round(screenCenter.y());

        int half = HANDLE_SIZE / 2;

        int left = centerX - half;
        int top = centerY - half;
        int right = centerX + half;
        int bottom = centerY + half;

        context.graphics().fill(
                left,
                top,
                right,
                bottom,
                HANDLE_FILL_COLOR
        );

        context.graphics().hLine(left, right, top, HANDLE_BORDER_COLOR);
        context.graphics().hLine(left, right, bottom, HANDLE_BORDER_COLOR);
        context.graphics().vLine(left, top, bottom, HANDLE_BORDER_COLOR);
        context.graphics().vLine(right, top, bottom, HANDLE_BORDER_COLOR);
    }
}