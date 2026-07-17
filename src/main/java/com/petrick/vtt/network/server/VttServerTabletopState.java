package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttTabletop;
import com.petrick.vtt.feature.tabletop.persistence.TabletopStorage;
import com.petrick.vtt.feature.tabletop.persistence.TabletopStoragePaths;
import com.petrick.vtt.network.payload.VttSceneSnapshotPayload;
import net.neoforged.fml.loading.FMLPaths;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.network.payload.VttTokenTransformRequestPayload;
import com.petrick.vtt.network.payload.VttTokenTransformUpdatePayload;
import com.google.gson.reflect.TypeToken;
import com.petrick.vtt.feature.tabletop.VttDoor;
import com.petrick.vtt.feature.tabletop.VttFogOfWar;
import com.petrick.vtt.feature.tabletop.VttWall;
import com.petrick.vtt.feature.tabletop.SceneMovementCollision;
import com.petrick.vtt.feature.tabletop.VttSceneCollisionBox;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.network.payload.VttEnvironmentStateRequestPayload;
import com.petrick.vtt.network.payload.VttEnvironmentStateUpdatePayload;
import com.petrick.vtt.network.payload.VttTokenLifecycleRequestPayload;
import com.petrick.vtt.network.payload.VttTokenLifecycleUpdatePayload;

import java.lang.reflect.Type;
import java.util.List;

public final class VttServerTabletopState {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type DOOR_LIST_TYPE = new TypeToken<List<VttDoor>>() {}.getType();
    private static final Type WALL_LIST_TYPE = new TypeToken<List<VttWall>>() {}.getType();
    private static final Type VISION_LIST_TYPE = new TypeToken<List<VisionState>>() {}.getType();
    private static VttServerTabletopState instance;

    private final VttTabletop tabletop;
    private final VttScene activeScene;
    private final TabletopStorage storage;
    private final SceneMovementCollision movementCollision = new SceneMovementCollision();

    private VttServerTabletopState() {
        TabletopStoragePaths paths = new TabletopStoragePaths(FMLPaths.GAMEDIR.get());
        paths.ensureBaseFoldersExist();
        paths.ensureTabletopFoldersExist("default");

        this.storage = new TabletopStorage(paths);
        this.tabletop = storage.loadOrCreateDefaultTabletop();
        this.activeScene = storage.loadOrCreateActiveScene(tabletop);
    }

    public static synchronized VttServerTabletopState get() {
        if (instance == null) {
            instance = new VttServerTabletopState();
        }
        return instance;
    }

    public VttSceneSnapshotPayload createSnapshotPayload() {
        return new VttSceneSnapshotPayload(GSON.toJson(tabletop), GSON.toJson(activeScene));
    }

    public VttScene activeScene() {
        return activeScene;
    }

    public synchronized void updateTokenDefinitionOwnership(String definitionId, String ownerId) {
        if (definitionId == null || definitionId.isBlank()) return;
        activeScene.getObjects().stream()
                .filter(object -> object != null && definitionId.equals(object.getSourceTokenDefinitionId()))
                .forEach(object -> object.setOwnerId(ownerId));
        storage.saveScene(tabletop.getId(), activeScene);
    }

    public synchronized VttTokenTransformUpdatePayload applyTokenTransform(
            VttTokenTransformRequestPayload request, String playerId, boolean master
    ) {
        if (request == null || playerId == null || !valid(request)) return null;
        VttSceneObject object = activeScene.getObjects().stream()
                .filter(candidate -> candidate != null && request.objectId().equals(candidate.getId()))
                .findFirst().orElse(null);
        if (object == null || (!master && !playerId.equals(object.getOwnerId()))) return null;

        Vec2d currentPosition = new Vec2d(object.getTransform().getX(), object.getTransform().getY());
        Vec2d requestedDelta = new Vec2d(request.x(), request.y()).subtract(currentPosition);
        boolean bypassCollision = master && request.bypassCollision();
        Vec2d allowedDelta;
        if (!bypassCollision && requestedDelta.lengthSquared() > 4096.0 * 4096.0) {
            allowedDelta = Vec2d.ZERO;
        } else {
            allowedDelta = bypassCollision ? requestedDelta
                    : movementCollision.clipSceneObjectMovement(activeScene, object, requestedDelta);
        }
        boolean movementAccepted = allowedDelta.subtract(requestedDelta).lengthSquared() <= 0.0000001;
        boolean masterFieldsAccepted = master
                || (nearlyEqual(request.scaleX(), object.getTransform().getScaleX())
                && nearlyEqual(request.scaleY(), object.getTransform().getScaleY())
                && request.layerIndex() == currentLayerIndex(object));
        Vec2d acceptedPosition = currentPosition.add(allowedDelta);
        object.getTransform().setX(acceptedPosition.x());
        object.getTransform().setY(acceptedPosition.y());
        object.getTransform().setRotationDegrees(normalizeRotation(request.rotationDegrees()));
        if (master) {
            object.getTransform().setScaleX(request.scaleX());
            object.getTransform().setScaleY(request.scaleY());
            moveObjectToLayer(object, request.layerIndex());
        }
        object.getState().setFlippedHorizontally(request.flippedHorizontally());
        object.getState().setActiveStateId(request.activeStateId());
        storage.saveScene(tabletop.getId(), activeScene);

        return new VttTokenTransformUpdatePayload(object.getId(), object.getTransform().getX(),
                object.getTransform().getY(), object.getTransform().getRotationDegrees(),
                object.getTransform().getScaleX(), object.getTransform().getScaleY(), currentLayerIndex(object),
                object.getState().isFlippedHorizontally(), object.getState().getActiveStateId(),
                playerId, movementAccepted && masterFieldsAccepted);
    }

    public synchronized VttTokenTransformUpdatePayload currentTokenTransform(String objectId, String playerId) {
        if (objectId == null || objectId.isBlank()) return null;
        VttSceneObject object = activeScene.getObjects().stream()
                .filter(candidate -> candidate != null && objectId.equals(candidate.getId()))
                .findFirst().orElse(null);
        if (object == null) return null;
        return new VttTokenTransformUpdatePayload(object.getId(), object.getTransform().getX(),
                object.getTransform().getY(), object.getTransform().getRotationDegrees(),
                object.getTransform().getScaleX(), object.getTransform().getScaleY(), currentLayerIndex(object),
                object.getState().isFlippedHorizontally(), object.getState().getActiveStateId(), playerId, false);
    }

    public synchronized VttTokenLifecycleUpdatePayload applyTokenLifecycle(
            VttTokenLifecycleRequestPayload request, String playerId, boolean master
    ) {
        if (!master || request == null || playerId == null || request.operation() == null
                || request.objectId() == null || request.objectId().isBlank()) return null;
        if ("CREATE".equals(request.operation())) return createSceneToken(request, playerId);
        if ("DELETE".equals(request.operation())) return deleteSceneToken(request.objectId(), playerId);
        return null;
    }

    private VttTokenLifecycleUpdatePayload createSceneToken(
            VttTokenLifecycleRequestPayload request, String playerId
    ) {
        if (request.objectJson() == null || request.objectJson().length() > 1_000_000) return null;
        try {
            VttSceneObject object = GSON.fromJson(request.objectJson(), VttSceneObject.class);
            if (!validSceneObject(object)) return null;
            String requestedId = normalizeObjectId(request.objectId());
            String authoritativeId = createUniqueObjectId(requestedId);
            object.setId(authoritativeId);
            int layerIndex = Math.max(0, Math.min(object.getLayerIndex(), activeScene.getObjects().size()));
            activeScene.getObjects().add(layerIndex, object);
            normalizeLayerIndices();
            storage.saveScene(tabletop.getId(), activeScene);
            return new VttTokenLifecycleUpdatePayload("CREATE", request.objectId(), authoritativeId,
                    GSON.toJson(object), playerId);
        } catch (RuntimeException exception) {
            VTT.LOGGER.warn("Could not decode VTT token creation", exception);
            return null;
        }
    }

    private VttTokenLifecycleUpdatePayload deleteSceneToken(String objectId, String playerId) {
        VttSceneObject object = activeScene.getObjects().stream()
                .filter(candidate -> candidate != null && objectId.equals(candidate.getId()))
                .findFirst().orElse(null);
        if (object == null) return null;
        activeScene.removeObject(objectId);
        activeScene.removeVisionSourceObjectId(objectId);
        normalizeLayerIndices();
        storage.saveScene(tabletop.getId(), activeScene);
        return new VttTokenLifecycleUpdatePayload("DELETE", objectId, objectId, "", playerId);
    }

    private boolean validSceneObject(VttSceneObject object) {
        if (object == null || object.getDisplayName() == null || object.getDisplayName().isBlank()
                || object.getSourceTokenDefinitionId() == null || object.getSourceTokenDefinitionId().isBlank()
                || object.getTransform() == null || object.getSize() == null || object.getState() == null
                || object.getState().getActiveStateId() == null || object.getState().getActiveStateId().isBlank()) {
            return false;
        }
        return Double.isFinite(object.getTransform().getX()) && Double.isFinite(object.getTransform().getY())
                && Math.abs(object.getTransform().getX()) <= 10_000_000.0
                && Math.abs(object.getTransform().getY()) <= 10_000_000.0
                && Double.isFinite(object.getTransform().getRotationDegrees())
                && Double.isFinite(object.getTransform().getScaleX())
                && Double.isFinite(object.getTransform().getScaleY())
                && object.getTransform().getScaleX() >= 0.01 && object.getTransform().getScaleX() <= 1_000.0
                && object.getTransform().getScaleY() >= 0.01 && object.getTransform().getScaleY() <= 1_000.0
                && Double.isFinite(object.getSize().getWidth()) && Double.isFinite(object.getSize().getHeight())
                && object.getSize().getWidth() > 0.0 && object.getSize().getWidth() <= 1_000_000.0
                && object.getSize().getHeight() > 0.0 && object.getSize().getHeight() <= 1_000_000.0
                && validCollisionBox(object.getCollisionBox(), true);
    }

    private String normalizeObjectId(String objectId) {
        String normalized = objectId.trim().toLowerCase().replaceAll("[^a-z0-9/_-]", "_");
        return normalized.isBlank() ? "token" : normalized.substring(0, Math.min(96, normalized.length()));
    }

    private String createUniqueObjectId(String requestedId) {
        if (activeScene.getObjects().stream().noneMatch(
                object -> object != null && requestedId.equals(object.getId()))) return requestedId;
        int suffix = 2;
        while (suffix < 1_000_000) {
            String candidate = requestedId + "_" + suffix++;
            boolean exists = activeScene.getObjects().stream().anyMatch(
                    object -> object != null && candidate.equals(object.getId()));
            if (!exists) return candidate;
        }
        return requestedId + "_" + System.nanoTime();
    }

    private void normalizeLayerIndices() {
        for (int index = 0; index < activeScene.getObjects().size(); index++) {
            VttSceneObject object = activeScene.getObjects().get(index);
            if (object != null) object.setLayerIndex(index);
        }
    }

    public synchronized VttEnvironmentStateUpdatePayload applyEnvironmentState(
            VttEnvironmentStateRequestPayload request) {
        if (request == null || request.wallsJson() == null || request.doorsJson() == null
                || request.fogJson() == null || request.visionJson() == null
                || request.wallsJson().length() > 1_000_000 || request.doorsJson().length() > 1_000_000
                || request.fogJson().length() > 1_000_000 || request.visionJson().length() > 1_000_000) return null;
        try {
            List<VttWall> walls = GSON.fromJson(request.wallsJson(), WALL_LIST_TYPE);
            List<VttDoor> doors = GSON.fromJson(request.doorsJson(), DOOR_LIST_TYPE);
            VttFogOfWar fog = GSON.fromJson(request.fogJson(), VttFogOfWar.class);
            List<VisionState> visionStates = GSON.fromJson(request.visionJson(), VISION_LIST_TYPE);
            if (walls == null || doors == null || fog == null || visionStates == null
                    || walls.size() > 10_000 || doors.size() > 10_000 || visionStates.size() > 10_000
                    || fog.getHiddenAreas().size() + fog.getRevealedAreas().size() > 10_000) return null;
            activeScene.clearWalls();
            walls.stream().filter(wall -> wall != null && wall.getId() != null && !wall.getId().isBlank())
                    .forEach(activeScene::addWall);
            activeScene.clearDoors();
            doors.stream().filter(door -> door != null && door.getId() != null && !door.getId().isBlank())
                    .forEach(activeScene::addDoor);
            activeScene.setFogOfWar(fog);
            for (VisionState vision : visionStates) {
                if (vision == null || vision.objectId() == null || !Double.isFinite(vision.innerRadius())
                        || !Double.isFinite(vision.outerRadius())) continue;
                activeScene.getObjects().stream()
                        .filter(object -> object != null && vision.objectId().equals(object.getId()))
                        .findFirst().ifPresent(object -> {
                            double outer = Math.max(64.0, Math.min(100_000.0, vision.outerRadius()));
                            object.setVisionOuterRadius(outer);
                            object.setVisionInnerRadius(Math.min(outer, Math.max(0.0, vision.innerRadius())));
                            object.setVisionEnabled(vision.enabled());
                            if (validCollisionBox(vision.collisionBox(), false)) {
                                object.setCollisionBox(vision.collisionBox());
                            }
                        });
            }
            storage.saveScene(tabletop.getId(), activeScene);
            return currentEnvironmentState();
        } catch (RuntimeException exception) {
            VTT.LOGGER.warn("Could not decode VTT environment update", exception);
            return null;
        }
    }

    public synchronized VttEnvironmentStateUpdatePayload currentEnvironmentState() {
        return new VttEnvironmentStateUpdatePayload(
                GSON.toJson(activeScene.getWalls()), GSON.toJson(activeScene.getDoors()),
                GSON.toJson(activeScene.getFogOfWar()),
                GSON.toJson(activeScene.getObjects().stream()
                        .filter(object -> object != null && object.getId() != null)
                        .map(object -> new VisionState(object.getId(), object.getVisionInnerRadius(),
                                object.getVisionOuterRadius() > 0.0
                                        ? object.getVisionOuterRadius() : 512.0,
                                object.isVisionEnabled(), object.getCollisionBox())).toList()));
    }

    private boolean validCollisionBox(VttSceneCollisionBox box, boolean allowNull) {
        if (box == null) return allowNull;
        return Double.isFinite(box.getOffsetX()) && Double.isFinite(box.getOffsetY())
                && Double.isFinite(box.getWidth()) && Double.isFinite(box.getHeight())
                && Math.abs(box.getOffsetX()) <= 1_000_000.0
                && Math.abs(box.getOffsetY()) <= 1_000_000.0
                && box.getWidth() >= 1.0 && box.getWidth() <= 1_000_000.0
                && box.getHeight() >= 1.0 && box.getHeight() <= 1_000_000.0;
    }

    private record VisionState(String objectId, double innerRadius, double outerRadius, boolean enabled,
                               VttSceneCollisionBox collisionBox) {}

    private boolean valid(VttTokenTransformRequestPayload request) {
        return request.objectId() != null && !request.objectId().isBlank()
                && request.activeStateId() != null && !request.activeStateId().isBlank()
                && Double.isFinite(request.x()) && Double.isFinite(request.y())
                && Double.isFinite(request.rotationDegrees())
                && Double.isFinite(request.scaleX()) && Double.isFinite(request.scaleY())
                && request.scaleX() >= 0.01 && request.scaleX() <= 1_000.0
                && request.scaleY() >= 0.01 && request.scaleY() <= 1_000.0
                && request.layerIndex() >= 0 && request.layerIndex() <= 100_000
                && Math.abs(request.x()) <= 10_000_000.0 && Math.abs(request.y()) <= 10_000_000.0;
    }

    private int currentLayerIndex(VttSceneObject object) {
        int index = activeScene.getObjects().indexOf(object);
        return index >= 0 ? index : object.getLayerIndex();
    }

    private void moveObjectToLayer(VttSceneObject object, int requestedLayerIndex) {
        int currentIndex = activeScene.getObjects().indexOf(object);
        if (currentIndex < 0 || activeScene.getObjects().isEmpty()) return;
        int targetIndex = Math.max(0, Math.min(requestedLayerIndex, activeScene.getObjects().size() - 1));
        if (currentIndex != targetIndex) {
            activeScene.getObjects().remove(currentIndex);
            activeScene.getObjects().add(targetIndex, object);
        }
        for (int index = 0; index < activeScene.getObjects().size(); index++) {
            VttSceneObject sceneObject = activeScene.getObjects().get(index);
            if (sceneObject != null) sceneObject.setLayerIndex(index);
        }
    }

    private boolean nearlyEqual(double first, double second) {
        return Math.abs(first - second) <= 0.0000001;
    }

    private double normalizeRotation(double degrees) {
        double normalized = degrees % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }
}
