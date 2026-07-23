package com.petrick.vtt.editor.input;

import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Undo/redo history for token transformations in the currently active scene.
 * A continuous mouse gesture is stored as one entry.
 */
public final class EditorTokenTransformHistory {
    private static final int MAX_ENTRIES = 100;

    private final Deque<Change> undoStack = new ArrayDeque<>();
    private final Deque<Change> redoStack = new ArrayDeque<>();

    private String activeSceneId;
    private Snapshot pendingGesture;

    public void beginGesture(String sceneId, CanvasScene scene) {
        ensureScene(sceneId);
        pendingGesture = scene == null ? null : Snapshot.capture(scene);
    }

    public boolean endGesture(String sceneId, CanvasScene scene) {
        ensureScene(sceneId);
        Snapshot before = pendingGesture;
        pendingGesture = null;
        if (before == null || scene == null) return false;
        return record(before, Snapshot.capture(scene));
    }

    public boolean perform(String sceneId, CanvasScene scene, Runnable mutation) {
        ensureScene(sceneId);
        if (scene == null || mutation == null) return false;
        Snapshot before = Snapshot.capture(scene);
        mutation.run();
        return record(before, Snapshot.capture(scene));
    }

    public Result undo(String sceneId, CanvasScene scene) {
        ensureScene(sceneId);
        pendingGesture = null;
        if (scene == null || undoStack.isEmpty()) return Result.none();
        Change change = undoStack.removeLast();
        change.before().apply(
                scene, change.transformedObjectIds(), change.layerOrderChanged());
        redoStack.addLast(change);
        return new Result(true, change.affectedObjectIds());
    }

    public Result redo(String sceneId, CanvasScene scene) {
        ensureScene(sceneId);
        pendingGesture = null;
        if (scene == null || redoStack.isEmpty()) return Result.none();
        Change change = redoStack.removeLast();
        change.after().apply(
                scene, change.transformedObjectIds(), change.layerOrderChanged());
        undoStack.addLast(change);
        return new Result(true, change.affectedObjectIds());
    }

    public boolean canUndo(String sceneId) {
        ensureScene(sceneId);
        return !undoStack.isEmpty();
    }

    public boolean canRedo(String sceneId) {
        ensureScene(sceneId);
        return !redoStack.isEmpty();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
        pendingGesture = null;
    }

    private boolean record(Snapshot before, Snapshot after) {
        Change change = Change.between(before, after);
        if (change == null) return false;
        undoStack.addLast(change);
        while (undoStack.size() > MAX_ENTRIES) undoStack.removeFirst();
        redoStack.clear();
        return true;
    }

    private void ensureScene(String sceneId) {
        String normalized = sceneId == null ? "" : sceneId;
        if (normalized.equals(activeSceneId)) return;
        activeSceneId = normalized;
        clear();
    }

    public record Result(boolean changed, Set<String> affectedObjectIds) {
        public Result {
            affectedObjectIds = affectedObjectIds == null
                    ? Set.of() : Set.copyOf(affectedObjectIds);
        }

        private static Result none() {
            return new Result(false, Set.of());
        }
    }

    private record Change(
            Snapshot before,
            Snapshot after,
            Set<String> transformedObjectIds,
            Set<String> affectedObjectIds,
            boolean layerOrderChanged
    ) {
        private static Change between(Snapshot before, Snapshot after) {
            Map<String, CanvasObject> beforeById = before.byId();
            Map<String, CanvasObject> afterById = after.byId();
            Set<String> transformed = new LinkedHashSet<>();
            for (String id : beforeById.keySet()) {
                CanvasObject beforeObject = beforeById.get(id);
                CanvasObject afterObject = afterById.get(id);
                if (afterObject != null && transformDiffers(beforeObject, afterObject)) {
                    transformed.add(id);
                }
            }

            boolean layerOrderChanged = beforeById.keySet().equals(afterById.keySet())
                    && !before.objectIds().equals(after.objectIds());
            Set<String> affected = new LinkedHashSet<>(transformed);
            if (layerOrderChanged) {
                int commonSize = Math.min(before.objectIds().size(), after.objectIds().size());
                for (int index = 0; index < commonSize; index++) {
                    String beforeId = before.objectIds().get(index);
                    String afterId = after.objectIds().get(index);
                    if (!beforeId.equals(afterId)) {
                        affected.add(beforeId);
                        affected.add(afterId);
                    }
                }
            }

            affected.retainAll(beforeById.keySet());
            affected.retainAll(afterById.keySet());
            if (affected.isEmpty()) return null;
            return new Change(before, after, Set.copyOf(transformed),
                    Set.copyOf(affected), layerOrderChanged);
        }

        private static boolean transformDiffers(CanvasObject first, CanvasObject second) {
            return !first.transform().equals(second.transform())
                    || first.flippedHorizontally() != second.flippedHorizontally();
        }
    }

    private record Snapshot(List<CanvasObject> objects) {
        private Snapshot {
            objects = List.copyOf(objects);
        }

        private static Snapshot capture(CanvasScene scene) {
            return new Snapshot(scene.getObjects());
        }

        private Map<String, CanvasObject> byId() {
            Map<String, CanvasObject> result = new LinkedHashMap<>();
            for (CanvasObject object : objects) result.put(object.id(), object);
            return result;
        }

        private List<String> objectIds() {
            return objects.stream().map(CanvasObject::id).toList();
        }

        private void apply(
                CanvasScene scene,
                Set<String> affectedObjectIds,
                boolean restoreLayerOrder
        ) {
            Map<String, CanvasObject> currentById = new LinkedHashMap<>();
            for (CanvasObject object : scene.getObjects()) currentById.put(object.id(), object);

            Map<String, CanvasObject> targetById = byId();
            List<CanvasObject> restored = new ArrayList<>();
            if (restoreLayerOrder) {
                for (CanvasObject target : objects) {
                    CanvasObject current = currentById.remove(target.id());
                    if (current == null) continue;
                    restored.add(affectedObjectIds.contains(target.id())
                            ? restoreTransform(current, target) : current);
                }
            } else {
                for (CanvasObject current : scene.getObjects()) {
                    CanvasObject target = targetById.get(current.id());
                    restored.add(target != null && affectedObjectIds.contains(current.id())
                            ? restoreTransform(current, target) : current);
                    currentById.remove(current.id());
                }
            }
            restored.addAll(currentById.values());
            scene.replaceAllObjects(restored);
        }

        private CanvasObject restoreTransform(CanvasObject current, CanvasObject target) {
            return current.withTransform(target.transform())
                    .withFlippedHorizontally(target.flippedHorizontally());
        }
    }
}
