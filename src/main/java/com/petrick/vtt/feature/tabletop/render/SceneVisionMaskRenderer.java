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
            CanvasScene canvasScene, SelectionManager selectionManager,
            String visionOwnerId
    ) {
        if (tabletopScene == null) return;
        List<CanvasObject> sources = sourceResolver.resolveAll(
                tabletopScene, canvasScene, selectionManager, visionOwnerId);
        if (sources.isEmpty()) {
            if (visionOwnerId != null && !visionOwnerId.isBlank()) {
                context.graphics().fill(0, 0, context.screenWidth(), context.screenHeight(), MASK_COLOR);
            }
            return;
        }
        var segments = geometry.build(tabletopScene);
        List<List<Vec2d>> screenPolygons = new ArrayList<>();
        for (CanvasObject source : sources) {
            List<Vec2d> worldPolygon = raycaster.buildVisibilityPolygon(
                    source.transform().position(), resolveVisionRange(context, tabletopScene, source.id()), segments);
            if (worldPolygon.size() >= 3) {
                screenPolygons.add(worldPolygon.stream().map(context.renderState()::worldToScreen).toList());
            }
        }
        if (!screenPolygons.isEmpty()) fillOutsidePolygons(context, screenPolygons);
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

    private void fillOutsidePolygons(VRenderContext context, List<List<Vec2d>> polygons) {
        for (int left = 0; left < context.screenWidth(); left += COLUMN_WIDTH) {
            int right = Math.min(context.screenWidth(), left + COLUMN_WIDTH);
            double sampleX = left + (right - left) / 2.0;
            List<VisibleInterval> visibleIntervals = visibleIntervalsAtX(
                    polygons, sampleX, context.screenHeight());
            int cursor = 0;
            for (VisibleInterval interval : visibleIntervals) {
                double insideStart = interval.start();
                double insideEnd = interval.end();
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

    private List<VisibleInterval> visibleIntervalsAtX(
            List<List<Vec2d>> polygons, double x, int screenHeight
    ) {
        List<VisibleInterval> intervals = new ArrayList<>();
        for (List<Vec2d> polygon : polygons) {
            List<Double> intersections = intersectionsAtX(polygon, x);
            for (int index = 0; index + 1 < intersections.size(); index += 2) {
                double start = clampToScreen(intersections.get(index), screenHeight);
                double end = clampToScreen(intersections.get(index + 1), screenHeight);
                if (end > start) intervals.add(new VisibleInterval(start, end));
            }
        }
        intervals.sort(Comparator.comparingDouble(VisibleInterval::start));
        List<VisibleInterval> merged = new ArrayList<>();
        for (VisibleInterval interval : intervals) {
            if (merged.isEmpty() || interval.start() > merged.getLast().end()) {
                merged.add(interval);
            } else {
                VisibleInterval previous = merged.removeLast();
                merged.add(new VisibleInterval(previous.start(), Math.max(previous.end(), interval.end())));
            }
        }
        return merged;
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

    private record VisibleInterval(double start, double end) {}
}
