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
    private static final int COLUMN_WIDTH = 2;

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
                source.transform().position(), maximumDistance(context), geometry.build(tabletopScene));
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

    private void fillOutsidePolygon(VRenderContext context, List<Vec2d> polygon) {
        for (int left = 0; left < context.screenWidth(); left += COLUMN_WIDTH) {
            int right = Math.min(context.screenWidth(), left + COLUMN_WIDTH);
            double sampleX = left + (right - left) / 2.0;
            List<Double> intersections = intersectionsAtX(polygon, sampleX);
            int cursor = 0;
            for (int index = 0; index + 1 < intersections.size(); index += 2) {
                int insideStart = clampToScreen(intersections.get(index), context.screenHeight());
                int insideEnd = clampToScreen(intersections.get(index + 1), context.screenHeight());
                if (insideStart > cursor) context.graphics().fill(left, cursor, right, insideStart, MASK_COLOR);
                cursor = Math.max(cursor, insideEnd);
            }
            if (cursor < context.screenHeight()) {
                context.graphics().fill(left, cursor, right, context.screenHeight(), MASK_COLOR);
            }
        }
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

    private int clampToScreen(double value, int screenHeight) {
        return Math.max(0, Math.min(screenHeight, (int) Math.round(value)));
    }
}
