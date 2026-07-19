package com.petrick.vtt.feature.tabletop.vision;

import com.petrick.vtt.feature.tabletop.VttScene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cached uniform-grid index for wall and closed-door vision segments. */
public final class SceneVisionGeometrySpatialIndex {
    public static final double CELL_SIZE = 256.0;
    private static final long MAX_CELLS_PER_SEGMENT = 4_096L;

    private final SceneVisionGeometry geometry = new SceneVisionGeometry();
    private final Map<CellKey, LinkedHashSet<Integer>> cells = new HashMap<>();
    private final Set<Integer> globalSegments = new LinkedHashSet<>();
    private List<VisionSegment> segments = List.of();
    private List<SegmentBounds> bounds = List.of();
    private String sceneId;

    public void rebuild(VttScene scene) {
        cells.clear();
        globalSegments.clear();
        sceneId = scene == null ? null : scene.getId();
        segments = geometry.build(scene);
        List<SegmentBounds> rebuiltBounds = new ArrayList<>(segments.size());
        for (int index = 0; index < segments.size(); index++) {
            SegmentBounds segmentBounds = boundsOf(segments.get(index));
            rebuiltBounds.add(segmentBounds);
            indexSegment(index, segmentBounds);
        }
        bounds = List.copyOf(rebuiltBounds);
    }

    public List<VisionSegment> query(double minX, double minY, double maxX, double maxY) {
        if (!finite(minX, minY, maxX, maxY)) return segments;
        SegmentBounds query = new SegmentBounds(
                Math.min(minX, maxX), Math.min(minY, maxY),
                Math.max(minX, maxX), Math.max(minY, maxY));
        long firstX = cellCoordinate(query.minX());
        long lastX = cellCoordinate(query.maxX());
        long firstY = cellCoordinate(query.minY());
        long lastY = cellCoordinate(query.maxY());
        long columns = lastX - firstX + 1L;
        long rows = lastY - firstY + 1L;
        Set<Integer> candidates = new LinkedHashSet<>(globalSegments);

        if (columns > 0L && rows > 0L && columns <= 10_000L && rows <= 10_000L
                && columns * rows <= Math.max(64L, cells.size() * 4L)) {
            for (long x = firstX; x <= lastX; x++) {
                for (long y = firstY; y <= lastY; y++) {
                    Set<Integer> values = cells.get(new CellKey(x, y));
                    if (values != null) candidates.addAll(values);
                }
            }
        } else {
            for (int index = 0; index < bounds.size(); index++) {
                if (bounds.get(index).intersects(query)) candidates.add(index);
            }
        }

        List<VisionSegment> result = new ArrayList<>(candidates.size());
        for (int index : candidates) {
            if (index >= 0 && index < segments.size() && bounds.get(index).intersects(query)) {
                result.add(segments.get(index));
            }
        }
        return List.copyOf(result);
    }

    public boolean isBuiltFor(VttScene scene) {
        return scene != null && scene.getId().equals(sceneId);
    }

    public List<VisionSegment> allSegments() {
        return segments;
    }

    private void indexSegment(int index, SegmentBounds segment) {
        long firstX = cellCoordinate(segment.minX());
        long lastX = cellCoordinate(segment.maxX());
        long firstY = cellCoordinate(segment.minY());
        long lastY = cellCoordinate(segment.maxY());
        long columns = lastX - firstX + 1L;
        long rows = lastY - firstY + 1L;
        if (columns <= 0L || rows <= 0L || columns > MAX_CELLS_PER_SEGMENT
                || rows > MAX_CELLS_PER_SEGMENT || columns * rows > MAX_CELLS_PER_SEGMENT) {
            globalSegments.add(index);
            return;
        }
        for (long x = firstX; x <= lastX; x++) {
            for (long y = firstY; y <= lastY; y++) {
                cells.computeIfAbsent(new CellKey(x, y), ignored -> new LinkedHashSet<>()).add(index);
            }
        }
    }

    private SegmentBounds boundsOf(VisionSegment segment) {
        return new SegmentBounds(
                Math.min(segment.start().x(), segment.end().x()),
                Math.min(segment.start().y(), segment.end().y()),
                Math.max(segment.start().x(), segment.end().x()),
                Math.max(segment.start().y(), segment.end().y()));
    }

    private long cellCoordinate(double value) {
        return (long) Math.floor(value / CELL_SIZE);
    }

    private boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private record CellKey(long x, long y) {}

    private record SegmentBounds(double minX, double minY, double maxX, double maxY) {
        private boolean intersects(SegmentBounds other) {
            return maxX >= other.minX && minX <= other.maxX
                    && maxY >= other.minY && minY <= other.maxY;
        }
    }
}
