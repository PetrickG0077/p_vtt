package com.petrick.vtt.feature.tabletop.render;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.selection.SelectionManager;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.vision.SceneVisionGeometry;
import com.petrick.vtt.feature.tabletop.vision.SceneVisionRaycaster;
import com.petrick.vtt.feature.tabletop.vision.SceneVisionSourceResolver;
import com.petrick.vtt.platform.render.VRenderContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Draws the player-only darkness outside the selected token's visibility polygon. */
public final class SceneVisionMaskRenderer {
    private static final int MASK_COLOR = 0xFF08080C;
    private static final int COLUMN_WIDTH = 1;
    private static final int FEATHER_PIXELS = 4;
    private static final int FEATHER_MAX_ALPHA = 208;

    private final SceneVisionGeometry geometry = new SceneVisionGeometry();
    private final SceneVisionRaycaster raycaster = new SceneVisionRaycaster();
    private final SceneVisionSourceResolver sourceResolver = new SceneVisionSourceResolver();

    public void render(
            VRenderContext context, VttScene tabletopScene,
            CanvasScene canvasScene, SelectionManager selectionManager
    ) {
        CanvasObject source = sourceResolver.resolve(tabletopScene, canvasScene, selectionManager);
        if (source == null || tabletopScene == null) return;
        List<Vec2d> worldPolygon = raycaster.buildVisibilityPolygon(
                source.transform().position(), resolveVisionRange(context, tabletopScene, source.id()),
                geometry.build(tabletopScene));
        if (worldPolygon.size() < 3) return;
        List<Vec2d> screenPolygon = worldPolygon.stream()
                .map(context.renderState()::worldToScreen)
                .toList();
        fillOutsidePolygon(context, screenPolygon);
    }

    private double maximumDistance(VRenderContext context) {
        return Math.hypot(context.screenWidth(), context.screenHeight())
                / Math.max(0.0001, context.renderState().getCamera().getZoom()) * 1.5;
    }

    private double resolveVisionRange(VRenderContext context, VttScene scene, String objectId) {
        return scene.getObjects().stream()
                .filter(object -> object != null && objectId.equals(object.getId()))
                .mapToDouble(object -> object.getVisionRange())
                .filter(range -> range > 0.0)
                .findFirst()
                .orElseGet(() -> maximumDistance(context));
    }

    private void fillOutsidePolygon(VRenderContext context, List<Vec2d> polygon) {
        for (int left = 0; left < context.screenWidth(); left += COLUMN_WIDTH) {
            int right = Math.min(context.screenWidth(), left + COLUMN_WIDTH);
            double sampleX = left + (right - left) / 2.0;
            List<Double> intersections = intersectionsAtX(polygon, sampleX);
            int cursor = 0;
            for (int index = 0; index + 1 < intersections.size(); index += 2) {
                double insideStart = clampToScreen(intersections.get(index), context.screenHeight());
                double insideEnd = clampToScreen(intersections.get(index + 1), context.screenHeight());
                int opaqueEnd = Math.max(cursor, (int) Math.floor(insideStart));
                if (opaqueEnd > cursor) context.graphics().fill(left, cursor, right, opaqueEnd, MASK_COLOR);
                renderFeather(context, left, right, insideStart, insideEnd);
                cursor = Math.max(cursor, (int) Math.ceil(insideEnd));
            }
            if (cursor < context.screenHeight()) {
                context.graphics().fill(left, cursor, right, context.screenHeight(), MASK_COLOR);
            }
        }
    }

    private void renderFeather(
            VRenderContext context, int left, int right, double insideStart, double insideEnd
    ) {
        int topStart = Math.max(0, (int) Math.floor(insideStart));
        int topEnd = Math.min(context.screenHeight(),
                Math.min((int) Math.ceil(insideEnd), (int) Math.ceil(insideStart + FEATHER_PIXELS)));
        if (topEnd > topStart) {
            context.graphics().fillGradient(left, topStart, right, topEnd,
                    featherColor(FEATHER_MAX_ALPHA), featherColor(0));
        }

        int bottomStart = Math.max(0, (int) Math.floor(insideEnd - FEATHER_PIXELS));
        int bottomEnd = Math.min(context.screenHeight(), (int) Math.ceil(insideEnd));
        bottomStart = Math.max(bottomStart, (int) Math.floor(insideStart));
        if (bottomEnd > bottomStart) {
            context.graphics().fillGradient(left, bottomStart, right, bottomEnd,
                    featherColor(0), featherColor(FEATHER_MAX_ALPHA));
        }
    }

    private int featherColor(int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | 0x0008080C;
    }

    private List<Double> intersectionsAtX(List<Vec2d> polygon, double x) {
        List<Double> intersections = new ArrayList<>();
        for (int index = 0; index < polygon.size(); index++) {
            Vec2d first = polygon.get(index);
            Vec2d second = polygon.get((index + 1) % polygon.size());
            if ((first.x() <= x && second.x() > x) || (second.x() <= x && first.x() > x)) {
                double progress = (x - first.x()) / (second.x() - first.x());
                intersections.add(first.y() + progress * (second.y() - first.y()));
            }
        }
        intersections.sort(Comparator.naturalOrder());
        return intersections;
    }

    private double clampToScreen(double value, int screenHeight) {
        return Math.max(0.0, Math.min(screenHeight, value));
    }
}
