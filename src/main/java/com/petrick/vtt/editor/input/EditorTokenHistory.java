package com.petrick.vtt.editor.input;

import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneCollisionBox;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.feature.tabletop.VttSceneSize;
import com.petrick.vtt.feature.tabletop.VttSceneState;
import com.petrick.vtt.feature.tabletop.VttSceneTransform;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded undo/redo history for token transformations and lifecycle actions. */
public final class EditorTokenHistory {
    private static final int MAX_ENTRIES = 100;

    private final Deque<Change> undoStack = new ArrayDeque<>();
    private final Deque<Change> redoStack = new ArrayDeque<>();

    private String activeSceneId;
    private Snapshot pendingChange;

    public void begin(String sceneId, CanvasScene canvasScene, VttScene tabletopScene) {
        ensureScene(sceneId);
        pendingChange = canvasScene == null
                ? null : Snapshot.capture(canvasScene, tabletopScene);
    }

    public boolean end(String sceneId, CanvasScene canvasScene, VttScene tabletopScene) {
        ensureScene(sceneId);
        Snapshot before = pendingChange;
        pendingChange = null;
        if (before == null || canvasScene == null) return false;
        return record(before, Snapshot.capture(canvasScene, tabletopScene));
    }

    public Result undo(String sceneId, CanvasScene canvasScene, VttScene tabletopScene) {
        ensureScene(sceneId);
        pendingChange = null;
        if (canvasScene == null || undoStack.isEmpty()) return Result.none();
        Change change = undoStack.removeLast();
        change.apply(change.before(), canvasScene, tabletopScene);
        redoStack.addLast(change);
        return new Result(true, change.affectedObjectIds());
    }

    public Result redo(String sceneId, CanvasScene canvasScene, VttScene tabletopScene) {
        ensureScene(sceneId);
        pendingChange = null;
        if (canvasScene == null || redoStack.isEmpty()) return Result.none();
        Change change = redoStack.removeLast();
        change.apply(change.after(), canvasScene, tabletopScene);
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
        pendingChange = null;
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
            Map<String, Fields> changedFields,
            Set<String> structuralObjectIds,
            Set<String> affectedObjectIds,
            boolean layerOrderChanged
    ) {
        private static Change between(Snapshot before, Snapshot after) {
            Map<String, CanvasObject> beforeById = before.canvasById();
            Map<String, CanvasObject> afterById = after.canvasById();
            Set<String> allIds = new LinkedHashSet<>(beforeById.keySet());
            allIds.addAll(afterById.keySet());

            Map<String, Fields> fieldsById = new LinkedHashMap<>();
            Set<String> structural = new LinkedHashSet<>();
            for (String id : allIds) {
                CanvasObject beforeObject = beforeById.get(id);
                CanvasObject afterObject = afterById.get(id);
                if (beforeObject == null || afterObject == null) {
                    structural.add(id);
                    continue;
                }
                Fields fields = Fields.between(beforeObject, afterObject);
                if (fields.any()) fieldsById.put(id, fields);
            }

            boolean sameIds = beforeById.keySet().equals(afterById.keySet());
            boolean layerChanged = sameIds
                    && !before.canvasIds().equals(after.canvasIds());
            Set<String> affected = new LinkedHashSet<>(structural);
            affected.addAll(fieldsById.keySet());
            if (layerChanged) {
                for (int index = 0; index < before.canvasIds().size(); index++) {
                    String beforeId = before.canvasIds().get(index);
                    String afterId = after.canvasIds().get(index);
                    if (!beforeId.equals(afterId)) {
                        affected.add(beforeId);
                        affected.add(afterId);
                    }
                }
            }
            if (affected.isEmpty()) return null;
            return new Change(before, after, Map.copyOf(fieldsById), Set.copyOf(structural),
                    Set.copyOf(affected), layerChanged);
        }

        private void apply(Snapshot target, CanvasScene canvasScene, VttScene tabletopScene) {
            Map<String, CanvasObject> currentById = new LinkedHashMap<>();
            for (CanvasObject object : canvasScene.getObjects()) {
                currentById.put(object.id(), object);
            }
            Map<String, CanvasObject> targetById = target.canvasById();

            for (String id : structuralObjectIds) {
                CanvasObject targetObject = targetById.get(id);
                if (targetObject == null) {
                    currentById.remove(id);
                    removePersistentObject(tabletopScene, id);
                } else {
                    currentById.put(id, targetObject);
                    restorePersistentObject(tabletopScene, target.persistentById().get(id));
                }
            }

            for (Map.Entry<String, Fields> entry : changedFields.entrySet()) {
                CanvasObject current = currentById.get(entry.getKey());
                CanvasObject targetObject = targetById.get(entry.getKey());
                if (current != null && targetObject != null) {
                    currentById.put(entry.getKey(),
                            entry.getValue().restore(current, targetObject));
                }
            }

            List<CanvasObject> restored = new ArrayList<>();
            if (layerOrderChanged || !structuralObjectIds.isEmpty()) {
                for (String id : target.canvasIds()) {
                    CanvasObject object = currentById.remove(id);
                    if (object != null) restored.add(object);
                }
            } else {
                for (CanvasObject object : canvasScene.getObjects()) {
                    CanvasObject restoredObject = currentById.remove(object.id());
                    if (restoredObject != null) restored.add(restoredObject);
                }
            }
            restored.addAll(currentById.values());
            canvasScene.replaceAllObjects(restored);
        }

        private void removePersistentObject(VttScene scene, String id) {
            if (scene == null) return;
            scene.removeObject(id);
            scene.removeVisionSourceObjectId(id);
        }

        private void restorePersistentObject(VttScene scene, PersistentObject target) {
            if (scene == null || target == null) return;
            scene.removeObject(target.id());
            scene.addObject(target.toSceneObject());
            if (target.visionSource()) scene.addVisionSourceObjectId(target.id());
            else scene.removeVisionSourceObjectId(target.id());
        }
    }

    private record Fields(
            boolean transform,
            boolean displayName,
            boolean activeState,
            boolean visible,
            boolean flipped
    ) {
        private static Fields between(CanvasObject before, CanvasObject after) {
            return new Fields(
                    !before.transform().equals(after.transform()),
                    !before.displayName().equals(after.displayName()),
                    !before.activeStateId().equals(after.activeStateId()),
                    before.visible() != after.visible(),
                    before.flippedHorizontally() != after.flippedHorizontally()
            );
        }

        private boolean any() {
            return transform || displayName || activeState || visible || flipped;
        }

        private CanvasObject restore(CanvasObject current, CanvasObject target) {
            CanvasObject result = current;
            if (transform) result = result.withTransform(target.transform());
            if (displayName) result = result.withDisplayName(target.displayName());
            if (activeState) result = result.withActiveState(target.activeStateId());
            if (visible) result = result.withVisible(target.visible());
            if (flipped) result = result.withFlippedHorizontally(target.flippedHorizontally());
            return result;
        }
    }

    private record Snapshot(
            List<CanvasObject> canvasObjects,
            Map<String, PersistentObject> persistentById
    ) {
        private Snapshot {
            canvasObjects = List.copyOf(canvasObjects);
            persistentById = Map.copyOf(persistentById);
        }

        private static Snapshot capture(CanvasScene canvasScene, VttScene tabletopScene) {
            Map<String, PersistentObject> persistent = new LinkedHashMap<>();
            if (tabletopScene != null) {
                Set<String> visionSources =
                        new LinkedHashSet<>(tabletopScene.getVisionSourceObjectIds());
                for (VttSceneObject object : tabletopScene.getObjects()) {
                    if (object != null && object.getId() != null) {
                        persistent.put(object.getId(),
                                PersistentObject.capture(
                                        object, visionSources.contains(object.getId())));
                    }
                }
            }
            return new Snapshot(canvasScene.getObjects(), persistent);
        }

        private Map<String, CanvasObject> canvasById() {
            Map<String, CanvasObject> result = new LinkedHashMap<>();
            for (CanvasObject object : canvasObjects) result.put(object.id(), object);
            return result;
        }

        private List<String> canvasIds() {
            return canvasObjects.stream().map(CanvasObject::id).toList();
        }
    }

    private record PersistentObject(
            String id,
            String displayName,
            String sourceTokenDefinitionId,
            double x,
            double y,
            double scaleX,
            double scaleY,
            double rotationDegrees,
            double width,
            double height,
            String activeStateId,
            boolean visible,
            boolean flipped,
            VttSceneCollisionBox collisionBox,
            int layerIndex,
            double visionOuterRadius,
            double visionInnerRadius,
            boolean visionEnabled,
            String ownerId,
            boolean visionSource
    ) {
        private static PersistentObject capture(VttSceneObject object, boolean visionSource) {
            VttSceneCollisionBox box = object.getCollisionBox();
            VttSceneCollisionBox boxCopy = box == null ? null : new VttSceneCollisionBox(
                    box.getOffsetX(), box.getOffsetY(), box.getWidth(), box.getHeight());
            return new PersistentObject(
                    object.getId(), object.getDisplayName(), object.getSourceTokenDefinitionId(),
                    object.getTransform().getX(), object.getTransform().getY(),
                    object.getTransform().getScaleX(), object.getTransform().getScaleY(),
                    object.getTransform().getRotationDegrees(),
                    object.getSize().getWidth(), object.getSize().getHeight(),
                    object.getState().getActiveStateId(), object.getState().isVisible(),
                    object.getState().isFlippedHorizontally(), boxCopy, object.getLayerIndex(),
                    object.getVisionOuterRadius(), object.getVisionInnerRadius(),
                    object.isVisionEnabled(), object.getOwnerId(), visionSource);
        }

        private VttSceneObject toSceneObject() {
            VttSceneObject object = new VttSceneObject(id, displayName, sourceTokenDefinitionId);
            object.setTransform(new VttSceneTransform(
                    x, y, scaleX, scaleY, rotationDegrees));
            object.setSize(new VttSceneSize(width, height));
            object.setState(new VttSceneState(activeStateId, visible, flipped));
            object.setLayerIndex(layerIndex);
            object.setVisionOuterRadius(visionOuterRadius);
            object.setVisionInnerRadius(visionInnerRadius);
            object.setVisionEnabled(visionEnabled);
            object.setOwnerId(ownerId);
            if (collisionBox != null) {
                object.setCollisionBox(new VttSceneCollisionBox(
                        collisionBox.getOffsetX(), collisionBox.getOffsetY(),
                        collisionBox.getWidth(), collisionBox.getHeight()));
            }
            return object;
        }
    }
}
