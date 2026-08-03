package com.petrick.vtt.feature.canvas;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.asset.animation.AnimatedTextureService;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnailRegistry;
import com.petrick.vtt.feature.canvas.visual.CanvasVisualRenderer;
import com.petrick.vtt.feature.grid.GridRenderer;
import com.petrick.vtt.feature.selection.SelectionManager;
import com.petrick.vtt.feature.attachment.AttachmentVisibilityResolver;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.render.SceneBackgroundRenderer;
import com.petrick.vtt.feature.tabletop.render.SceneWallRenderer;
import com.petrick.vtt.feature.tabletop.render.SceneDoorRenderer;
import com.petrick.vtt.feature.tabletop.render.SceneFogRenderer;
import com.petrick.vtt.feature.tabletop.render.SceneVisionDebugRenderer;
import com.petrick.vtt.feature.tabletop.render.SceneVisionMaskRenderer;
import com.petrick.vtt.feature.tabletop.vision.AuthoritativeVisionRegion;
import com.petrick.vtt.platform.render.VRenderContext;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

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
    private final SceneBackgroundRenderer sceneBackgroundRenderer;
    private final SceneWallRenderer sceneWallRenderer;
    private final SceneDoorRenderer sceneDoorRenderer;
    private final SceneFogRenderer sceneFogRenderer;
    private final SceneVisionDebugRenderer sceneVisionDebugRenderer;
    private final SceneVisionMaskRenderer sceneVisionMaskRenderer;

    public CanvasRenderer(AnimatedTextureService animatedTextureService, AssetRegistry assetRegistry,
                          AssetThumbnailRegistry thumbnailRegistry) {
        this.gridRenderer = new GridRenderer();
        this.visualRenderer = new CanvasVisualRenderer(animatedTextureService);
        this.sceneBackgroundRenderer = new SceneBackgroundRenderer(assetRegistry, thumbnailRegistry);
        this.sceneWallRenderer = new SceneWallRenderer();
        this.sceneDoorRenderer = new SceneDoorRenderer();
        this.sceneFogRenderer = new SceneFogRenderer();
        this.sceneVisionDebugRenderer = new SceneVisionDebugRenderer();
        this.sceneVisionMaskRenderer = new SceneVisionMaskRenderer();
    }

    public void render(
            VRenderContext context,
            VttScene tabletopScene,
            CanvasScene scene,
            SelectionManager selectionManager,
            boolean masterView,
            boolean renderVisionMask,
            boolean editorSelectionVisible,
            boolean resizeHandlesVisible,
            boolean attachmentMarkersVisible,
            boolean visionDebugVisible,
            String visionOwnerId,
            Collection<AuthoritativeVisionRegion> authoritativeVisionRegions,
            boolean maskWhenAuthoritativeVisionEmpty,
            Collection<String> authoritativeVisibleObjectIds
    ) {
        var grid = tabletopScene == null ? null : tabletopScene.getGrid();
        if (grid != null && !grid.isTopLayer()) gridRenderer.render(context, grid);
        sceneBackgroundRenderer.render(context, tabletopScene);
        renderObjects(context, tabletopScene, scene, selectionManager, editorSelectionVisible,
                resizeHandlesVisible, attachmentMarkersVisible,
                masterView, authoritativeVisibleObjectIds);
        sceneWallRenderer.render(context, tabletopScene, masterView);
        sceneDoorRenderer.render(context, tabletopScene, masterView);
        if (grid != null && grid.isTopLayer()) gridRenderer.render(context, grid);
        if (masterView && visionDebugVisible) {
            sceneVisionDebugRenderer.render(context, tabletopScene, scene, selectionManager);
        }
        if (renderVisionMask) {
            sceneVisionMaskRenderer.render(
                    context, tabletopScene, scene, selectionManager, visionOwnerId,
                    authoritativeVisionRegions, maskWhenAuthoritativeVisionEmpty);
        }
        sceneFogRenderer.render(context, tabletopScene, masterView);
    }

    private void renderObjects(
            VRenderContext context,
            VttScene tabletopScene,
            CanvasScene scene,
            SelectionManager selectionManager,
            boolean editorSelectionVisible,
            boolean resizeHandlesVisible,
            boolean attachmentMarkersVisible,
            boolean masterView,
            Collection<String> authoritativeVisibleObjectIds
    ) {
        Set<String> visibleIds = authoritativeVisibleObjectIds == null
                ? null : new HashSet<>(authoritativeVisibleObjectIds);
        Map<String, Integer> tintColors = new HashMap<>();
        if (tabletopScene != null) tabletopScene.getObjects().forEach(object -> {
            if (object != null && object.getId() != null && object.getState() != null) {
                tintColors.put(object.getId(), object.getState().getTintColorRgb());
            }
        });
        for (CanvasObject object : scene.getObjects()) {
            if (attachmentMarkersVisible
                    && AttachmentVisibilityResolver.isInactiveForParentState(
                    tabletopScene, scene, object)) continue;
            boolean effectivelyVisible = AttachmentVisibilityResolver.isObjectEffectivelyVisible(
                    tabletopScene, scene, object);
            if (!effectivelyVisible && !masterView) continue;
            if (visibleIds != null && !visibleIds.contains(object.id())) continue;

            renderObject(context, object, effectivelyVisible ? 1.0F : 0.5F,
                    tintColors.getOrDefault(object.id(), 0xFFFFFF));

            if (attachmentMarkersVisible && object.hasSourceAttachmentDefinition()) {
                boolean stateSpecific = tabletopScene != null
                        && tabletopScene.getObjects().stream()
                        .filter(metadata -> metadata != null
                                && object.id().equals(metadata.getId()))
                        .map(metadata -> metadata.getAttachmentBinding() != null
                                && metadata.getAttachmentBinding().getParentStateId() != null)
                        .findFirst().orElse(false);
                renderAttachmentMarker(context, object, stateSpecific);
            }

            if (editorSelectionVisible && selectionManager.isSelected(object.id())) {
                renderSelectionBorder(context, object);
                if (resizeHandlesVisible) renderSelectionHandles(context, object);
                renderRotationHandle(context, object);
            }
        }
    }

    private void renderAttachmentMarker(
            VRenderContext context, CanvasObject object, boolean stateSpecific
    ) {
        Vec2d center = context.renderState().worldToScreen(object.transform().position());
        PoseStack pose = context.graphics().pose();
        pose.pushPose();
        pose.translate(center.x(), center.y(), 0.0);
        pose.mulPose(Axis.ZP.rotationDegrees((float) object.transform().rotationDegrees()));
        int half = 6;
        int fill = stateSpecific ? 0xDDB455FF : 0xDD278CFF;
        int outline = stateSpecific ? 0xFFFF88FF : 0xFF66CCFF;
        context.graphics().fill(-half, -half, half, half, fill);
        context.graphics().hLine(-half, half, -half, outline);
        context.graphics().hLine(-half, half, half, outline);
        context.graphics().vLine(-half, -half, half, outline);
        context.graphics().vLine(half, -half, half, outline);
        pose.popPose();
    }

    private void renderObject(
            VRenderContext context, CanvasObject object, float opacity, int tintColorRgb
    ) {
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
                localBottom,
                opacity,
                tintColorRgb
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

    /** Highlights a token while an attachment is being dropped onto it. */
    public void renderAttachmentDropTarget(VRenderContext context, CanvasObject target) {
        if (target != null) renderSelectionBorder(context, target);
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
