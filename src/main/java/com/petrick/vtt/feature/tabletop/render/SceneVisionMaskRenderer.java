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
    private static final int ALPHA_QUANTIZATION = 4;
    private static final double DEFAULT_INNER_RADIUS = 256.0;
    private static final double DEFAULT_OUTER_RADIUS = 512.0;

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
            if (shouldRenderNoVisionMask(tabletopScene, selectionManager, visionOwnerId)) {
                context.graphics().fill(0, 0, context.screenWidth(), context.screenHeight(), MASK_COLOR);
            }
            return;
        }
        var segments = geometry.build(tabletopScene);
        List<VisionRegion> regions = new ArrayList<>();
        for (CanvasObject source : sources) {
            VisionRadii radii = resolveVisionRadii(tabletopScene, source.id());
            List<Vec2d> outerWorldPolygon = raycaster.buildVisibilityPolygon(
                    source.transform().position(), radii.outerRadius(), segments);
            if (outerWorldPolygon.size() >= 3) {
                double zoom = context.renderState().getCamera().getZoom();
                regions.add(new VisionRegion(
                        outerWorldPolygon.stream().map(context.renderState()::worldToScreen).toList(),
                        context.renderState().worldToScreen(source.transform().position()),
                        radii.innerRadius() * zoom, radii.outerRadius() * zoom));
            }
        }
        if (!regions.isEmpty()) {
            List<List<Vec2d>> outerPolygons = regions.stream().map(VisionRegion::outerPolygon).toList();
            fillOutsidePolygons(context, outerPolygons);
            renderRadialGradient(context, regions, outerPolygons);
        }
    }

    private boolean shouldRenderNoVisionMask(
            VttScene scene, SelectionManager selectionManager, String ownerId
    ) {
        if (scene == null) return ownerId != null && !ownerId.isBlank();
        if (ownerId != null && !ownerId.isBlank()) {
            boolean ownsDisabledToken = scene.getObjects().stream()
                    .anyMatch(object -> object != null && ownerId.equals(object.getOwnerId())
                            && !object.isVisionEnabled());
            return !ownsDisabledToken;
        }
        if (selectionManager == null || selectionManager.getSelectedObjectIds().size() != 1) return false;
        String selectedId = selectionManager.getSelectedObjectIds().iterator().next();
        return scene.getObjects().stream()
                .filter(object -> object != null && selectedId.equals(object.getId()))
                .findFirst().map(object -> object.isVisionEnabled()).orElse(false);
    }

    private VisionRadii resolveVisionRadii(VttScene scene, String objectId) {
        return scene.getObjects().stream()
                .filter(object -> object != null && objectId.equals(object.getId()))
                .map(object -> {
                    double outer = object.getVisionOuterRadius() > 0.0
                            ? object.getVisionOuterRadius() : DEFAULT_OUTER_RADIUS;
                    double inner = Math.max(0.0, object.getVisionInnerRadius());
                    return new VisionRadii(Math.min(inner, outer), outer);
                })
                .findFirst()
                .orElse(new VisionRadii(DEFAULT_INNER_RADIUS, DEFAULT_OUTER_RADIUS));
    }

    private void renderRadialGradient(
            VRenderContext context, List<VisionRegion> regions, List<List<Vec2d>> outerPolygons
    ) {
        for (int left = 0; left < context.screenWidth(); left += COLUMN_WIDTH) {
            int right = Math.min(context.screenWidth(), left + COLUMN_WIDTH);
            double sampleX = left + (right - left) / 2.0;
            List<VisibleInterval> outer = visibleIntervalsAtX(outerPolygons, sampleX, context.screenHeight());
            for (VisibleInterval outerInterval : outer) {
                renderGradientInterval(context, left, right, sampleX, outerInterval, regions);
            }
        }
    }

    private void renderGradientInterval(
            VRenderContext context, int left, int right, double x,
            VisibleInterval interval, List<VisionRegion> regions
    ) {
        int top = Math.max(0, (int) Math.floor(interval.start()));
        int bottom = Math.min(context.screenHeight(), (int) Math.ceil(interval.end()));
        if (bottom <= top) return;

        int runStart = top;
        int runAlpha = quantizeAlpha(gradientAlpha(x, top + 0.5, regions));
        for (int y = top + 1; y < bottom; y++) {
            int alpha = quantizeAlpha(gradientAlpha(x, y + 0.5, regions));
            if (alpha == runAlpha) continue;
            fillAlphaRun(context, left, right, runStart, y, runAlpha);
            runStart = y;
            runAlpha = alpha;
        }
        fillAlphaRun(context, left, right, runStart, bottom, runAlpha);
    }

    private int quantizeAlpha(int alpha) {
        if (alpha <= 0) return 0;
        if (alpha >= 252) return 255;
        return Math.min(255, ((alpha + ALPHA_QUANTIZATION / 2) / ALPHA_QUANTIZATION)
                * ALPHA_QUANTIZATION);
    }

    private void fillAlphaRun(
            VRenderContext context, int left, int right, int top, int bottom, int alpha
    ) {
        if (alpha > 0 && bottom > top) {
            context.graphics().fill(left, top, right, bottom, maskColor(alpha));
        }
    }

    private int gradientAlpha(double x, double y, List<VisionRegion> regions) {
        double darkness = 1.0;
        for (VisionRegion region : regions) {
            double distance = Math.hypot(x - region.origin().x(), y - region.origin().y());
            if (distance > region.outerRadiusPixels()) continue;
            double span = Math.max(1.0, region.outerRadiusPixels() - region.innerRadiusPixels());
            darkness = Math.min(darkness,
                    Math.max(0.0, Math.min(1.0, (distance - region.innerRadiusPixels()) / span)));
        }
        return (int) Math.round(darkness * 255.0);
    }

    private int maskColor(int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | 0x0008080C;
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
    private record VisionRadii(double innerRadius, double outerRadius) {}
    private record VisionRegion(
            List<Vec2d> outerPolygon, Vec2d origin,
            double innerRadiusPixels, double outerRadiusPixels) {}
}
