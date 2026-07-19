package com.petrick.vtt.feature.tabletop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Uniform-grid index for scene-object centers used by server interest queries. */
public final class SceneObjectSpatialIndex {
    public static final double CELL_SIZE = 256.0;

    private final Map<CellKey, LinkedHashSet<String>> cells = new HashMap<>();
    private final Map<String, VttSceneObject> objectsById = new HashMap<>();
    private final Map<String, CellKey> objectCells = new HashMap<>();
    private String sceneId;

    public void rebuild(VttScene scene) {
        clear();
        if (scene == null) return;
        sceneId = scene.getId();
        for (VttSceneObject object : scene.getObjects()) addOrUpdate(object);
    }

    public void addOrUpdate(VttSceneObject object) {
        if (!valid(object)) return;
        String objectId = object.getId();
        CellKey newCell = cellAt(object.getTransform().getX(), object.getTransform().getY());
        CellKey previousCell = objectCells.put(objectId, newCell);
        objectsById.put(objectId, object);
        if (previousCell != null && !previousCell.equals(newCell)) removeFromCell(previousCell, objectId);
        cells.computeIfAbsent(newCell, ignored -> new LinkedHashSet<>()).add(objectId);
    }

    public void remove(String objectId) {
        if (objectId == null) return;
        CellKey previousCell = objectCells.remove(objectId);
        objectsById.remove(objectId);
        if (previousCell != null) removeFromCell(previousCell, objectId);
    }

    public List<VttSceneObject> query(double minX, double minY, double maxX, double maxY) {
        if (!Double.isFinite(minX) || !Double.isFinite(minY)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY)) return List.of();
        double normalizedMinX = Math.min(minX, maxX);
        double normalizedMaxX = Math.max(minX, maxX);
        double normalizedMinY = Math.min(minY, maxY);
        double normalizedMaxY = Math.max(minY, maxY);
        long firstCellX = cellCoordinate(normalizedMinX);
        long lastCellX = cellCoordinate(normalizedMaxX);
        long firstCellY = cellCoordinate(normalizedMinY);
        long lastCellY = cellCoordinate(normalizedMaxY);
        long columnCount = lastCellX - firstCellX + 1L;
        long rowCount = lastCellY - firstCellY + 1L;

        Set<String> candidates = new LinkedHashSet<>();
        if (columnCount > 0L && rowCount > 0L
                && columnCount <= 10_000L && rowCount <= 10_000L
                && columnCount * rowCount <= Math.max(64L, cells.size() * 4L)) {
            for (long cellX = firstCellX; cellX <= lastCellX; cellX++) {
                for (long cellY = firstCellY; cellY <= lastCellY; cellY++) {
                    Set<String> cellObjects = cells.get(new CellKey(cellX, cellY));
                    if (cellObjects != null) candidates.addAll(cellObjects);
                }
            }
        } else {
            for (Map.Entry<String, VttSceneObject> entry : objectsById.entrySet()) {
                VttSceneObject object = entry.getValue();
                double x = object.getTransform().getX();
                double y = object.getTransform().getY();
                if (x >= normalizedMinX && x <= normalizedMaxX
                        && y >= normalizedMinY && y <= normalizedMaxY) {
                    candidates.add(entry.getKey());
                }
            }
        }

        List<VttSceneObject> result = new ArrayList<>(candidates.size());
        for (String objectId : candidates) {
            VttSceneObject object = objectsById.get(objectId);
            if (object == null) continue;
            double x = object.getTransform().getX();
            double y = object.getTransform().getY();
            if (x >= normalizedMinX && x <= normalizedMaxX
                    && y >= normalizedMinY && y <= normalizedMaxY) result.add(object);
        }
        return result;
    }

    public boolean isBuiltFor(VttScene scene) {
        return scene != null && scene.getId().equals(sceneId);
    }

    public int size() {
        return objectsById.size();
    }

    private void clear() {
        cells.clear();
        objectsById.clear();
        objectCells.clear();
        sceneId = null;
    }

    private void removeFromCell(CellKey cell, String objectId) {
        LinkedHashSet<String> values = cells.get(cell);
        if (values == null) return;
        values.remove(objectId);
        if (values.isEmpty()) cells.remove(cell);
    }

    private boolean valid(VttSceneObject object) {
        return object != null && object.getId() != null && !object.getId().isBlank()
                && object.getTransform() != null
                && Double.isFinite(object.getTransform().getX())
                && Double.isFinite(object.getTransform().getY());
    }

    private CellKey cellAt(double x, double y) {
        return new CellKey(cellCoordinate(x), cellCoordinate(y));
    }

    private long cellCoordinate(double value) {
        return (long) Math.floor(value / CELL_SIZE);
    }

    private record CellKey(long x, long y) {}
}
