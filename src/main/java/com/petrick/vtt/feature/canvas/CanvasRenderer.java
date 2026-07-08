package com.petrick.vtt.feature.canvas;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.canvas.visual.CanvasVisualRenderer;
import com.petrick.vtt.feature.grid.GridRenderer;
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

    private static final int ROTATION_HANDLE_COLOR = 0xFFFFFFFF;

    private static final int ROTATION_HANDLE_BORDER_COLOR = 0xFF0099FF;

    private static final int ROTATION_HANDLE_SIZE = 6;

    private static final double ROTATION_HANDLE_DISTANCE = 24.0;

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
                renderRotationHandle(context, object);
            }
        }
    }

    private void renderObject(VRenderContext context, CanvasObject object) {
        Vec2d screenCenter = context.renderState().worldToScreen(object.transform().position());

        double zoom = context.renderState().getCamera().getZoom();

        double width = object.size().x() * object.transform().scale().x() * zoom;
        double height = object.size().y() * object.transform().scale().y() * zoom;

        int drawWidth = (int) Math.round(width);
        int drawHeight = (int) Math.round(height);

        if (drawWidth == 0 || drawHeight == 0) {
            return;
        }

        int localLeft = -drawWidth / 2;
        int localTop = -drawHeight / 2;
        int localRight = localLeft + drawWidth;
        int localBottom = localTop + drawHeight;

        PoseStack poseStack = context.graphics().pose();

        boolean flipped = object.flippedHorizontally();

        poseStack.pushPose();

        poseStack.translate(screenCenter.x(), screenCenter.y(), 0.0);
        poseStack.mulPose(Axis.ZP.rotationDegrees((float) object.transform().rotationDegrees()));

        if (flipped) {
            /*
             * O scale negativo espelha a imagem, mas pode inverter a face do quad.
             * Por isso desativamos o culling só durante o desenho do objeto flipado.
             */
            RenderSystem.disableCull();
            poseStack.scale(-1.0F, 1.0F, 1.0F);
        }

        visualRenderer.render(
                context,
                object.currentVisual(),
                localLeft,
                localTop,
                localRight,
                localBottom
        );

        poseStack.popPose();

        if (flipped) {
            RenderSystem.enableCull();
        }
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

    private void renderRotationHandle(VRenderContext context, CanvasObject object) {
        Vec2d screenPosition = getRotationHandleScreenPosition(context, object);

        int centerX = (int) Math.round(screenPosition.x());
        int centerY = (int) Math.round(screenPosition.y());

        int half = ROTATION_HANDLE_SIZE / 2;

        int left = centerX - half;
        int top = centerY - half;
        int right = centerX + half;
        int bottom = centerY + half;

        context.graphics().fill(
                left,
                top,
                right,
                bottom,
                ROTATION_HANDLE_COLOR
        );

        context.graphics().hLine(left, right, top, ROTATION_HANDLE_BORDER_COLOR);
        context.graphics().hLine(left, right, bottom, ROTATION_HANDLE_BORDER_COLOR);
        context.graphics().vLine(left, top, bottom, ROTATION_HANDLE_BORDER_COLOR);
        context.graphics().vLine(right, top, bottom, ROTATION_HANDLE_BORDER_COLOR);
    }

    private Vec2d getRotationHandleScreenPosition(VRenderContext context, CanvasObject object) {
        Vec2d center = context.renderState().worldToScreen(object.transform().position());

        Vec2d topCenterWorld = getTopCenterWorld(object);
        Vec2d topCenter = context.renderState().worldToScreen(topCenterWorld);

        Vec2d direction = topCenter.subtract(center);

        if (direction.length() <= 0.0001) {
            direction = new Vec2d(0.0, -1.0);
        } else {
            direction = direction.normalize();
        }

        return topCenter.add(direction.multiply(ROTATION_HANDLE_DISTANCE));
    }

    private Vec2d getTopCenterWorld(CanvasObject object) {
        Vec2d topLeft = object.worldTopLeft();
        Vec2d topRight = object.worldTopRight();

        return new Vec2d(
                (topLeft.x() + topRight.x()) / 2.0,
                (topLeft.y() + topRight.y()) / 2.0
        );
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