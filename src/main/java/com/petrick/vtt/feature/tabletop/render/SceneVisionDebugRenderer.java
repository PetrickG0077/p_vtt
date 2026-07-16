package com.petrick.vtt.feature.tabletop.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.selection.SelectionManager;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.vision.SceneVisionGeometry;
import com.petrick.vtt.feature.tabletop.vision.SceneVisionRaycaster;
import com.petrick.vtt.feature.tabletop.vision.SceneVisionSourceResolver;
import com.petrick.vtt.feature.tabletop.vision.VisionSegment;
import com.petrick.vtt.platform.render.VRenderContext;

import java.util.List;

/** Temporary master-only visualization used to validate scene vision raycasts. */
public final class SceneVisionDebugRenderer {
    private static final double DEFAULT_INNER_RADIUS = 256.0;
    private static final double DEFAULT_OUTER_RADIUS = 512.0;
    private static final int POLYGON_COLOR = 0xFF44FFFF;
    private static final int INNER_RADIUS_COLOR = 0xFFFFFF44;
    private static final int RAY_COLOR = 0x5544FFFF;
    private final SceneVisionGeometry geometry = new SceneVisionGeometry();
    private final SceneVisionRaycaster raycaster = new SceneVisionRaycaster();
    private final SceneVisionSourceResolver sourceResolver = new SceneVisionSourceResolver();

    public void render(
            VRenderContext context, VttScene tabletopScene,
            CanvasScene canvasScene, SelectionManager selectionManager
    ) {
        if (tabletopScene == null) return;
        List<CanvasObject> sources = sourceResolver.resolveAll(tabletopScene, canvasScene, selectionManager);
        if (sources.isEmpty()) return;
        var segments = geometry.build(tabletopScene);
        if (segments.isEmpty()) return;
        for (CanvasObject source : sources) {
            renderSource(context, tabletopScene, source, segments);
        }
    }

    private void renderSource(
            VRenderContext context, VttScene tabletopScene,
            CanvasObject source, List<VisionSegment> segments
    ) {
        Vec2d origin = source.transform().position();
        var sceneObject = tabletopScene.getObjects().stream()
                .filter(object -> object != null && source.id().equals(object.getId()))
                .findFirst()
                .orElse(null);
        double outerRadius = sceneObject != null && sceneObject.getVisionOuterRadius() > 0.0
                ? sceneObject.getVisionOuterRadius() : DEFAULT_OUTER_RADIUS;
        double innerRadius = sceneObject != null
                ? Math.min(sceneObject.getVisionInnerRadius(), outerRadius)
                : DEFAULT_INNER_RADIUS;
        List<Vec2d> polygon = raycaster.buildVisibilityPolygon(origin, outerRadius, segments);
        if (polygon.size() < 2) return;
        for (int index = 0; index < polygon.size(); index++) {
            Vec2d point = polygon.get(index);
            renderWorldLine(context, point, polygon.get((index + 1) % polygon.size()), POLYGON_COLOR);
            renderWorldLine(context, origin, point, RAY_COLOR);
        }
        if (innerRadius > 0.0) {
            renderPolygonBoundary(context,
                    raycaster.buildVisibilityPolygon(origin, innerRadius, segments), INNER_RADIUS_COLOR);
        }
        Vec2d screenOrigin = context.renderState().worldToScreen(origin);
        int x = (int) Math.round(screenOrigin.x());
        int y = (int) Math.round(screenOrigin.y());
        context.graphics().fill(x - 3, y - 3, x + 4, y + 4, 0xFFFFFF44);
    }

    private void renderPolygonBoundary(VRenderContext context, List<Vec2d> polygon, int color) {
        if (polygon.size() < 2) return;
        for (int index = 0; index < polygon.size(); index++) {
            renderWorldLine(context, polygon.get(index),
                    polygon.get((index + 1) % polygon.size()), color);
        }
    }

    private void renderWorldLine(VRenderContext context, Vec2d worldStart, Vec2d worldEnd, int color) {
        Vec2d a = context.renderState().worldToScreen(worldStart);
        Vec2d b = context.renderState().worldToScreen(worldEnd);
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0) return;
        PoseStack pose = context.graphics().pose();
        pose.pushPose();
        pose.translate(a.x(), a.y(), 0.0);
        pose.mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        context.graphics().fill(0, 0, (int) Math.ceil(length), 1, color);
        pose.popPose();
    }
}
