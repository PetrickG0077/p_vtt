package com.petrick.vtt.feature.tabletop.render;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.VttFogArea;
import com.petrick.vtt.feature.tabletop.VttFogOfWar;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.platform.render.VRenderContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Renders rectangular fog operations as the final scene overlay. */
public final class SceneFogRenderer {
    private static final int FOG_COLOR = 0xEE08080C;

    public void render(VRenderContext context, VttScene scene) {
        if (scene == null) return;
        VttFogOfWar fog = scene.getFogOfWar();
        if (!fog.isEnabled()) return;

        if (fog.isDefaultHidden()) {
            renderDefaultHiddenFog(context, fog.getRevealedAreas());
        }

        for (VttFogArea hiddenArea : fog.getHiddenAreas()) {
            if (hiddenArea == null || !hiddenArea.isVisible()) continue;
            ScreenRect rect = screenBounds(context, hiddenArea);
            if (rect != null) fillRect(context, rect);
        }
    }

    private void renderDefaultHiddenFog(VRenderContext context, List<VttFogArea> revealedAreas) {
        List<ScreenRect> reveals = new ArrayList<>();
        for (VttFogArea area : revealedAreas) {
            if (area == null || !area.isVisible()) continue;
            ScreenRect rect = screenBounds(context, area);
            if (rect != null) reveals.add(rect);
        }

        if (reveals.isEmpty()) {
            context.graphics().fill(0, 0, context.screenWidth(), context.screenHeight(), FOG_COLOR);
            return;
        }

        List<Integer> yCoordinates = new ArrayList<>();
        yCoordinates.add(0);
        yCoordinates.add(context.screenHeight());
        for (ScreenRect reveal : reveals) {
            yCoordinates.add(reveal.top());
            yCoordinates.add(reveal.bottom());
        }
        yCoordinates = yCoordinates.stream().distinct().sorted().toList();

        for (int index = 0; index < yCoordinates.size() - 1; index++) {
            int top = yCoordinates.get(index);
            int bottom = yCoordinates.get(index + 1);
            if (bottom <= top) continue;

            List<Interval> openIntervals = new ArrayList<>();
            for (ScreenRect reveal : reveals) {
                if (reveal.top() < bottom && reveal.bottom() > top) {
                    openIntervals.add(new Interval(reveal.left(), reveal.right()));
                }
            }
            openIntervals.sort(Comparator.comparingInt(Interval::start));
            List<Interval> merged = mergeIntervals(openIntervals);

            int cursor = 0;
            for (Interval interval : merged) {
                if (interval.start() > cursor) {
                    context.graphics().fill(cursor, top, interval.start(), bottom, FOG_COLOR);
                }
                cursor = Math.max(cursor, interval.end());
            }
            if (cursor < context.screenWidth()) {
                context.graphics().fill(cursor, top, context.screenWidth(), bottom, FOG_COLOR);
            }
        }
    }

    private List<Interval> mergeIntervals(List<Interval> intervals) {
        List<Interval> merged = new ArrayList<>();
        for (Interval interval : intervals) {
            if (merged.isEmpty() || interval.start() > merged.get(merged.size() - 1).end()) {
                merged.add(interval);
            } else {
                Interval previous = merged.remove(merged.size() - 1);
                merged.add(new Interval(previous.start(), Math.max(previous.end(), interval.end())));
            }
        }
        return merged;
    }

    private ScreenRect screenBounds(VRenderContext context, VttFogArea area) {
        var transform = area.getTransform();
        double halfWidth = Math.abs(area.getSize().getWidth() * transform.getScaleX()) / 2.0;
        double halfHeight = Math.abs(area.getSize().getHeight() * transform.getScaleY()) / 2.0;
        double radians = Math.toRadians(transform.getRotationDegrees());
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        double[][] corners = {
                {-halfWidth, -halfHeight}, {halfWidth, -halfHeight},
                {halfWidth, halfHeight}, {-halfWidth, halfHeight}
        };
        for (double[] corner : corners) {
            double worldX = transform.getX() + corner[0] * cos - corner[1] * sin;
            double worldY = transform.getY() + corner[0] * sin + corner[1] * cos;
            Vec2d screen = context.renderState().worldToScreen(new Vec2d(worldX, worldY));
            minX = Math.min(minX, screen.x());
            minY = Math.min(minY, screen.y());
            maxX = Math.max(maxX, screen.x());
            maxY = Math.max(maxY, screen.y());
        }

        int left = Math.max(0, (int) Math.floor(minX));
        int top = Math.max(0, (int) Math.floor(minY));
        int right = Math.min(context.screenWidth(), (int) Math.ceil(maxX));
        int bottom = Math.min(context.screenHeight(), (int) Math.ceil(maxY));
        if (right <= left || bottom <= top) return null;
        return new ScreenRect(left, top, right, bottom);
    }

    private void fillRect(VRenderContext context, ScreenRect rect) {
        context.graphics().fill(rect.left(), rect.top(), rect.right(), rect.bottom(), FOG_COLOR);
    }

    private record ScreenRect(int left, int top, int right, int bottom) {}
    private record Interval(int start, int end) {}
}
