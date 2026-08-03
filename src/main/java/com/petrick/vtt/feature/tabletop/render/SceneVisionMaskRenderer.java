package com.petrick.vtt.feature.tabletop.render;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.selection.SelectionManager;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttLight;
import com.petrick.vtt.feature.tabletop.VttLightType;
import com.petrick.vtt.feature.tabletop.vision.AuthoritativeVisionRegion;
import com.petrick.vtt.feature.tabletop.vision.SceneVisionGeometry;
import com.petrick.vtt.feature.tabletop.vision.SceneVisionRaycaster;
import com.petrick.vtt.feature.tabletop.vision.SceneVisionSourceResolver;
import com.petrick.vtt.platform.render.VRenderContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Draws the player-only darkness outside the selected token's visibility polygon. */
public final class SceneVisionMaskRenderer {
    private static final int ALPHA_QUANTIZATION = 4;
    private static final double DEFAULT_INNER_RADIUS = 256.0;
    private static final double DEFAULT_OUTER_RADIUS = 512.0;

    private final SceneVisionGeometry geometry = new SceneVisionGeometry();
    private final SceneVisionRaycaster raycaster = new SceneVisionRaycaster();
    private final SceneVisionSourceResolver sourceResolver = new SceneVisionSourceResolver();

    public void render(
            VRenderContext context, VttScene tabletopScene,
            CanvasScene canvasScene, SelectionManager selectionManager,
            String visionOwnerId, Collection<AuthoritativeVisionRegion> authoritativeRegions,
            boolean maskWhenAuthoritativeSourcesEmpty
    ) {
        if (tabletopScene == null) return;
        if (authoritativeRegions != null && authoritativeRegions.isEmpty()) {
            if (maskWhenAuthoritativeSourcesEmpty) {
                fillScreenMask(context, tabletopScene);
            }
            return;
        }
        var sceneSegments = geometry.build(tabletopScene);

        List<VisionRegion> regions = new ArrayList<>();
        if (authoritativeRegions != null) {
            double zoom = context.renderState().getCamera().getZoom();
            for (AuthoritativeVisionRegion region : authoritativeRegions) {
                if (region == null || region.origin() == null || region.outerPolygon().size() < 3) continue;
                regions.add(new VisionRegion(
                        region.outerPolygon().stream().map(context.renderState()::worldToScreen).toList(),
                        context.renderState().worldToScreen(region.origin()),
                        region.innerRadius() * zoom, region.outerRadius() * zoom,
                        region.ownLightEnabled()));
            }
        } else {
            List<CanvasObject> sources = sourceResolver.resolveAll(
                    tabletopScene, canvasScene, selectionManager, visionOwnerId);
            if (sources.isEmpty()) {
                boolean renderEmptyMask = shouldRenderNoVisionMask(
                        tabletopScene, selectionManager, visionOwnerId);
                if (renderEmptyMask) {
                    fillScreenMask(context, tabletopScene);
                }
                return;
            }
            for (CanvasObject source : sources) {
                VisionRadii radii = resolveVisionRadii(tabletopScene, source.id());
                double raycastDistance = visibilityReach(
                        tabletopScene, source.transform().position(), radii.outerRadius());
                List<Vec2d> outerWorldPolygon = raycaster.buildVisibilityPolygon(
                        source.transform().position(), raycastDistance, sceneSegments,
                        tabletopScene.getLighting().getVisionRayCount());
                if (outerWorldPolygon.size() < 3) continue;
                double zoom = context.renderState().getCamera().getZoom();
                regions.add(new VisionRegion(
                        outerWorldPolygon.stream().map(context.renderState()::worldToScreen).toList(),
                        context.renderState().worldToScreen(source.transform().position()),
                        radii.innerRadius() * zoom, radii.outerRadius() * zoom,
                        radii.ownLightEnabled()));
            }
        }
        if (regions.isEmpty()) {
            boolean renderEmptyMask = authoritativeRegions == null
                    ? shouldRenderNoVisionMask(tabletopScene, selectionManager, visionOwnerId)
                    : maskWhenAuthoritativeSourcesEmpty;
            if (renderEmptyMask) {
                fillScreenMask(context, tabletopScene);
            }
            return;
        }
        List<List<Vec2d>> outerPolygons = regions.stream().map(VisionRegion::outerPolygon).toList();
        int darknessRgb = tabletopScene.getLighting().getDarknessColorRgb();
        int pixelSize = tabletopScene.getLighting().getVisionPixelSize();
        fillOutsidePolygons(context, outerPolygons, darknessRgb, pixelSize);
        double zoom = context.renderState().getCamera().getZoom();
        List<ScreenLight> screenLights = tabletopScene.getLights().stream()
                .filter(light -> light != null && light.isEnabled())
                .map(light -> {
                    Vec2d worldOrigin = new Vec2d(light.getX(), light.getY());
                    List<Vec2d> worldPolygon = light.getType() == VttLightType.SPOT
                            ? raycaster.buildVisibilityCone(
                            worldOrigin, light.getOuterRadius(), sceneSegments,
                            Math.toRadians(light.getDirectionDegrees()),
                            Math.toRadians(light.getConeAngleDegrees()),
                            tabletopScene.getLighting().getVisionRayCount())
                            : raycaster.buildVisibilityPolygon(
                            worldOrigin, light.getOuterRadius(), sceneSegments,
                            tabletopScene.getLighting().getVisionRayCount());
                    List<Vec2d> visibilityPolygon = worldPolygon.stream()
                            .map(context.renderState()::worldToScreen).toList();
                    return new ScreenLight(
                            context.renderState().worldToScreen(worldOrigin),
                            light.getInnerRadius() * zoom, light.getOuterRadius() * zoom,
                            light.getColorRgb(), light.isTintEnabled(), light.getIntensity(),
                            visibilityPolygon);
                })
                .filter(light -> light.visibilityPolygon().size() >= 3)
                .toList();
        renderRadialGradient(context, regions, outerPolygons,
                screenLights, darknessRgb, pixelSize);
        renderLightTint(context, screenLights, outerPolygons, pixelSize);
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
                    return new VisionRadii(Math.min(inner, outer), outer,
                            object.isVisionOwnLightEnabled());
                })
                .findFirst()
                .orElse(new VisionRadii(DEFAULT_INNER_RADIUS, DEFAULT_OUTER_RADIUS, true));
    }

    private void renderRadialGradient(
            VRenderContext context, List<VisionRegion> regions, List<List<Vec2d>> outerPolygons,
            List<ScreenLight> lights, int darknessRgb, int pixelSize
    ) {
        for (int left = 0; left < context.screenWidth(); left += pixelSize) {
            int right = Math.min(context.screenWidth(), left + pixelSize);
            double sampleX = left + (right - left) / 2.0;
            List<VisibleInterval> outer = visibleIntervalsAtX(outerPolygons, sampleX, context.screenHeight());
            List<ColumnLight> columnLights = columnLightsAtX(
                    lights, sampleX, context.screenHeight());
            for (VisibleInterval outerInterval : outer) {
                renderGradientInterval(context, left, right, sampleX, outerInterval, regions,
                        columnLights, darknessRgb, pixelSize);
            }
        }
    }

    private void renderGradientInterval(
            VRenderContext context, int left, int right, double x,
            VisibleInterval interval, List<VisionRegion> regions, List<ColumnLight> lights,
            int darknessRgb, int pixelSize
    ) {
        int top = Math.max(0, alignDown(interval.start(), pixelSize));
        int bottom = Math.min(context.screenHeight(), alignUp(interval.end(), pixelSize));
        if (bottom <= top) return;

        int runStart = top;
        int firstBottom = Math.min(bottom, top + pixelSize);
        int runAlpha = quantizeAlpha(gradientAlpha(
                x, top + (firstBottom - top) / 2.0,
                regions, lights, interval, pixelSize));
        for (int y = top + pixelSize; y < bottom; y += pixelSize) {
            int cellBottom = Math.min(bottom, y + pixelSize);
            int alpha = quantizeAlpha(gradientAlpha(
                    x, y + (cellBottom - y) / 2.0,
                    regions, lights, interval, pixelSize));
            if (alpha == runAlpha) continue;
            fillAlphaRun(context, left, right, runStart, y, runAlpha, darknessRgb);
            runStart = y;
            runAlpha = alpha;
        }
        fillAlphaRun(context, left, right, runStart, bottom, runAlpha, darknessRgb);
    }

    private int quantizeAlpha(int alpha) {
        if (alpha <= 0) return 0;
        if (alpha >= 252) return 255;
        return Math.min(255, ((alpha + ALPHA_QUANTIZATION / 2) / ALPHA_QUANTIZATION)
                * ALPHA_QUANTIZATION);
    }

    private void fillAlphaRun(
            VRenderContext context, int left, int right, int top, int bottom, int alpha,
            int darknessRgb
    ) {
        if (alpha > 0 && bottom > top) {
            context.graphics().fill(left, top, right, bottom, maskColor(alpha, darknessRgb));
        }
    }

    private int gradientAlpha(
            double x, double y, List<VisionRegion> regions, List<ColumnLight> lights,
            VisibleInterval interval, int pixelSize
    ) {
        double darkness = 1.0;
        for (VisionRegion region : regions) {
            if (!region.ownLightEnabled()) continue;
            double distance = Math.hypot(x - region.origin().x(), y - region.origin().y());
            if (distance > region.outerRadiusPixels()) continue;
            double span = Math.max(1.0, region.outerRadiusPixels() - region.innerRadiusPixels());
            double radialDarkness = Math.max(0.0,
                    Math.min(1.0, (distance - region.innerRadiusPixels()) / span));
            darkness = Math.min(darkness, radialDarkness);
        }
        for (ColumnLight columnLight : lights) {
            if (!columnLight.containsY(y)) continue;
            ScreenLight light = columnLight.light();
            double distance = Math.hypot(x - light.origin().x(), y - light.origin().y());
            if (distance > light.outerRadiusPixels()) continue;
            double span = Math.max(1.0,
                    light.outerRadiusPixels() - light.innerRadiusPixels());
            double lightDarkness = Math.max(0.0, Math.min(1.0,
                    (distance - light.innerRadiusPixels()) / span));
            double revealStrength = (1.0 - lightDarkness) * light.intensity();
            darkness = Math.min(darkness, 1.0 - Math.min(1.0, revealStrength));
        }
        double edgeSoftness = Math.max(4.0, pixelSize * 2.0);
        double intervalEdgeDistance = Math.max(0.0,
                Math.min(y - interval.start(), interval.end() - y));
        double edgeDarkness = Math.max(0.0,
                1.0 - intervalEdgeDistance / edgeSoftness);
        darkness = Math.max(darkness, edgeDarkness);
        return (int) Math.round(darkness * 255.0);
    }

    private double visibilityReach(VttScene scene, Vec2d origin, double baseRadius) {
        double reach = baseRadius;
        for (VttLight light : scene.getLights()) {
            if (light == null || !light.isEnabled()) continue;
            reach = Math.max(reach, Math.hypot(
                    light.getX() - origin.x(), light.getY() - origin.y())
                    + light.getOuterRadius());
        }
        return reach;
    }

    private void renderLightTint(
            VRenderContext context, List<ScreenLight> lights,
            List<List<Vec2d>> visiblePolygons, int pixelSize
    ) {
        if (lights.isEmpty()) return;
        for (int left = 0; left < context.screenWidth(); left += pixelSize) {
            int right = Math.min(context.screenWidth(), left + pixelSize);
            double x = left + (right - left) / 2.0;
            List<ColumnLight> columnLights = columnLightsAtX(
                    lights, x, context.screenHeight());
            for (VisibleInterval interval : visibleIntervalsAtX(
                    visiblePolygons, x, context.screenHeight())) {
                int top = Math.max(0, alignDown(interval.start(), pixelSize));
                int bottom = Math.min(context.screenHeight(), alignUp(interval.end(), pixelSize));
                for (int y = top; y < bottom; y += pixelSize) {
                    int cellBottom = Math.min(bottom, y + pixelSize);
                    double sampleY = y + (cellBottom - y) / 2.0;
                    double combinedStrength = 0.0;
                    double totalWeight = 0.0;
                    double red = 0.0;
                    double green = 0.0;
                    double blue = 0.0;
                    for (ColumnLight columnLight : columnLights) {
                        if (!columnLight.containsY(sampleY)) continue;
                        ScreenLight light = columnLight.light();
                        if (!light.tintEnabled()) continue;
                        double distance = Math.hypot(
                                x - light.origin().x(), sampleY - light.origin().y());
                        if (distance > light.outerRadiusPixels()) continue;
                        double candidate = distance <= light.innerRadiusPixels() ? 1.0
                                : 1.0 - (distance - light.innerRadiusPixels())
                                / Math.max(1.0, light.outerRadiusPixels()
                                - light.innerRadiusPixels());
                        candidate = Math.max(0.0,
                                Math.min(1.0, candidate * light.intensity()));
                        if (candidate <= 0.0) continue;
                        combinedStrength = 1.0
                                - (1.0 - combinedStrength) * (1.0 - candidate);
                        totalWeight += candidate;
                        red += ((light.colorRgb() >> 16) & 0xFF) * candidate;
                        green += ((light.colorRgb() >> 8) & 0xFF) * candidate;
                        blue += (light.colorRgb() & 0xFF) * candidate;
                    }
                    if (totalWeight > 0.0 && combinedStrength > 0.0) {
                        int mixedRed = (int) Math.round(red / totalWeight);
                        int mixedGreen = (int) Math.round(green / totalWeight);
                        int mixedBlue = (int) Math.round(blue / totalWeight);
                        int mixedRgb = (Math.max(0, Math.min(255, mixedRed)) << 16)
                                | (Math.max(0, Math.min(255, mixedGreen)) << 8)
                                | Math.max(0, Math.min(255, mixedBlue));
                        int alpha = (int) Math.round(104.0 * combinedStrength);
                        context.graphics().fill(left, y, right, cellBottom,
                                (alpha << 24) | mixedRgb);
                    }
                }
            }
        }
    }

    private int maskColor(int alpha, int darknessRgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24)
                | (darknessRgb & 0x00FFFFFF);
    }

    private void fillOutsidePolygons(
            VRenderContext context, List<List<Vec2d>> polygons, int darknessRgb,
            int pixelSize
    ) {
        int opaqueMask = maskColor(255, darknessRgb);
        for (int left = 0; left < context.screenWidth(); left += pixelSize) {
            int right = Math.min(context.screenWidth(), left + pixelSize);
            double sampleX = left + (right - left) / 2.0;
            List<VisibleInterval> visibleIntervals = visibleIntervalsAtX(
                    polygons, sampleX, context.screenHeight());
            int cursor = 0;
            for (VisibleInterval interval : visibleIntervals) {
                double insideStart = interval.start();
                double insideEnd = interval.end();
                int opaqueEnd = Math.max(cursor, alignDown(insideStart, pixelSize));
                if (opaqueEnd > cursor) context.graphics().fill(
                        left, cursor, right, opaqueEnd, opaqueMask);
                cursor = Math.max(cursor, alignUp(insideEnd, pixelSize));
            }
            if (cursor < context.screenHeight()) {
                context.graphics().fill(
                        left, cursor, right, context.screenHeight(), opaqueMask);
            }
        }
    }

    private int alignDown(double value, int pixelSize) {
        return (int) Math.floor(value / pixelSize) * pixelSize;
    }

    private int alignUp(double value, int pixelSize) {
        return (int) Math.ceil(value / pixelSize) * pixelSize;
    }

    private void fillScreenMask(VRenderContext context, VttScene scene) {
        context.graphics().fill(0, 0, context.screenWidth(), context.screenHeight(),
                maskColor(255, scene.getLighting().getDarknessColorRgb()));
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

    private List<ColumnLight> columnLightsAtX(
            List<ScreenLight> lights, double x, int screenHeight
    ) {
        List<ColumnLight> result = new ArrayList<>();
        for (ScreenLight light : lights) {
            if (Math.abs(x - light.origin().x()) > light.outerRadiusPixels()) continue;
            List<VisibleInterval> intervals = visibleIntervalsAtX(
                    List.of(light.visibilityPolygon()), x, screenHeight);
            if (!intervals.isEmpty()) result.add(new ColumnLight(light, intervals));
        }
        return result;
    }

    private record VisibleInterval(double start, double end) {}
    private record VisionRadii(
            double innerRadius, double outerRadius, boolean ownLightEnabled) {}
    private record VisionRegion(
            List<Vec2d> outerPolygon, Vec2d origin,
            double innerRadiusPixels, double outerRadiusPixels,
            boolean ownLightEnabled) {}
    private record ScreenLight(
            Vec2d origin, double innerRadiusPixels, double outerRadiusPixels,
            int colorRgb, boolean tintEnabled, double intensity,
            List<Vec2d> visibilityPolygon) {}
    private record ColumnLight(ScreenLight light, List<VisibleInterval> intervals) {
        private boolean containsY(double y) {
            for (VisibleInterval interval : intervals) {
                if (y >= interval.start() && y <= interval.end()) return true;
            }
            return false;
        }
    }
}
