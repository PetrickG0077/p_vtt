package com.petrick.vtt.editor.input;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.tabletop.VttDoor;
import com.petrick.vtt.feature.tabletop.VttFogArea;
import com.petrick.vtt.feature.tabletop.VttFogOfWar;
import com.petrick.vtt.feature.tabletop.VttLight;
import com.petrick.vtt.feature.tabletop.VttLightType;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneBackgroundTransform;
import com.petrick.vtt.feature.tabletop.VttSceneCameraView;
import com.petrick.vtt.feature.tabletop.VttSceneMap;
import com.petrick.vtt.feature.tabletop.VttSceneCollisionBox;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.feature.tabletop.VttSceneSize;
import com.petrick.vtt.feature.tabletop.VttSceneState;
import com.petrick.vtt.feature.tabletop.VttSceneTransform;
import com.petrick.vtt.feature.tabletop.VttWall;
import com.petrick.vtt.feature.map.MapTextureMode;
import com.petrick.vtt.feature.tabletop.persistence.CanvasSceneToVttSceneMapper;
import com.petrick.vtt.network.VttSceneFingerprint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded undo/redo history for token and scene-environment editor actions. */
public final class EditorSceneHistory {
    private static final int MAX_ENTRIES = 100;
    private static final Gson GSON = new GsonBuilder().create();

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
        return new Result(true, change.affectedObjectIds(), change.backgroundChanged(),
                change.before().environment().backgroundAssetId());
    }

    public Result redo(String sceneId, CanvasScene canvasScene, VttScene tabletopScene) {
        ensureScene(sceneId);
        pendingChange = null;
        if (canvasScene == null || redoStack.isEmpty()) return Result.none();
        Change change = redoStack.removeLast();
        change.apply(change.after(), canvasScene, tabletopScene);
        undoStack.addLast(change);
        return new Result(true, change.affectedObjectIds(), change.backgroundChanged(),
                change.after().environment().backgroundAssetId());
    }

    public boolean canUndo(String sceneId) {
        ensureScene(sceneId);
        return !undoStack.isEmpty();
    }

    public boolean canRedo(String sceneId) {
        ensureScene(sceneId);
        return !redoStack.isEmpty();
    }

    public String nextUndoDescription(String sceneId) {
        ensureScene(sceneId);
        return undoStack.isEmpty() ? "" : undoStack.getLast().description();
    }

    public String nextRedoDescription(String sceneId) {
        ensureScene(sceneId);
        return redoStack.isEmpty() ? "" : redoStack.getLast().description();
    }

    public PreparedNetworkAction prepareUndo(String sceneId, VttScene templateScene) {
        ensureScene(sceneId);
        if (undoStack.isEmpty() || templateScene == null) return null;
        Change change = undoStack.getLast();
        return prepare(change.after(), change.before(), change.description(), templateScene);
    }

    public PreparedNetworkAction prepareRedo(String sceneId, VttScene templateScene) {
        ensureScene(sceneId);
        if (redoStack.isEmpty() || templateScene == null) return null;
        Change change = redoStack.getLast();
        return prepare(change.before(), change.after(), change.description(), templateScene);
    }

    private PreparedNetworkAction prepare(
            Snapshot expected, Snapshot target, String description, VttScene templateScene
    ) {
        VttScene expectedScene = expected.toScene(templateScene);
        VttScene targetScene = target.toScene(templateScene);
        return new PreparedNetworkAction(
                VttSceneFingerprint.of(expectedScene), GSON.toJson(targetScene), description);
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

    public record Result(
            boolean changed,
            Set<String> affectedObjectIds,
            boolean backgroundChanged,
            String backgroundAssetId
    ) {
        public Result {
            affectedObjectIds = affectedObjectIds == null
                    ? Set.of() : Set.copyOf(affectedObjectIds);
        }

        private static Result none() {
            return new Result(false, Set.of(), false, null);
        }
    }

    public record PreparedNetworkAction(
            String expectedFingerprint, String targetSceneJson, String description
    ) {}

    private record Change(
            Snapshot before,
            Snapshot after,
            Map<String, Fields> changedFields,
            Set<String> structuralObjectIds,
            Set<String> persistentChangedObjectIds,
            Set<String> affectedObjectIds,
            boolean layerOrderChanged,
            boolean environmentChanged,
            boolean backgroundChanged,
            String description
    ) {
        private static Change between(Snapshot before, Snapshot after) {
            Map<String, CanvasObject> beforeById = before.canvasById();
            Map<String, CanvasObject> afterById = after.canvasById();
            Set<String> allIds = new LinkedHashSet<>(beforeById.keySet());
            allIds.addAll(afterById.keySet());

            Map<String, Fields> fieldsById = new LinkedHashMap<>();
            Set<String> structural = new LinkedHashSet<>();
            Set<String> persistentChanged = new LinkedHashSet<>();
            for (String id : allIds) {
                CanvasObject beforeObject = beforeById.get(id);
                CanvasObject afterObject = afterById.get(id);
                if (beforeObject == null || afterObject == null) {
                    structural.add(id);
                    continue;
                }
                Fields fields = Fields.between(beforeObject, afterObject);
                if (fields.any()) fieldsById.put(id, fields);
                if (!java.util.Objects.equals(
                        before.persistentById().get(id),
                        after.persistentById().get(id))) {
                    persistentChanged.add(id);
                }
            }

            boolean sameIds = beforeById.keySet().equals(afterById.keySet());
            boolean layerChanged = sameIds
                    && !before.canvasIds().equals(after.canvasIds());
            Set<String> affected = new LinkedHashSet<>(structural);
            affected.addAll(fieldsById.keySet());
            affected.addAll(persistentChanged);
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
            boolean environmentChanged =
                    !before.environment().equals(after.environment());
            boolean backgroundChanged = !java.util.Objects.equals(
                    before.environment().backgroundAssetId(),
                    after.environment().backgroundAssetId());
            if (affected.isEmpty() && !environmentChanged) return null;
            String description = describe(before, after, fieldsById, structural,
                    persistentChanged, layerChanged);
            return new Change(before, after, Map.copyOf(fieldsById), Set.copyOf(structural),
                    Set.copyOf(persistentChanged), Set.copyOf(affected),
                    layerChanged, environmentChanged, backgroundChanged, description);
        }

        private static String describe(
                Snapshot before,
                Snapshot after,
                Map<String, Fields> fieldsById,
                Set<String> structural,
                Set<String> persistentChanged,
                boolean layerChanged
        ) {
            if (!structural.isEmpty()) {
                long added = structural.stream().filter(id -> !before.canvasById().containsKey(id)).count();
                long removed = structural.size() - added;
                if (added > 0 && removed == 0) {
                    boolean duplicate = structural.stream().anyMatch(id -> id.contains("_copy"));
                    return duplicate ? "Duplicate token" : "Create token";
                }
                if (removed > 0 && added == 0) return "Delete token";
                return "Change tokens";
            }

            Environment oldEnvironment = before.environment();
            Environment newEnvironment = after.environment();
            String map = describeCollectionChange(oldEnvironment.maps(), newEnvironment.maps(),
                    SceneMap::id, "map");
            if (map != null) return map;
            String wall = describeCollectionChange(oldEnvironment.walls(), newEnvironment.walls(),
                    Wall::id, "wall");
            if (wall != null) return wall;
            String door = describeDoorChange(oldEnvironment.doors(), newEnvironment.doors());
            if (door != null) return door;
            String light = describeCollectionChange(oldEnvironment.lights(),
                    newEnvironment.lights(), Light::id, "light");
            if (light != null) return light;
            String fog = describeFogChange(oldEnvironment, newEnvironment);
            if (fog != null) return fog;
            if (!java.util.Objects.equals(oldEnvironment.backgroundAssetId(),
                    newEnvironment.backgroundAssetId())) return "Change background";
            if (!oldEnvironment.backgroundTransform().equals(
                    newEnvironment.backgroundTransform())) {
                if (Double.compare(oldEnvironment.backgroundTransform().x(),
                        newEnvironment.backgroundTransform().x()) != 0
                        || Double.compare(oldEnvironment.backgroundTransform().y(),
                        newEnvironment.backgroundTransform().y()) != 0) {
                    return "Move scene background";
                }
                return "Resize scene background";
            }
            if (!java.util.Objects.equals(
                    oldEnvironment.initialCameraView(),
                    newEnvironment.initialCameraView())) return "Change initial scene view";
            if (!oldEnvironment.grid().equals(newEnvironment.grid())) return "Change grid";

            if (layerChanged) return "Change token layer";
            for (Map.Entry<String, Fields> entry : fieldsById.entrySet()) {
                CanvasObject oldObject = before.canvasById().get(entry.getKey());
                CanvasObject newObject = after.canvasById().get(entry.getKey());
                Fields fields = entry.getValue();
                if (fields.displayName()) return "Rename token";
                if (fields.activeState()) return "Change token state";
                if (fields.visible()) return "Toggle token visibility";
                if (fields.flipped()) return "Flip token";
                if (fields.transform() && oldObject != null && newObject != null) {
                    if (!oldObject.transform().position().equals(
                            newObject.transform().position())) return "Move token";
                    if (!oldObject.transform().scale().equals(
                            newObject.transform().scale())) return "Resize token";
                    if (Double.compare(oldObject.transform().rotationDegrees(),
                            newObject.transform().rotationDegrees()) != 0) return "Rotate token";
                    return "Transform token";
                }
            }

            for (String id : persistentChanged) {
                PersistentObject oldObject = before.persistentById().get(id);
                PersistentObject newObject = after.persistentById().get(id);
                if (oldObject == null || newObject == null) continue;
                if (!java.util.Objects.equals(oldObject.collisionBox(),
                        newObject.collisionBox())) return "Edit collision box";
                if (!java.util.Objects.equals(oldObject.ownerId(),
                        newObject.ownerId())) return "Change token owner";
                if (oldObject.visionSource() != newObject.visionSource()) {
                    return "Change vision source";
                }
                if (Double.compare(oldObject.visionOuterRadius(),
                        newObject.visionOuterRadius()) != 0
                        || Double.compare(oldObject.visionInnerRadius(),
                        newObject.visionInnerRadius()) != 0
                        || oldObject.visionEnabled() != newObject.visionEnabled()) {
                    return "Change token vision";
                }
            }
            return "Edit scene";
        }

        private static <T> String describeCollectionChange(
                List<T> before,
                List<T> after,
                java.util.function.Function<T, String> id,
                String name
        ) {
            Set<String> oldIds = before.stream().map(id).collect(
                    java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Set<String> newIds = after.stream().map(id).collect(
                    java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (!oldIds.equals(newIds)) {
                if (newIds.containsAll(oldIds)) return "Create " + name;
                if (oldIds.containsAll(newIds)) return "Delete " + name;
                return "Change " + name;
            }
            return before.equals(after) ? null : "Edit " + name;
        }

        private static String describeDoorChange(List<Door> before, List<Door> after) {
            String structural = describeCollectionChange(before, after, Door::id, "door");
            if (structural == null || !structural.equals("Edit door")) return structural;
            Map<String, Door> oldById = new LinkedHashMap<>();
            before.forEach(door -> oldById.put(door.id(), door));
            for (Door door : after) {
                Door oldDoor = oldById.get(door.id());
                if (oldDoor == null) continue;
                if (oldDoor.open() != door.open()) return door.open() ? "Open door" : "Close door";
                if (oldDoor.locked() != door.locked()) return door.locked() ? "Lock door" : "Unlock door";
            }
            return "Edit door";
        }

        private static String describeFogChange(Environment before, Environment after) {
            List<FogArea> oldAreas = new ArrayList<>(before.revealedFog());
            oldAreas.addAll(before.hiddenFog());
            List<FogArea> newAreas = new ArrayList<>(after.revealedFog());
            newAreas.addAll(after.hiddenFog());
            String areas = describeCollectionChange(oldAreas, newAreas, FogArea::id, "fog area");
            if (areas != null && !areas.equals("Edit fog area")) return areas;
            if (areas != null) {
                Map<String, FogArea> oldById = new LinkedHashMap<>();
                oldAreas.forEach(area -> oldById.put(area.id(), area));
                for (FogArea area : newAreas) {
                    FogArea oldArea = oldById.get(area.id());
                    if (oldArea != null && oldArea.visible() != area.visible()) {
                        return "Toggle fog area";
                    }
                }
                return areas;
            }
            if (before.fogEnabled() != after.fogEnabled()) return "Toggle fog";
            if (before.fogDefaultHidden() != after.fogDefaultHidden()) return "Change fog";
            return null;
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
            for (String id : persistentChangedObjectIds) {
                if (!structuralObjectIds.contains(id)) {
                    restorePersistentObject(tabletopScene, target.persistentById().get(id));
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
            if (environmentChanged) target.environment().restore(tabletopScene);
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
            Map<String, PersistentObject> persistentById,
            Environment environment
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
            return new Snapshot(canvasScene.getObjects(), persistent,
                    Environment.capture(tabletopScene));
        }

        private Map<String, CanvasObject> canvasById() {
            Map<String, CanvasObject> result = new LinkedHashMap<>();
            for (CanvasObject object : canvasObjects) result.put(object.id(), object);
            return result;
        }

        private List<String> canvasIds() {
            return canvasObjects.stream().map(CanvasObject::id).toList();
        }

        private VttScene toScene(VttScene template) {
            VttScene result = GSON.fromJson(GSON.toJson(template), VttScene.class);
            result.clearObjects();
            result.clearVisionSourceObjectIds();
            for (CanvasObject canvasObject : canvasObjects) {
                PersistentObject persistent = persistentById.get(canvasObject.id());
                if (persistent == null) continue;
                result.addObject(persistent.toSceneObject());
                if (persistent.visionSource()) {
                    result.addVisionSourceObjectId(persistent.id());
                }
            }
            environment.restore(result);
            CanvasScene targetCanvas = new CanvasScene();
            targetCanvas.replaceAllObjects(canvasObjects);
            CanvasSceneToVttSceneMapper.copyCanvasObjectsToScene(targetCanvas, result);
            return result;
        }
    }

    private record Environment(
            List<Wall> walls,
            List<Door> doors,
            String backgroundAssetId,
            BackgroundTransform backgroundTransform,
            List<SceneMap> maps,
            List<Light> lights,
            InitialCameraView initialCameraView,
            Grid grid,
            boolean fogEnabled,
            boolean fogDefaultHidden,
            List<FogArea> revealedFog,
            List<FogArea> hiddenFog
    ) {
        private Environment {
            walls = List.copyOf(walls);
            doors = List.copyOf(doors);
            maps = List.copyOf(maps);
            lights = List.copyOf(lights);
            revealedFog = List.copyOf(revealedFog);
            hiddenFog = List.copyOf(hiddenFog);
        }

        private static Environment capture(VttScene scene) {
            if (scene == null) {
                return new Environment(List.of(), List.of(), null,
                        BackgroundTransform.defaults(), List.of(), List.of(), null, Grid.defaults(),
                        false, false, List.of(), List.of());
            }
            VttFogOfWar fog = scene.getFogOfWar();
            return new Environment(
                    scene.getWalls().stream().filter(java.util.Objects::nonNull)
                            .map(Wall::capture).toList(),
                    scene.getDoors().stream().filter(java.util.Objects::nonNull)
                            .map(Door::capture).toList(),
                    scene.getBackgroundAssetId(),
                    BackgroundTransform.capture(scene.getBackgroundTransform()),
                    scene.getMaps().stream().filter(java.util.Objects::nonNull)
                            .map(SceneMap::capture).toList(),
                    scene.getLights().stream().filter(java.util.Objects::nonNull)
                            .map(Light::capture).toList(),
                    InitialCameraView.capture(scene.getInitialCameraView()),
                    Grid.capture(scene),
                    fog.isEnabled(), fog.isDefaultHidden(),
                    fog.getRevealedAreas().stream().filter(java.util.Objects::nonNull)
                            .map(FogArea::capture).toList(),
                    fog.getHiddenAreas().stream().filter(java.util.Objects::nonNull)
                            .map(FogArea::capture).toList());
        }

        private void restore(VttScene scene) {
            if (scene == null) return;
            scene.getWalls().clear();
            walls.stream().map(Wall::restore).forEach(scene::addWall);
            scene.getDoors().clear();
            doors.stream().map(Door::restore).forEach(scene::addDoor);
            scene.setBackgroundAssetId(backgroundAssetId);
            scene.setBackgroundTransform(backgroundTransform.restore());
            scene.getMaps().clear();
            maps.stream().map(SceneMap::restore).forEach(scene::addMap);
            scene.getLights().clear();
            lights.stream().map(Light::restore).forEach(scene::addLight);
            scene.setInitialCameraView(initialCameraView == null
                    ? null : initialCameraView.restore());
            scene.setGrid(grid.restore());

            VttFogOfWar fog = scene.getFogOfWar();
            fog.setEnabled(fogEnabled);
            fog.setDefaultHidden(fogDefaultHidden);
            fog.clearAreas();
            revealedFog.stream().map(FogArea::restore).forEach(fog::addRevealedArea);
            hiddenFog.stream().map(FogArea::restore).forEach(fog::addHiddenArea);
        }
    }

    private record Light(
            String id, VttLightType type, double x, double y,
            double innerRadius, double outerRadius,
            int colorRgb, double intensity, boolean tintEnabled, boolean enabled,
            double directionDegrees, double outerConeAngleDegrees,
            double innerConeAngleDegrees,
            String attachedToObjectId,
            double attachmentOffsetX,
            double attachmentOffsetY,
            double attachmentDirectionOffsetDegrees
    ) {
        private static Light capture(VttLight light) {
            return new Light(light.getId(), light.getType(), light.getX(), light.getY(),
                    light.getInnerRadius(), light.getOuterRadius(), light.getColorRgb(),
                    light.getIntensity(), light.isTintEnabled(), light.isEnabled(),
                    light.getDirectionDegrees(), light.getConeAngleDegrees(),
                    light.getInnerConeAngleDegrees(), light.getAttachedToObjectId(),
                    light.getAttachmentOffsetX(), light.getAttachmentOffsetY(),
                    light.getAttachmentDirectionOffsetDegrees());
        }

        private VttLight restore() {
            VttLight light = new VttLight(id, x, y);
            light.setType(type);
            light.setOuterRadius(outerRadius);
            light.setInnerRadius(innerRadius);
            light.setColorRgb(colorRgb);
            light.setIntensity(intensity);
            light.setTintEnabled(tintEnabled);
            light.setEnabled(enabled);
            light.setDirectionDegrees(directionDegrees);
            light.setConeAngleDegrees(outerConeAngleDegrees);
            light.setInnerConeAngleDegrees(innerConeAngleDegrees);
            light.setAttachedToObjectId(attachedToObjectId);
            light.setAttachmentOffsetX(attachmentOffsetX);
            light.setAttachmentOffsetY(attachmentOffsetY);
            light.setAttachmentDirectionOffsetDegrees(attachmentDirectionOffsetDegrees);
            return light;
        }
    }

    private record SceneMap(
            String id,
            String displayName,
            String sourceMapDefinitionId,
            String assetId,
            BackgroundTransform transform,
            int layerIndex,
            boolean visible,
            MapTextureMode textureMode
    ) {
        private static SceneMap capture(VttSceneMap map) {
            return new SceneMap(
                    map.getId(), map.getDisplayName(), map.getSourceMapDefinitionId(),
                    map.getAssetId(), BackgroundTransform.capture(map.getTransform()),
                    map.getLayerIndex(), map.isVisible(), map.getTextureMode());
        }

        private VttSceneMap restore() {
            VttSceneMap map = new VttSceneMap(
                    id, displayName, sourceMapDefinitionId, assetId, textureMode);
            map.setTransform(transform.restore());
            map.setLayerIndex(layerIndex);
            map.setVisible(visible);
            return map;
        }
    }

    private record BackgroundTransform(double x, double y, double scaleX, double scaleY) {
        private static BackgroundTransform capture(VttSceneBackgroundTransform transform) {
            return new BackgroundTransform(transform.getX(), transform.getY(),
                    transform.getScaleX(), transform.getScaleY());
        }

        private static BackgroundTransform defaults() {
            return new BackgroundTransform(0.0, 0.0, 1.0, 1.0);
        }

        private VttSceneBackgroundTransform restore() {
            return new VttSceneBackgroundTransform(x, y, scaleX, scaleY);
        }
    }

    private record InitialCameraView(double x, double y, double zoom) {
        private static InitialCameraView capture(VttSceneCameraView view) {
            return view == null ? null : new InitialCameraView(
                    view.getX(), view.getY(), view.getZoom());
        }

        private VttSceneCameraView restore() {
            return new VttSceneCameraView(x, y, zoom);
        }
    }

    private record Wall(
            String id, Transform transform, Size size,
            boolean visible, boolean blocksVision, boolean blocksMovement
    ) {
        private static Wall capture(VttWall wall) {
            return new Wall(wall.getId(), Transform.capture(wall.getTransform()),
                    Size.capture(wall.getSize()), wall.isVisible(),
                    wall.isBlocksVision(), wall.isBlocksMovement());
        }

        private VttWall restore() {
            VttWall wall = new VttWall(id, transform.restore(), size.restore());
            wall.setVisible(visible);
            wall.setBlocksVision(blocksVision);
            wall.setBlocksMovement(blocksMovement);
            return wall;
        }
    }

    private record Door(
            String id, String wallId, Transform transform, Size size,
            boolean open, boolean locked, boolean visible,
            boolean blocksVisionWhenClosed, boolean blocksMovementWhenClosed
    ) {
        private static Door capture(VttDoor door) {
            return new Door(door.getId(), door.getWallId(),
                    Transform.capture(door.getTransform()), Size.capture(door.getSize()),
                    door.isOpen(), door.isLocked(), door.isVisible(),
                    door.isBlocksVisionWhenClosed(), door.isBlocksMovementWhenClosed());
        }

        private VttDoor restore() {
            VttDoor door = new VttDoor(id, wallId, transform.restore(), size.restore());
            door.setOpen(open);
            door.setLocked(locked);
            door.setVisible(visible);
            door.setBlocksVisionWhenClosed(blocksVisionWhenClosed);
            door.setBlocksMovementWhenClosed(blocksMovementWhenClosed);
            return door;
        }
    }

    private record FogArea(String id, Transform transform, Size size, boolean visible) {
        private static FogArea capture(VttFogArea area) {
            return new FogArea(area.getId(), Transform.capture(area.getTransform()),
                    Size.capture(area.getSize()), area.isVisible());
        }

        private VttFogArea restore() {
            VttFogArea area = new VttFogArea(id, transform.restore(), size.restore());
            area.setVisible(visible);
            return area;
        }
    }

    private record Grid(
            int colorRgb, double opacity, double gridSize, int lineWidth, boolean topLayer
    ) {
        private static Grid capture(VttScene scene) {
            var grid = scene.getGrid();
            return new Grid(grid.getColorRgb(), grid.getOpacity(), grid.getGridSize(),
                    grid.getLineWidth(), grid.isTopLayer());
        }

        private static Grid defaults() {
            return new Grid(0xFFFFFF, 0.2, 64.0, 1, false);
        }

        private com.petrick.vtt.feature.tabletop.VttSceneGrid restore() {
            var grid = new com.petrick.vtt.feature.tabletop.VttSceneGrid();
            grid.setColorRgb(colorRgb);
            grid.setOpacity(opacity);
            grid.setGridSize(gridSize);
            grid.setLineWidth(lineWidth);
            grid.setTopLayer(topLayer);
            return grid;
        }
    }

    private record Transform(
            double x, double y, double scaleX, double scaleY, double rotationDegrees
    ) {
        private static Transform capture(VttSceneTransform transform) {
            return new Transform(transform.getX(), transform.getY(),
                    transform.getScaleX(), transform.getScaleY(),
                    transform.getRotationDegrees());
        }

        private VttSceneTransform restore() {
            return new VttSceneTransform(x, y, scaleX, scaleY, rotationDegrees);
        }
    }

    private record Size(double width, double height) {
        private static Size capture(VttSceneSize size) {
            return new Size(size.getWidth(), size.getHeight());
        }

        private VttSceneSize restore() {
            return new VttSceneSize(width, height);
        }
    }

    private record PersistentObject(
            String id,
            String displayName,
            String sourceTokenDefinitionId,
            String sourceAttachmentDefinitionId,
            AttachmentBinding attachmentBinding,
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
            CollisionBox collisionBox,
            int layerIndex,
            double visionOuterRadius,
            double visionInnerRadius,
            boolean visionEnabled,
            boolean visionOwnLightEnabled,
            int tintColorRgb,
            String ownerId,
            boolean visionSource
    ) {
        private static PersistentObject capture(VttSceneObject object, boolean visionSource) {
            VttSceneCollisionBox box = object.getCollisionBox();
            CollisionBox boxCopy = box == null ? null : new CollisionBox(
                    box.getOffsetX(), box.getOffsetY(), box.getWidth(), box.getHeight());
            return new PersistentObject(
                    object.getId(), object.getDisplayName(), object.getSourceTokenDefinitionId(),
                    object.getSourceAttachmentDefinitionId(),
                    AttachmentBinding.capture(object.getAttachmentBinding()),
                    object.getTransform().getX(), object.getTransform().getY(),
                    object.getTransform().getScaleX(), object.getTransform().getScaleY(),
                    object.getTransform().getRotationDegrees(),
                    object.getSize().getWidth(), object.getSize().getHeight(),
                    object.getState().getActiveStateId(), object.getState().isVisible(),
                    object.getState().isFlippedHorizontally(), boxCopy, object.getLayerIndex(),
                    object.getVisionOuterRadius(), object.getVisionInnerRadius(),
                    object.isVisionEnabled(), object.isVisionOwnLightEnabled(),
                    object.getState().getTintColorRgb(), object.getOwnerId(), visionSource);
        }

        private VttSceneObject toSceneObject() {
            VttSceneObject object = new VttSceneObject(id, displayName, sourceTokenDefinitionId);
            object.setSourceAttachmentDefinitionId(sourceAttachmentDefinitionId);
            object.setAttachmentBinding(attachmentBinding == null
                    ? null : attachmentBinding.restore());
            object.setTransform(new VttSceneTransform(
                    x, y, scaleX, scaleY, rotationDegrees));
            object.setSize(new VttSceneSize(width, height));
            object.setState(new VttSceneState(activeStateId, visible, flipped));
            object.setLayerIndex(layerIndex);
            object.setVisionOuterRadius(visionOuterRadius);
            object.setVisionInnerRadius(visionInnerRadius);
            object.setVisionEnabled(visionEnabled);
            object.setVisionOwnLightEnabled(visionOwnLightEnabled);
            object.getState().setTintColorRgb(tintColorRgb);
            object.setOwnerId(ownerId);
            if (collisionBox != null) {
                object.setCollisionBox(new VttSceneCollisionBox(
                        collisionBox.offsetX(), collisionBox.offsetY(),
                        collisionBox.width(), collisionBox.height()));
            }
            return object;
        }
    }

    private record AttachmentBinding(
            String targetObjectId,
            boolean followPosition,
            boolean followRotation,
            boolean followScale,
            boolean flipOffset,
            double offsetX,
            double offsetY,
            double rotationOffsetDegrees,
            double scaleMultiplierX,
            double scaleMultiplierY
    ) {
        private static AttachmentBinding capture(
                com.petrick.vtt.feature.tabletop.VttAttachmentBinding binding
        ) {
            if (binding == null) return null;
            return new AttachmentBinding(binding.getTargetObjectId(),
                    binding.isFollowPosition(), binding.isFollowRotation(),
                    binding.isFollowScale(), binding.isFlipOffset(),
                    binding.getOffsetX(), binding.getOffsetY(),
                    binding.getRotationOffsetDegrees(), binding.getScaleMultiplierX(),
                    binding.getScaleMultiplierY());
        }

        private com.petrick.vtt.feature.tabletop.VttAttachmentBinding restore() {
            var binding = new com.petrick.vtt.feature.tabletop.VttAttachmentBinding();
            binding.setTargetObjectId(targetObjectId);
            binding.setFollowPosition(followPosition);
            binding.setFollowRotation(followRotation);
            binding.setFollowScale(followScale);
            binding.setFlipOffset(flipOffset);
            binding.setOffsetX(offsetX);
            binding.setOffsetY(offsetY);
            binding.setRotationOffsetDegrees(rotationOffsetDegrees);
            binding.setScaleMultiplierX(scaleMultiplierX);
            binding.setScaleMultiplierY(scaleMultiplierY);
            return binding;
        }
    }

    private record CollisionBox(double offsetX, double offsetY, double width, double height) {
    }
}
