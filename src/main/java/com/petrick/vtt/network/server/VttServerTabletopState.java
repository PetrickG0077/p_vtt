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
import com.petrick.vtt.feature.asset.DebugAssets;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.network.payload.VttEnvironmentStateRequestPayload;
import com.petrick.vtt.network.payload.VttEnvironmentStateUpdatePayload;
import com.petrick.vtt.network.payload.VttEnvironmentCommandPayload;
import com.petrick.vtt.network.payload.VttEnvironmentCommandUpdatePayload;
import com.petrick.vtt.feature.tabletop.VttFogArea;
import com.petrick.vtt.network.payload.VttTokenLifecycleRequestPayload;
import com.petrick.vtt.network.payload.VttTokenLifecycleUpdatePayload;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public final class VttServerTabletopState {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type DOOR_LIST_TYPE = new TypeToken<List<VttDoor>>() {}.getType();
    private static final Type WALL_LIST_TYPE = new TypeToken<List<VttWall>>() {}.getType();
    private static final Type VISION_LIST_TYPE = new TypeToken<List<VisionState>>() {}.getType();
    private static VttServerTabletopState instance;

    private final VttTabletop tabletop;
    private VttScene activeScene;
    private final TabletopStorage storage;
    private final SceneMovementCollision movementCollision = new SceneMovementCollision();
    private long authorityRevision = 1L;
    private final Map<String, Long> tokenTransformRevisions = new HashMap<>();
    private final Map<String, Long> lastTokenTransformSequences = new HashMap<>();
    private final Map<String, Long> environmentRevisions = new HashMap<>();
    private final Map<String, Long> lastEnvironmentSequences = new HashMap<>();

    private VttServerTabletopState() {
        TabletopStoragePaths paths = new TabletopStoragePaths(FMLPaths.GAMEDIR.get());
        paths.ensureBaseFoldersExist();
        paths.ensureTabletopFoldersExist("default");

        this.storage = new TabletopStorage(paths);
        this.tabletop = storage.loadOrCreateDefaultTabletop();
        this.activeScene = storage.loadOrCreateActiveScene(tabletop);
        this.tabletop.setSceneDisplayName(activeScene.getId(), activeScene.getDisplayName());
        storage.saveTabletop(tabletop);
    }

    public static synchronized VttServerTabletopState get() {
        if (instance == null) {
            instance = new VttServerTabletopState();
        }
        return instance;
    }

    public VttSceneSnapshotPayload createSnapshotPayload() {
        return new VttSceneSnapshotPayload(
                GSON.toJson(tabletop), GSON.toJson(activeScene), authorityRevision);
    }

    public VttScene activeScene() {
        return activeScene;
    }

    public synchronized long authorityRevision() {
        return authorityRevision;
    }

    public synchronized boolean switchToScene(String sceneId) {
        if (sceneId == null || sceneId.isBlank()) return false;
        if (sceneId.equals(tabletop.getActiveSceneId())) return true;
        if (!tabletop.getSceneIds().contains(sceneId)) return false;
        VttScene target = storage.loadScene(tabletop.getId(), sceneId);
        if (target == null) return false;
        storage.saveScene(tabletop.getId(), activeScene);
        activeScene = target;
        advanceAuthorityRevision();
        tabletop.setActiveSceneId(sceneId);
        storage.saveTabletop(tabletop);
        return true;
    }

    public synchronized boolean createAndActivateScene(String displayName) {
        if (displayName == null || displayName.isBlank() || displayName.length() > 48
                || tabletop.getSceneIds().size() >= 1_000) return false;
        String trimmedName = displayName.trim();
        String baseId = trimmedName.toLowerCase()
                .replaceAll("[^a-z0-9_-]", "_").replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (baseId.isBlank()) baseId = "new_scene";
        if (baseId.length() > 64) baseId = baseId.substring(0, 64);
        String sceneId = baseId;
        int suffix = 2;
        while (tabletop.getSceneIds().contains(sceneId)) sceneId = baseId + "_" + suffix++;

        storage.saveScene(tabletop.getId(), activeScene);
        VttScene created = new VttScene(sceneId, trimmedName);
        storage.saveScene(tabletop.getId(), created);
        tabletop.addSceneId(sceneId);
        tabletop.setSceneDisplayName(sceneId, trimmedName);
        tabletop.setActiveSceneId(sceneId);
        activeScene = created;
        advanceAuthorityRevision();
        storage.saveTabletop(tabletop);
        return true;
    }

    public synchronized boolean renameScene(String sceneId, String displayName) {
        if (sceneId == null || sceneId.isBlank() || !tabletop.getSceneIds().contains(sceneId)
                || displayName == null || displayName.isBlank() || displayName.length() > 48) return false;
        String trimmedName = displayName.trim();
        VttScene target = sceneId.equals(activeScene.getId())
                ? activeScene : storage.loadScene(tabletop.getId(), sceneId);
        if (target == null) return false;
        target.setDisplayName(trimmedName);
        tabletop.setSceneDisplayName(sceneId, trimmedName);
        storage.saveScene(tabletop.getId(), target);
        storage.saveTabletop(tabletop);
        return true;
    }

    public synchronized boolean deleteScene(String sceneId) {
        if (sceneId == null || sceneId.isBlank() || tabletop.getSceneIds().size() <= 1
                || !tabletop.getSceneIds().contains(sceneId)) return false;
        boolean deletingActive = sceneId.equals(activeScene.getId());
        VttScene replacement = null;
        if (deletingActive) {
            String replacementId = tabletop.getSceneIds().stream()
                    .filter(id -> !sceneId.equals(id)).findFirst().orElse(null);
            if (replacementId == null) return false;
            replacement = storage.loadScene(tabletop.getId(), replacementId);
            if (replacement == null) return false;
        }
        if (!storage.deleteScene(tabletop.getId(), sceneId)) return false;
        tabletop.removeSceneId(sceneId);
        if (deletingActive) {
            activeScene = replacement;
            advanceAuthorityRevision();
            tabletop.setActiveSceneId(replacement.getId());
            tabletop.setSceneDisplayName(replacement.getId(), replacement.getDisplayName());
        }
        storage.saveTabletop(tabletop);
        return true;
    }

    private void advanceAuthorityRevision() {
        authorityRevision = authorityRevision == Long.MAX_VALUE ? 1L : authorityRevision + 1L;
        tokenTransformRevisions.clear();
        lastTokenTransformSequences.clear();
        environmentRevisions.clear();
        lastEnvironmentSequences.clear();
    }

    public synchronized boolean setActiveSceneBackground(String assetId) {
        if (activeScene == null || assetId == null || assetId.length() > 512) return false;
        if (assetId.isBlank()) {
            activeScene.setBackgroundAssetId(null);
            storage.saveScene(tabletop.getId(), activeScene);
            return true;
        }
        String validatedId = validateBackgroundAssetId(assetId.trim());
        if (validatedId == null) return false;
        activeScene.setBackgroundAssetId(validatedId);
        storage.saveScene(tabletop.getId(), activeScene);
        return true;
    }

    private String validateBackgroundAssetId(String assetId) {
        if (assetId.startsWith("registered:")) {
            String builtInId = assetId.substring("registered:".length());
            return DebugAssets.TEST_TOKEN_ID.equals(builtInId) ? assetId : null;
        }
        if (!assetId.startsWith("library:")) return null;
        String relative = assetId.substring("library:".length()).replace('\\', '/');
        if (relative.isBlank() || relative.startsWith("/") || relative.contains("../")) return null;
        Path root = FMLPaths.GAMEDIR.get().resolve("config/vtt_assets/assets")
                .toAbsolutePath().normalize();
        Path file = root.resolve(relative).toAbsolutePath().normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) return null;
        String lowerName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(lowerName.endsWith(".png") || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg") || lowerName.endsWith(".webp"))) return null;
        return "library:" + root.relativize(file).toString().replace('\\', '/');
    }

    public synchronized void updateTokenDefinitionOwnership(String definitionId, String ownerId) {
        if (definitionId == null || definitionId.isBlank()) return;
        activeScene.getObjects().stream()
                .filter(object -> object != null && definitionId.equals(object.getSourceTokenDefinitionId()))
                .forEach(object -> object.setOwnerId(ownerId));
        storage.saveScene(tabletop.getId(), activeScene);
    }

    public synchronized int removeObjectsUsingTokenDefinition(String definitionId) {
        if (definitionId == null || definitionId.isBlank()) return 0;
        int removedCount = 0;
        for (String sceneId : List.copyOf(tabletop.getSceneIds())) {
            VttScene scene = activeScene != null && sceneId.equals(activeScene.getId())
                    ? activeScene : storage.loadScene(tabletop.getId(), sceneId);
            if (scene == null) continue;

            List<String> removedIds = scene.getObjects().stream()
                    .filter(object -> object != null
                            && definitionId.equals(object.getSourceTokenDefinitionId()))
                    .map(VttSceneObject::getId)
                    .toList();
            if (removedIds.isEmpty()) continue;

            scene.getObjects().removeIf(object -> object != null
                    && definitionId.equals(object.getSourceTokenDefinitionId()));
            removedIds.forEach(scene::removeVisionSourceObjectId);
            normalizeLayerIndices(scene);
            storage.saveScene(tabletop.getId(), scene);
            removedCount += removedIds.size();
        }
        return removedCount;
    }

    public synchronized VttTokenTransformUpdatePayload applyTokenTransform(
            VttTokenTransformRequestPayload request, String playerId, boolean master
    ) {
        if (request == null || playerId == null || activeScene == null
                || request.authorityRevision() != authorityRevision
                || !activeScene.getId().equals(request.sceneId()) || !valid(request)) return null;
        String sequenceKey = playerId + "\u0000" + request.objectId();
        if (request.clientSequence() <= lastTokenTransformSequences.getOrDefault(sequenceKey, 0L)) return null;
        VttSceneObject object = activeScene.getObjects().stream()
                .filter(candidate -> candidate != null && request.objectId().equals(candidate.getId()))
                .findFirst().orElse(null);
        if (object == null || (!master && !playerId.equals(object.getOwnerId()))) return null;
        lastTokenTransformSequences.put(sequenceKey, request.clientSequence());

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
                && request.layerIndex() == currentLayerIndex(object)
                && request.visible() == object.getState().isVisible());
        Vec2d acceptedPosition = currentPosition.add(allowedDelta);
        object.getTransform().setX(acceptedPosition.x());
        object.getTransform().setY(acceptedPosition.y());
        object.getTransform().setRotationDegrees(normalizeRotation(request.rotationDegrees()));
        if (master) {
            object.getTransform().setScaleX(request.scaleX());
            object.getTransform().setScaleY(request.scaleY());
            moveObjectToLayer(object, request.layerIndex());
            object.getState().setVisible(request.visible());
        }
        object.getState().setFlippedHorizontally(request.flippedHorizontally());
        object.getState().setActiveStateId(request.activeStateId());
        storage.saveScene(tabletop.getId(), activeScene);

        long entityRevision = nextRevision(tokenTransformRevisions, object.getId());
        return new VttTokenTransformUpdatePayload(authorityRevision, entityRevision, request.clientSequence(),
                activeScene.getId(), object.getId(), object.getTransform().getX(),
                object.getTransform().getY(), object.getTransform().getRotationDegrees(),
                object.getTransform().getScaleX(), object.getTransform().getScaleY(), currentLayerIndex(object),
                object.getState().isFlippedHorizontally(), object.getState().isVisible(),
                object.getState().getActiveStateId(),
                playerId, movementAccepted && masterFieldsAccepted);
    }

    public synchronized VttTokenTransformUpdatePayload currentTokenTransform(
            String sceneId, String objectId, String playerId, long clientSequence) {
        if (sceneId == null || activeScene == null || !sceneId.equals(activeScene.getId())
                || objectId == null || objectId.isBlank()) return null;
        VttSceneObject object = activeScene.getObjects().stream()
                .filter(candidate -> candidate != null && objectId.equals(candidate.getId()))
                .findFirst().orElse(null);
        if (object == null) return null;
        return new VttTokenTransformUpdatePayload(authorityRevision,
                tokenTransformRevisions.getOrDefault(objectId, 0L), clientSequence,
                activeScene.getId(), object.getId(), object.getTransform().getX(),
                object.getTransform().getY(), object.getTransform().getRotationDegrees(),
                object.getTransform().getScaleX(), object.getTransform().getScaleY(), currentLayerIndex(object),
                object.getState().isFlippedHorizontally(), object.getState().isVisible(),
                object.getState().getActiveStateId(), playerId, false);
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
        normalizeLayerIndices(activeScene);
    }

    private void normalizeLayerIndices(VttScene scene) {
        for (int index = 0; index < scene.getObjects().size(); index++) {
            VttSceneObject object = scene.getObjects().get(index);
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

    public synchronized VttEnvironmentCommandUpdatePayload applyEnvironmentCommand(
            VttEnvironmentCommandPayload command, String playerId) {
        if (playerId == null || !validEnvironmentCommand(command)) return null;
        String revisionKey = command.entityType() + "\u0000" + command.entityId();
        String sequenceKey = playerId + "\u0000" + revisionKey;
        if (command.clientSequence() <= lastEnvironmentSequences.getOrDefault(sequenceKey, 0L)) return null;
        boolean delete = VttEnvironmentCommandPayload.DELETE.equals(command.operation());
        try {
            String confirmedJson = command.entityJson();
            boolean changed = switch (command.entityType()) {
                case VttEnvironmentCommandPayload.WALL -> delete
                        ? activeScene.removeWall(command.entityId())
                        : upsertWall(command.entityId(), command.entityJson());
                case VttEnvironmentCommandPayload.DOOR -> delete
                        ? activeScene.removeDoor(command.entityId())
                        : upsertDoor(command.entityId(), command.entityJson());
                case VttEnvironmentCommandPayload.FOG_HIDDEN -> delete
                        ? activeScene.getFogOfWar().removeArea(command.entityId())
                        : upsertFogArea(command.entityId(), command.entityJson(), false);
                case VttEnvironmentCommandPayload.FOG_REVEALED -> delete
                        ? activeScene.getFogOfWar().removeArea(command.entityId())
                        : upsertFogArea(command.entityId(), command.entityJson(), true);
                case VttEnvironmentCommandPayload.FOG_CONFIG -> !delete
                        && applyFogConfig(command.entityJson());
                case VttEnvironmentCommandPayload.VISION -> delete
                        || applyVisionState(command.entityId(), command.entityJson());
                default -> false;
            };
            if (!changed) return null;
            lastEnvironmentSequences.put(sequenceKey, command.clientSequence());

            if (!delete) confirmedJson = authoritativeEnvironmentJson(
                    command.entityType(), command.entityId());
            storage.saveScene(tabletop.getId(), activeScene);
            long entityRevision = nextRevision(environmentRevisions, revisionKey);
            return new VttEnvironmentCommandUpdatePayload(
                    authorityRevision, entityRevision, command.clientSequence(),
                    command.operation(), command.sceneId(), command.entityType(),
                    command.entityId(), delete ? "" : confirmedJson, playerId);
        } catch (RuntimeException exception) {
            VTT.LOGGER.warn("Could not apply granular VTT environment command", exception);
            return null;
        }
    }

    private boolean validEnvironmentCommand(VttEnvironmentCommandPayload command) {
        if (command == null || command.authorityRevision() != authorityRevision
                || command.clientSequence() <= 0L
                || command.operation() == null || command.sceneId() == null
                || activeScene == null || !command.sceneId().equals(activeScene.getId())
                || command.entityType() == null
                || command.entityId() == null || command.entityId().isBlank()
                || command.entityId().length() > 128 || command.entityJson() == null
                || command.entityJson().length() > VttEnvironmentCommandPayload.MAX_JSON_LENGTH) return false;
        return VttEnvironmentCommandPayload.UPSERT.equals(command.operation())
                || VttEnvironmentCommandPayload.DELETE.equals(command.operation());
    }

    private boolean upsertWall(String id, String json) {
        VttWall wall = GSON.fromJson(json, VttWall.class);
        boolean exists = activeScene.getWalls().stream().anyMatch(
                value -> value != null && id.equals(value.getId()));
        if (wall == null || !id.equals(wall.getId()) || !validGeometry(
                wall.getTransform(), wall.getSize()) || activeScene.getWalls().size() >= 10_000 && !exists) return false;
        activeScene.getWalls().removeIf(value -> value != null && id.equals(value.getId()));
        activeScene.addWall(wall);
        return true;
    }

    private boolean upsertDoor(String id, String json) {
        VttDoor door = GSON.fromJson(json, VttDoor.class);
        boolean exists = activeScene.getDoors().stream().anyMatch(
                value -> value != null && id.equals(value.getId()));
        if (door == null || !id.equals(door.getId()) || !validGeometry(
                door.getTransform(), door.getSize()) || activeScene.getDoors().size() >= 10_000 && !exists) return false;
        if (door.getWallId() != null && activeScene.getWalls().stream().noneMatch(
                wall -> wall != null && door.getWallId().equals(wall.getId()))) return false;
        activeScene.getDoors().removeIf(value -> value != null && id.equals(value.getId()));
        activeScene.addDoor(door);
        return true;
    }

    private boolean upsertFogArea(String id, String json, boolean revealed) {
        VttFogArea area = GSON.fromJson(json, VttFogArea.class);
        int areaCount = activeScene.getFogOfWar().getHiddenAreas().size()
                + activeScene.getFogOfWar().getRevealedAreas().size();
        boolean exists = activeScene.getFogOfWar().getHiddenAreas().stream().anyMatch(
                value -> value != null && id.equals(value.getId()))
                || activeScene.getFogOfWar().getRevealedAreas().stream().anyMatch(
                value -> value != null && id.equals(value.getId()));
        if (area == null || !id.equals(area.getId()) || !validGeometry(
                area.getTransform(), area.getSize()) || areaCount >= 10_000 && !exists) return false;
        activeScene.getFogOfWar().removeArea(id);
        if (revealed) activeScene.getFogOfWar().addRevealedArea(area);
        else activeScene.getFogOfWar().addHiddenArea(area);
        return true;
    }

    private boolean applyFogConfig(String json) {
        FogConfig config = GSON.fromJson(json, FogConfig.class);
        if (config == null) return false;
        activeScene.getFogOfWar().setEnabled(config.enabled());
        activeScene.getFogOfWar().setDefaultHidden(config.defaultHidden());
        return true;
    }

    private boolean applyVisionState(String id, String json) {
        VisionState vision = GSON.fromJson(json, VisionState.class);
        if (vision == null || !id.equals(vision.objectId()) || !Double.isFinite(vision.innerRadius())
                || !Double.isFinite(vision.outerRadius()) || vision.innerRadius() < 0.0
                || vision.outerRadius() < 64.0 || vision.outerRadius() > 100_000.0
                || vision.innerRadius() > vision.outerRadius()
                || !validCollisionBox(vision.collisionBox(), true)) return false;
        VttSceneObject object = activeScene.getObjects().stream()
                .filter(value -> value != null && id.equals(value.getId())).findFirst().orElse(null);
        if (object == null) return false;
        object.setVisionInnerRadius(vision.innerRadius());
        object.setVisionOuterRadius(vision.outerRadius());
        object.setVisionEnabled(vision.enabled());
        object.setCollisionBox(vision.collisionBox());
        return true;
    }

    private String authoritativeEnvironmentJson(String type, String id) {
        return switch (type) {
            case VttEnvironmentCommandPayload.WALL -> GSON.toJson(activeScene.getWalls().stream()
                    .filter(value -> value != null && id.equals(value.getId())).findFirst().orElseThrow());
            case VttEnvironmentCommandPayload.DOOR -> GSON.toJson(activeScene.getDoors().stream()
                    .filter(value -> value != null && id.equals(value.getId())).findFirst().orElseThrow());
            case VttEnvironmentCommandPayload.FOG_HIDDEN -> GSON.toJson(
                    activeScene.getFogOfWar().getHiddenAreas().stream()
                            .filter(value -> value != null && id.equals(value.getId())).findFirst().orElseThrow());
            case VttEnvironmentCommandPayload.FOG_REVEALED -> GSON.toJson(
                    activeScene.getFogOfWar().getRevealedAreas().stream()
                            .filter(value -> value != null && id.equals(value.getId())).findFirst().orElseThrow());
            case VttEnvironmentCommandPayload.FOG_CONFIG -> GSON.toJson(new FogConfig(
                    activeScene.getFogOfWar().isEnabled(), activeScene.getFogOfWar().isDefaultHidden()));
            case VttEnvironmentCommandPayload.VISION -> {
                VttSceneObject object = activeScene.getObjects().stream()
                        .filter(value -> value != null && id.equals(value.getId())).findFirst().orElseThrow();
                yield GSON.toJson(new VisionState(id, object.getVisionInnerRadius(),
                        object.getVisionOuterRadius(), object.isVisionEnabled(), object.getCollisionBox()));
            }
            default -> throw new IllegalArgumentException("Unknown environment entity type");
        };
    }

    private boolean validGeometry(com.petrick.vtt.feature.tabletop.VttSceneTransform transform,
                                  com.petrick.vtt.feature.tabletop.VttSceneSize size) {
        return transform != null && size != null && Double.isFinite(transform.getX())
                && Double.isFinite(transform.getY()) && Double.isFinite(transform.getScaleX())
                && Double.isFinite(transform.getScaleY()) && Double.isFinite(transform.getRotationDegrees())
                && Math.abs(transform.getX()) <= 10_000_000.0 && Math.abs(transform.getY()) <= 10_000_000.0
                && transform.getScaleX() >= 0.01 && transform.getScaleX() <= 1_000.0
                && transform.getScaleY() >= 0.01 && transform.getScaleY() <= 1_000.0
                && Double.isFinite(size.getWidth()) && Double.isFinite(size.getHeight())
                && size.getWidth() >= 1.0 && size.getWidth() <= 1_000_000.0
                && size.getHeight() >= 1.0 && size.getHeight() <= 1_000_000.0;
    }

    private record FogConfig(boolean enabled, boolean defaultHidden) {}

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
        return request.authorityRevision() > 0L && request.clientSequence() > 0L
                && request.objectId() != null && !request.objectId().isBlank()
                && request.activeStateId() != null && !request.activeStateId().isBlank()
                && Double.isFinite(request.x()) && Double.isFinite(request.y())
                && Double.isFinite(request.rotationDegrees())
                && Double.isFinite(request.scaleX()) && Double.isFinite(request.scaleY())
                && request.scaleX() >= 0.01 && request.scaleX() <= 1_000.0
                && request.scaleY() >= 0.01 && request.scaleY() <= 1_000.0
                && request.layerIndex() >= 0 && request.layerIndex() <= 100_000
                && Math.abs(request.x()) <= 10_000_000.0 && Math.abs(request.y()) <= 10_000_000.0;
    }

    private long nextRevision(Map<String, Long> revisions, String entityId) {
        long current = revisions.getOrDefault(entityId, 0L);
        long next = current == Long.MAX_VALUE ? 1L : current + 1L;
        revisions.put(entityId, next);
        return next;
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
