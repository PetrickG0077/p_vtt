package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttTabletop;
import com.petrick.vtt.feature.tabletop.VttTabletopPlayerPreferences;
import com.petrick.vtt.feature.tabletop.persistence.TabletopStorage;
import com.petrick.vtt.feature.tabletop.persistence.TabletopStoragePaths;
import com.petrick.vtt.feature.tabletop.persistence.VttSceneDuplicator;
import net.neoforged.fml.loading.FMLPaths;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.feature.tabletop.VttAttachmentBinding;
import com.petrick.vtt.feature.tabletop.VttLight;
import com.petrick.vtt.network.payload.VttTokenTransformRequestPayload;
import com.petrick.vtt.network.payload.VttTokenTransformUpdatePayload;
import com.google.gson.reflect.TypeToken;
import com.petrick.vtt.feature.tabletop.VttDoor;
import com.petrick.vtt.feature.tabletop.VttFogOfWar;
import com.petrick.vtt.feature.tabletop.VttWall;
import com.petrick.vtt.feature.tabletop.SceneMovementCollision;
import com.petrick.vtt.feature.tabletop.VttSceneCollisionBox;
import com.petrick.vtt.feature.tabletop.VttSceneGrid;
import com.petrick.vtt.feature.tabletop.SceneObjectSpatialIndex;
import com.petrick.vtt.feature.tabletop.VttSceneLimits;
import com.petrick.vtt.feature.tabletop.vision.SceneVisionGeometrySpatialIndex;
import com.petrick.vtt.feature.tabletop.vision.VisionSegment;
import com.petrick.vtt.feature.asset.DebugAssets;
import com.petrick.vtt.feature.asset.folder.VttAssetFolderService;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.network.payload.VttEnvironmentStateRequestPayload;
import com.petrick.vtt.network.payload.VttEnvironmentStateUpdatePayload;
import com.petrick.vtt.network.payload.VttEnvironmentCommandPayload;
import com.petrick.vtt.network.payload.VttEnvironmentCommandUpdatePayload;
import com.petrick.vtt.network.payload.VttSceneHistoryCommandPayload;
import com.petrick.vtt.network.VttSceneFingerprint;
import com.petrick.vtt.feature.tabletop.VttFogArea;
import com.petrick.vtt.network.payload.VttTokenLifecycleRequestPayload;
import com.petrick.vtt.network.payload.VttCompositeAttachmentPlacementData;
import com.petrick.vtt.network.payload.VttTokenLifecycleUpdatePayload;
import com.petrick.vtt.network.payload.VttAssetFolderCommandPayload;
import com.petrick.vtt.feature.map.MapTextureMode;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class VttServerTabletopState {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type DOOR_LIST_TYPE = new TypeToken<List<VttDoor>>() {}.getType();
    private static final Type WALL_LIST_TYPE = new TypeToken<List<VttWall>>() {}.getType();
    private static final Type VISION_LIST_TYPE = new TypeToken<List<VisionState>>() {}.getType();
    private static final long SCENE_SAVE_DEBOUNCE_MS = 1_000L;
    private static final long SCENE_SAVE_MAX_DIRTY_MS = 5_000L;
    private static VttServerTabletopState instance;

    private final VttTabletop tabletop;
    private final VttTabletopPlayerPreferences playerPreferences;
    private VttScene activeScene;
    private final TabletopStorage storage;
    private final VttAssetFolderService assetFolderService;
    private final SceneMovementCollision movementCollision = new SceneMovementCollision();
    private final SceneObjectSpatialIndex objectSpatialIndex = new SceneObjectSpatialIndex();
    private final SceneVisionGeometrySpatialIndex visionGeometryIndex =
            new SceneVisionGeometrySpatialIndex();
    private long authorityRevision = 1L;
    private final Map<String, Long> tokenTransformRevisions = new HashMap<>();
    private final Map<String, Long> lastTokenTransformSequences = new HashMap<>();
    private final Map<String, Long> environmentRevisions = new HashMap<>();
    private final Map<String, Long> lastEnvironmentSequences = new HashMap<>();
    private boolean activeSceneDirty;
    private long activeSceneDirtySince;
    private long lastSceneMutationAt;
    private long pendingSceneMutationCount;
    private long completedSceneSaveCount;
    private long lastCompletedSceneSaveAt;
    private boolean presentationBlackout;
    private boolean presentationCameraFollow;
    private double presentationCameraX;
    private double presentationCameraY;
    private double presentationCameraZoom = 1.0;

    private VttServerTabletopState() {
        TabletopStoragePaths paths = new TabletopStoragePaths(FMLPaths.GAMEDIR.get());
        paths.ensureBaseFoldersExist();
        paths.ensureTabletopFoldersExist("default");

        this.storage = new TabletopStorage(paths);
        this.tabletop = storage.loadOrCreateDefaultTabletop();
        this.assetFolderService = new VttAssetFolderService(
                FMLPaths.GAMEDIR.get(), tabletop.getId());
        this.assetFolderService.applyMetadata(tabletop, null, null);
        this.playerPreferences = storage.loadOrCreatePlayerPreferences(tabletop.getId());
        this.activeScene = storage.loadOrCreateActiveScene(tabletop);
        this.objectSpatialIndex.rebuild(activeScene);
        this.visionGeometryIndex.rebuild(activeScene);
        this.movementCollision.rebuildObstacleIndex(activeScene);
        this.tabletop.setSceneDisplayName(activeScene.getId(), activeScene.getDisplayName());
        storage.saveTabletop(tabletop);
    }

    public static synchronized VttServerTabletopState get() {
        if (instance == null) {
            instance = new VttServerTabletopState();
        }
        return instance;
    }

    public static synchronized void tickPersistenceIfInitialized() {
        if (instance != null) instance.tickPersistence();
    }

    public static synchronized void shutdown() {
        if (instance == null) return;
        instance.flushActiveSceneNow("server stopping");
        instance = null;
    }

    public synchronized SceneSnapshotData createSnapshotData(ServerPlayer player) {
        VttScene replicatedScene = replicatedSceneFor(player);
        return new SceneSnapshotData(
                GSON.toJson(tabletop), GSON.toJson(replicatedScene), authorityRevision);
    }

    public record SceneSnapshotData(
            String tabletopJson, String sceneJson, long authorityRevision
    ) {}

    public synchronized VttScene replicatedSceneFor(ServerPlayer player) {
        if (player == null || VttServerPlayerEvents.canViewFullTabletop(player)) return activeScene;
        VttScene copy = GSON.fromJson(GSON.toJson(activeScene), VttScene.class);
        var visibleIds = VttServerVisionSourceSync.visibleObjectsFor(player, this).stream()
                .map(VttSceneObject::getId).collect(java.util.stream.Collectors.toSet());
        copy.getObjects().removeIf(object -> object == null || !visibleIds.contains(object.getId()));
        copy.getVisionSourceObjectIds().removeIf(id -> !visibleIds.contains(id));
        return copy;
    }

    public VttScene activeScene() {
        return activeScene;
    }

    public VttTabletop activeTabletop() {
        return tabletop;
    }

    public synchronized boolean applyAssetFolderCommand(
            VttAssetFolderCommandPayload request
    ) {
        if (request == null) return false;
        VttAssetFolderService.Section section;
        try {
            section = VttAssetFolderService.Section.valueOf(request.section());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        boolean changed = switch (request.operation()) {
            case VttAssetFolderCommandPayload.REFRESH -> refreshAssetFolders();
            case VttAssetFolderCommandPayload.CREATE_FOLDER ->
                    assetFolderService.createFolder(section, request.source(), request.value());
            case VttAssetFolderCommandPayload.RENAME_FOLDER ->
                    assetFolderService.renameFolder(
                            section, request.source(), request.value()) != null;
            case VttAssetFolderCommandPayload.DUPLICATE_FOLDER ->
                    duplicateAssetFolder(section, request.source());
            case VttAssetFolderCommandPayload.MOVE_FOLDER ->
                    assetFolderService.moveFolder(
                            section, request.source(), request.value()) != null;
            case VttAssetFolderCommandPayload.MOVE_ITEM ->
                    assetFolderService.moveItem(
                            section, request.source(), request.value());
            case VttAssetFolderCommandPayload.MOVE_SELECTION ->
                    assetFolderService.moveSelection(
                            section, request.source(), request.value());
            case VttAssetFolderCommandPayload.DELETE_SELECTION ->
                    deleteAssetSelection(section, request.source());
            case VttAssetFolderCommandPayload.DELETE_FOLDER ->
                    assetFolderService.deleteEmptyFolder(section, request.source());
            case VttAssetFolderCommandPayload.MOVE_CONTENTS_AND_DELETE_FOLDER ->
                    assetFolderService.moveContentsToParentAndDelete(
                            section, request.source());
            default -> false;
        };
        if (!changed) return false;
        assetFolderService.applyMetadata(tabletop, null, null);
        storage.saveTabletop(tabletop);
        advanceAuthorityRevision();
        return true;
    }

    public synchronized boolean applyMapDefinitionUpdate(
            String definitionId,
            String displayName,
            String assetId,
            int previousWidth,
            int previousHeight,
            int imageWidth,
            int imageHeight,
            MapTextureMode textureMode
    ) {
        boolean activeChanged = false;
        for (String sceneId : List.copyOf(tabletop.getSceneIds())) {
            VttScene scene = activeScene != null && sceneId.equals(activeScene.getId())
                    ? activeScene : storage.loadScene(tabletop.getId(), sceneId);
            if (scene == null) continue;
            boolean sceneChanged = false;
            for (var map : scene.getMaps()) {
                if (map == null || !definitionId.equals(map.getSourceMapDefinitionId())) continue;
                if (previousWidth > 0 && imageWidth > 0) {
                    map.getTransform().setScaleX(map.getTransform().getScaleX()
                            * previousWidth / (double) imageWidth);
                }
                if (previousHeight > 0 && imageHeight > 0) {
                    map.getTransform().setScaleY(map.getTransform().getScaleY()
                            * previousHeight / (double) imageHeight);
                }
                map.setDisplayName(displayName);
                map.setAssetId(assetId);
                map.setTextureMode(textureMode);
                sceneChanged = true;
            }
            if (!sceneChanged) continue;
            if (scene == activeScene) {
                activeChanged = true;
            } else if (!storage.saveScene(tabletop.getId(), scene)) {
                return false;
            }
        }
        if (activeChanged) {
            markActiveSceneDirty();
            if (!flushActiveSceneNow("map definition update")) return false;
        }
        advanceAuthorityRevision();
        return true;
    }

    public synchronized void markAssetCatalogChanged() {
        advanceAuthorityRevision();
    }

    public synchronized boolean isAttachmentDefinitionInUse(String definitionId) {
        if (definitionId == null || definitionId.isBlank()) return false;
        for (String sceneId : List.copyOf(tabletop.getSceneIds())) {
            VttScene scene = activeScene != null && sceneId.equals(activeScene.getId())
                    ? activeScene : storage.loadScene(tabletop.getId(), sceneId);
            if (scene == null) continue;
            if (scene.getObjects().stream().anyMatch(object -> object != null
                    && definitionId.equals(object.getSourceAttachmentDefinitionId()))) {
                return true;
            }
        }
        return false;
    }

    private boolean refreshAssetFolders() {
        assetFolderService.refresh();
        return true;
    }

    private boolean deleteAssetSelection(
            VttAssetFolderService.Section section,
            String encodedSources
    ) {
        List<VttAssetFolderService.SelectionEntry> entries =
                VttAssetFolderService.decodeSelection(encodedSources);
        if (entries.size() < 2) return false;
        List<String> itemIds = entries.stream()
                .filter(entry -> !entry.folder())
                .map(VttAssetFolderService.SelectionEntry::source).toList();
        VttScene replacement = null;
        if (section == VttAssetFolderService.Section.SCENES) {
            if (!tabletop.getSceneIds().containsAll(itemIds)
                    || tabletop.getSceneIds().size() - itemIds.size() < 1) return false;
            if (itemIds.contains(activeScene.getId())) {
                String replacementId = tabletop.getSceneIds().stream()
                        .filter(id -> !itemIds.contains(id)).findFirst().orElse(null);
                replacement = storage.loadScene(tabletop.getId(), replacementId);
                if (replacement == null) return false;
            }
            if (!flushActiveSceneNow("batch scene delete")) return false;
        } else {
            for (String id : itemIds) {
                if (section == VttAssetFolderService.Section.MAPS
                        && (!id.startsWith("user/maps/")
                        || definitionUsedInAnyScene(section, id))) return false;
                if (section == VttAssetFolderService.Section.TOKENS
                        && (!id.startsWith("user/tokens/")
                        || definitionUsedInAnyScene(section, id))) return false;
            }
        }
        if (!assetFolderService.deleteSelection(section, encodedSources)) return false;
        if (section == VttAssetFolderService.Section.SCENES) {
            itemIds.forEach(tabletop::removeSceneId);
            if (replacement != null) {
                activeScene = replacement;
                tabletop.setActiveSceneId(replacement.getId());
                objectSpatialIndex.rebuild(activeScene);
                visionGeometryIndex.rebuild(activeScene);
                movementCollision.rebuildObstacleIndex(activeScene);
            }
        }
        return true;
    }

    private boolean definitionUsedInAnyScene(
            VttAssetFolderService.Section section,
            String definitionId
    ) {
        for (String sceneId : List.copyOf(tabletop.getSceneIds())) {
            VttScene candidate = activeScene != null && sceneId.equals(activeScene.getId())
                    ? activeScene : storage.loadScene(tabletop.getId(), sceneId);
            if (candidate == null) continue;
            if (section == VttAssetFolderService.Section.MAPS
                    && candidate.getMaps().stream().anyMatch(map -> map != null
                    && definitionId.equals(map.getSourceMapDefinitionId()))) return true;
            if (section == VttAssetFolderService.Section.TOKENS
                    && candidate.getObjects().stream().anyMatch(object -> object != null
                    && definitionId.equals(object.getSourceTokenDefinitionId()))) return true;
        }
        return false;
    }

    private boolean duplicateAssetFolder(
            VttAssetFolderService.Section section,
            String folder
    ) {
        VttAssetFolderService.FolderDuplicateResult result =
                assetFolderService.duplicateFolder(section, folder);
        if (result == null) return false;
        for (VttAssetFolderService.DuplicatedScene scene : result.scenes()) {
            tabletop.addSceneId(scene.id());
            tabletop.setSceneDisplayName(scene.id(), scene.displayName());
            tabletop.setSceneFolder(scene.id(), scene.folder());
        }
        return true;
    }

    public synchronized boolean isPlayerSpectator(UUID playerId) {
        return playerPreferences.isSpectator(playerId);
    }

    public synchronized boolean setPlayerSpectator(UUID playerId, boolean spectator) {
        boolean previous = playerPreferences.isSpectator(playerId);
        if (!playerPreferences.setSpectator(playerId, spectator)) return false;
        if (storage.savePlayerPreferences(tabletop.getId(), playerPreferences)) return true;
        playerPreferences.setSpectator(playerId, previous);
        return false;
    }

    public synchronized List<VttSceneObject> querySceneObjects(
            double minX, double minY, double maxX, double maxY
    ) {
        if (activeScene == null) return List.of();
        if (!objectSpatialIndex.isBuiltFor(activeScene)
                || objectSpatialIndex.size() != activeScene.getObjects().size()) {
            objectSpatialIndex.rebuild(activeScene);
        }
        if (objectSpatialIndex.size() != activeScene.getObjects().size()) {
            return List.copyOf(activeScene.getObjects());
        }
        try {
            return objectSpatialIndex.query(minX, minY, maxX, maxY);
        } catch (RuntimeException exception) {
            VTT.LOGGER.warn("VTT spatial query failed; using full scene fallback", exception);
            objectSpatialIndex.rebuild(activeScene);
            return List.copyOf(activeScene.getObjects());
        }
    }

    public synchronized int indexedSceneObjectCount() {
        if (activeScene != null && (!objectSpatialIndex.isBuiltFor(activeScene)
                || objectSpatialIndex.size() != activeScene.getObjects().size())) {
            objectSpatialIndex.rebuild(activeScene);
        }
        return objectSpatialIndex.size();
    }

    public synchronized List<VisionSegment> queryVisionSegments(
            Vec2d origin, double radius
    ) {
        if (activeScene == null || origin == null || !Double.isFinite(radius) || radius <= 0.0) {
            return List.of();
        }
        if (!visionGeometryIndex.isBuiltFor(activeScene)) visionGeometryIndex.rebuild(activeScene);
        try {
            return visionGeometryIndex.query(
                    origin.x() - radius, origin.y() - radius,
                    origin.x() + radius, origin.y() + radius);
        } catch (RuntimeException exception) {
            VTT.LOGGER.warn("VTT vision geometry query failed; using full geometry fallback", exception);
            visionGeometryIndex.rebuild(activeScene);
            return visionGeometryIndex.allSegments();
        }
    }

    public synchronized long authorityRevision() {
        return authorityRevision;
    }

    public synchronized boolean togglePresentationBlackout() {
        presentationBlackout = !presentationBlackout;
        return presentationBlackout;
    }

    public synchronized boolean isPresentationBlackout() {
        return presentationBlackout;
    }

    public synchronized boolean togglePresentationCameraFollow(
            double cameraX, double cameraY, double cameraZoom
    ) {
        updatePresentationCamera(cameraX, cameraY, cameraZoom);
        presentationCameraFollow = !presentationCameraFollow;
        return presentationCameraFollow;
    }

    public synchronized void updatePresentationCamera(
            double cameraX, double cameraY, double cameraZoom
    ) {
        presentationCameraX = cameraX;
        presentationCameraY = cameraY;
        presentationCameraZoom = cameraZoom;
    }

    public synchronized boolean disablePresentationCameraFollow() {
        if (!presentationCameraFollow) return false;
        presentationCameraFollow = false;
        return true;
    }

    public synchronized PresentationState presentationState() {
        return new PresentationState(
                presentationBlackout, presentationCameraFollow,
                presentationCameraX, presentationCameraY, presentationCameraZoom);
    }

    public record PresentationState(
            boolean blackout, boolean cameraFollow,
            double cameraX, double cameraY, double cameraZoom
    ) {
    }

    public synchronized void tickPersistence() {
        if (!activeSceneDirty) return;
        long now = System.currentTimeMillis();
        boolean idleLongEnough = now - lastSceneMutationAt >= SCENE_SAVE_DEBOUNCE_MS;
        boolean dirtyTooLong = now - activeSceneDirtySince >= SCENE_SAVE_MAX_DIRTY_MS;
        if (idleLongEnough || dirtyTooLong) flushActiveSceneNow(
                dirtyTooLong ? "maximum dirty age" : "debounce elapsed");
    }

    public synchronized boolean flushActiveSceneNow(String reason) {
        if (!activeSceneDirty) return true;
        return saveSceneImmediately(activeScene, reason == null ? "explicit flush" : reason);
    }

    public synchronized long pendingSceneMutationCount() {
        return pendingSceneMutationCount;
    }

    public synchronized long completedSceneSaveCount() {
        return completedSceneSaveCount;
    }

    public synchronized long lastCompletedSceneSaveAt() {
        return lastCompletedSceneSaveAt;
    }

    private void markActiveSceneDirty() {
        long now = System.currentTimeMillis();
        if (!activeSceneDirty) {
            activeSceneDirtySince = now;
            VTT.LOGGER.debug("Queued debounced VTT save for scene {}", activeScene.getId());
        }
        activeSceneDirty = true;
        lastSceneMutationAt = now;
        pendingSceneMutationCount++;
    }

    private boolean saveSceneImmediately(VttScene scene, String reason) {
        if (scene == null) return false;
        long groupedMutations = scene == activeScene ? pendingSceneMutationCount : 0L;
        boolean saved = storage.saveScene(tabletop.getId(), scene);
        if (!saved) return false;
        completedSceneSaveCount++;
        lastCompletedSceneSaveAt = System.currentTimeMillis();
        if (scene == activeScene) {
            activeSceneDirty = false;
            activeSceneDirtySince = 0L;
            lastSceneMutationAt = 0L;
            pendingSceneMutationCount = 0L;
        }
        VTT.LOGGER.debug("Flushed VTT scene {} after {} pending mutation(s): {}",
                scene.getId(), groupedMutations, reason);
        return true;
    }

    public synchronized boolean switchToScene(String sceneId) {
        if (sceneId == null || sceneId.isBlank()) return false;
        if (sceneId.equals(tabletop.getActiveSceneId())) return true;
        if (!tabletop.getSceneIds().contains(sceneId)) return false;
        VttScene target = storage.loadScene(tabletop.getId(), sceneId);
        if (target == null) return false;
        if (!flushActiveSceneNow("scene switch")) return false;
        activeScene = target;
        objectSpatialIndex.rebuild(activeScene);
        visionGeometryIndex.rebuild(activeScene);
        movementCollision.rebuildObstacleIndex(activeScene);
        advanceAuthorityRevision();
        tabletop.setActiveSceneId(sceneId);
        storage.saveTabletop(tabletop);
        return true;
    }

    public synchronized boolean createAndActivateScene(
            String displayName, String backgroundAssetId
    ) {
        return createAndActivateScene(displayName, "", backgroundAssetId);
    }

    public synchronized boolean createAndActivateScene(
            String displayName, String sourceMapDefinitionId, String mapAssetId
    ) {
        return createAndActivateScene(displayName, sourceMapDefinitionId, mapAssetId, "");
    }

    public synchronized boolean createAndActivateScene(
            String displayName, String sourceMapDefinitionId, String mapAssetId,
            String mapTextureMode
    ) {
        if (displayName == null || displayName.isBlank() || displayName.length() > 48
                || VttSceneLimits.sceneCreation(tabletop) != null) return false;
        String trimmedName = displayName.trim();
        String baseId = trimmedName.toLowerCase()
                .replaceAll("[^a-z0-9_-]", "_").replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (baseId.isBlank()) baseId = "new_scene";
        if (baseId.length() > 64) baseId = baseId.substring(0, 64);
        String sceneId = baseId;
        int suffix = 2;
        while (tabletop.getSceneIds().contains(sceneId)) sceneId = baseId + "_" + suffix++;

        if (!flushActiveSceneNow("scene creation")) return false;
        VttScene created = new VttScene(sceneId, trimmedName);
        if (sourceMapDefinitionId != null && !sourceMapDefinitionId.isBlank()) {
            String validatedAssetId = validateBackgroundAssetId(mapAssetId);
            if (sourceMapDefinitionId.length() > 128 || validatedAssetId == null) return false;
            created.addMap(new com.petrick.vtt.feature.tabletop.VttSceneMap(
                    "map_" + java.util.UUID.randomUUID().toString()
                            .replace("-", "").substring(0, 12),
                    sourceMapDefinitionId.substring(
                            sourceMapDefinitionId.lastIndexOf('/') + 1),
                    sourceMapDefinitionId, validatedAssetId,
                    parseMapTextureMode(mapTextureMode)));
        } else if (mapAssetId != null && !mapAssetId.isBlank()) {
            created.setBackgroundAssetId(mapAssetId);
        }
        if (!saveSceneImmediately(created, "scene creation")) return false;
        tabletop.addSceneId(sceneId);
        tabletop.setSceneDisplayName(sceneId, trimmedName);
        tabletop.setActiveSceneId(sceneId);
        activeScene = created;
        objectSpatialIndex.rebuild(activeScene);
        visionGeometryIndex.rebuild(activeScene);
        movementCollision.rebuildObstacleIndex(activeScene);
        advanceAuthorityRevision();
        storage.saveTabletop(tabletop);
        return true;
    }

    private com.petrick.vtt.feature.map.MapTextureMode parseMapTextureMode(String value) {
        if (value == null || value.isBlank()) {
            return com.petrick.vtt.feature.map.MapTextureMode.STRETCH;
        }
        try {
            return com.petrick.vtt.feature.map.MapTextureMode.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return com.petrick.vtt.feature.map.MapTextureMode.STRETCH;
        }
    }

    public synchronized boolean renameScene(String sceneId, String displayName) {
        if (sceneId == null || sceneId.isBlank() || !tabletop.getSceneIds().contains(sceneId)
                || displayName == null || displayName.isBlank() || displayName.length() > 48) return false;
        String trimmedName = displayName.trim();
        VttScene target = sceneId.equals(activeScene.getId())
                ? activeScene : storage.loadScene(tabletop.getId(), sceneId);
        if (target == null) return false;
        target.setDisplayName(trimmedName);
        if (target == activeScene) {
            markActiveSceneDirty();
            if (!flushActiveSceneNow("scene rename")) return false;
        } else if (!saveSceneImmediately(target, "scene rename")) return false;
        tabletop.setSceneDisplayName(sceneId, trimmedName);
        if (!storage.saveTabletop(tabletop)) return false;
        advanceAuthorityRevision();
        return true;
    }

    public synchronized boolean duplicateAndActivateScene(String sceneId) {
        if (sceneId == null || sceneId.isBlank()
                || !tabletop.getSceneIds().contains(sceneId)
                || VttSceneLimits.sceneCreation(tabletop) != null) return false;
        if (!flushActiveSceneNow("scene duplication")) return false;
        VttScene source = activeScene != null && sceneId.equals(activeScene.getId())
                ? activeScene : storage.loadScene(tabletop.getId(), sceneId);
        if (source == null) return false;

        String displayName = tabletop.getSceneDisplayName(sceneId) + " Copy";
        String baseId = displayName.toLowerCase()
                .replaceAll("[^a-z0-9_-]", "_").replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (baseId.isBlank()) baseId = "scene_copy";
        if (baseId.length() > 64) baseId = baseId.substring(0, 64);
        String duplicateId = baseId;
        int suffix = 2;
        while (tabletop.getSceneIds().contains(duplicateId)) {
            String suffixText = "_" + suffix++;
            duplicateId = baseId.substring(
                    0, Math.min(baseId.length(), 64 - suffixText.length())) + suffixText;
        }

        VttScene duplicate = VttSceneDuplicator.duplicate(
                source, duplicateId, displayName);
        if (duplicate == null
                || !saveSceneImmediately(duplicate, "scene duplication")) return false;
        String sourceFolder = tabletop.getSceneFolder(sceneId);
        if (sourceFolder != null && !sourceFolder.isBlank()) {
            assetFolderService.moveItem(
                    VttAssetFolderService.Section.SCENES,
                    duplicateId, sourceFolder);
            assetFolderService.applyMetadata(tabletop, null, null);
        }
        tabletop.addSceneId(duplicateId);
        tabletop.setSceneDisplayName(duplicateId, displayName);
        tabletop.setActiveSceneId(duplicateId);
        activeScene = duplicate;
        objectSpatialIndex.rebuild(activeScene);
        visionGeometryIndex.rebuild(activeScene);
        movementCollision.rebuildObstacleIndex(activeScene);
        advanceAuthorityRevision();
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
        if (!flushActiveSceneNow("scene delete")) return false;
        if (!storage.deleteScene(tabletop.getId(), sceneId)) return false;
        tabletop.removeSceneId(sceneId);
        if (deletingActive) {
            activeScene = replacement;
            objectSpatialIndex.rebuild(activeScene);
            visionGeometryIndex.rebuild(activeScene);
            movementCollision.rebuildObstacleIndex(activeScene);
            tabletop.setActiveSceneId(replacement.getId());
            tabletop.setSceneDisplayName(replacement.getId(), replacement.getDisplayName());
        }
        if (!storage.saveTabletop(tabletop)) return false;
        advanceAuthorityRevision();
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
            if (activeScene.getBackgroundAssetId() == null) return true;
            activeScene.setBackgroundAssetId(null);
            markActiveSceneDirty();
            if (!flushActiveSceneNow("background change")) return false;
            advanceAuthorityRevision();
            return true;
        }
        String validatedId = validateBackgroundAssetId(assetId.trim());
        if (validatedId == null) return false;
        if (validatedId.equals(activeScene.getBackgroundAssetId())) return true;
        activeScene.setBackgroundAssetId(validatedId);
        markActiveSceneDirty();
        if (!flushActiveSceneNow("background change")) return false;
        advanceAuthorityRevision();
        return true;
    }

    public synchronized boolean clearActiveSceneMaps() {
        if (activeScene == null) return false;
        if (activeScene.getMaps().isEmpty()) return true;
        activeScene.getMaps().clear();
        markActiveSceneDirty();
        if (!flushActiveSceneNow("clear scene maps")) return false;
        advanceAuthorityRevision();
        return true;
    }

    public synchronized SceneHistoryApplyResult applySceneHistory(
            String sceneId, String expectedFingerprint, String targetSceneJson
    ) {
        if (activeScene == null || sceneId == null || !sceneId.equals(activeScene.getId())
                || expectedFingerprint == null || expectedFingerprint.length() != 64
                || targetSceneJson == null || targetSceneJson.isBlank()
                || targetSceneJson.length() > VttSceneHistoryCommandPayload.MAX_SCENE_JSON_LENGTH) {
            return SceneHistoryApplyResult.INVALID;
        }
        String currentFingerprint = VttSceneFingerprint.of(activeScene);
        if (!expectedFingerprint.equals(currentFingerprint)) {
            VTT.LOGGER.warn("Rejected stale VTT scene history state for {}: expected={}, current={}",
                    sceneId, expectedFingerprint, currentFingerprint);
            return SceneHistoryApplyResult.STALE;
        }
        final VttScene target;
        try {
            target = GSON.fromJson(targetSceneJson, VttScene.class);
        } catch (RuntimeException exception) {
            return SceneHistoryApplyResult.INVALID;
        }
        if (!validHistoryScene(target)) return SceneHistoryApplyResult.INVALID;
        if (!flushActiveSceneNow("before scene history operation")) {
            return SceneHistoryApplyResult.SAVE_FAILED;
        }

        VttScene previous = activeScene;
        activeScene = target;
        normalizeLayerIndices(activeScene);
        rebuildSceneIndexes();
        markActiveSceneDirty();
        if (!flushActiveSceneNow("scene history operation")) {
            activeScene = previous;
            rebuildSceneIndexes();
            clearDirtySceneState();
            return SceneHistoryApplyResult.SAVE_FAILED;
        }
        advanceAuthorityRevision();
        return SceneHistoryApplyResult.APPLIED;
    }

    public enum SceneHistoryApplyResult {
        APPLIED,
        STALE,
        INVALID,
        SAVE_FAILED
    }

    private boolean validHistoryScene(VttScene scene) {
        if (scene == null || activeScene == null
                || !activeScene.getId().equals(scene.getId())
                || !Objects.equals(activeScene.getDisplayName(), scene.getDisplayName())
                || scene.getSchemaVersion() != activeScene.getSchemaVersion()
                || !VttSceneLimits.inspect(scene).isEmpty()
                || scene.getBackgroundAssetId() != null
                && validateBackgroundAssetId(scene.getBackgroundAssetId()) == null
                || !validBackgroundTransform(scene.getBackgroundTransform())) return false;

        var camera = scene.getInitialCameraView();
        if (camera != null && (!Double.isFinite(camera.getX())
                || !Double.isFinite(camera.getY()) || !Double.isFinite(camera.getZoom())
                || Math.abs(camera.getX()) > 10_000_000.0
                || Math.abs(camera.getY()) > 10_000_000.0
                || camera.getZoom() < 0.1 || camera.getZoom() > 8.0)) return false;

        Set<String> objectIds = new HashSet<>();
        for (VttSceneObject object : scene.getObjects()) {
            if (object == null || object.getId() == null || object.getId().isBlank()
                    || object.getId().length() > 128 || !objectIds.add(object.getId())
                    || !validSceneObject(object)
                    || object.getDisplayName().length() > 128
                    || object.getSourceTokenDefinitionId().length() > 128
                    || object.getLayerIndex() < 0
                    || object.getOwnerId() != null && object.getOwnerId().length() > 128) return false;
            double innerRadius = object.getVisionInnerRadius();
            double storedOuterRadius = object.getVisionOuterRadius();
            double effectiveOuterRadius = storedOuterRadius > 0.0 ? storedOuterRadius : 512.0;
            if (!Double.isFinite(innerRadius) || !Double.isFinite(storedOuterRadius)
                    || innerRadius < 0.0 || storedOuterRadius < 0.0
                    || storedOuterRadius > 0.0 && storedOuterRadius < 64.0
                    || storedOuterRadius > 100_000.0
                    || innerRadius > effectiveOuterRadius) return false;
        }
        for (String sourceId : scene.getVisionSourceObjectIds()) {
            if (sourceId == null || !objectIds.contains(sourceId)) return false;
        }

        Set<String> mapIds = new HashSet<>();
        for (var map : scene.getMaps()) {
            if (map == null || map.getId() == null || map.getId().isBlank()
                    || map.getId().length() > 128 || !mapIds.add(map.getId())
                    || map.getDisplayName() == null || map.getDisplayName().isBlank()
                    || map.getDisplayName().length() > 128
                    || map.getSourceMapDefinitionId() == null
                    || map.getSourceMapDefinitionId().isBlank()
                    || map.getSourceMapDefinitionId().length() > 128
                    || map.getAssetId() == null || map.getAssetId().length() > 512
                    || validateBackgroundAssetId(map.getAssetId()) == null
                    || map.getTextureMode() == null || map.getLayerIndex() < 0
                    || map.getLayerIndex() > VttSceneLimits.MAX_MAPS
                    || !validBackgroundTransform(map.getTransform())) return false;
        }

        Set<String> wallIds = new HashSet<>();
        for (VttWall wall : scene.getWalls()) {
            if (wall == null || wall.getId() == null || wall.getId().isBlank()
                    || wall.getId().length() > 128 || !wallIds.add(wall.getId())
                    || !validGeometry(wall.getTransform(), wall.getSize())) return false;
        }
        Set<String> doorIds = new HashSet<>();
        for (VttDoor door : scene.getDoors()) {
            if (door == null || door.getId() == null || door.getId().isBlank()
                    || door.getId().length() > 128 || !doorIds.add(door.getId())
                    || !validGeometry(door.getTransform(), door.getSize())
                    || door.getWallId() != null && !wallIds.contains(door.getWallId())) return false;
        }
        Set<String> lightIds = new HashSet<>();
        for (var light : scene.getLights()) {
            if (light == null || light.getId() == null || light.getId().isBlank()
                    || light.getId().length() > 128 || !lightIds.add(light.getId())
                    || light.getType() != com.petrick.vtt.feature.tabletop.VttLightType.POINT
                    && light.getType() != com.petrick.vtt.feature.tabletop.VttLightType.SPOT
                    || !Double.isFinite(light.getX()) || !Double.isFinite(light.getY())
                    || !Double.isFinite(light.getInnerRadius())
                    || !Double.isFinite(light.getOuterRadius())
                    || !Double.isFinite(light.getIntensity())
                    || !Double.isFinite(light.getDirectionDegrees())
                    || !Double.isFinite(light.getConeAngleDegrees())
                    || !Double.isFinite(light.getInnerConeAngleDegrees())
                    || Math.abs(light.getX()) > 10_000_000.0
                    || Math.abs(light.getY()) > 10_000_000.0
                    || light.getInnerRadius() < 0.0
                    || light.getOuterRadius() < 1.0
                    || light.getOuterRadius() > 100_000.0
                    || light.getInnerRadius() > light.getOuterRadius()
                    || light.getIntensity()
                    < com.petrick.vtt.feature.tabletop.VttLight.MIN_INTENSITY
                    || light.getIntensity()
                    > com.petrick.vtt.feature.tabletop.VttLight.MAX_INTENSITY
                    || light.getConeAngleDegrees()
                    < com.petrick.vtt.feature.tabletop.VttLight.MIN_CONE_ANGLE_DEGREES
                    || light.getConeAngleDegrees()
                    > com.petrick.vtt.feature.tabletop.VttLight.MAX_CONE_ANGLE_DEGREES
                    || light.getInnerConeAngleDegrees() <= 0.0
                    || light.getInnerConeAngleDegrees() > light.getConeAngleDegrees()) return false;
        }
        Set<String> fogIds = new HashSet<>();
        for (VttFogArea area : scene.getFogOfWar().getHiddenAreas()) {
            if (!validHistoryFogArea(area, fogIds)) return false;
        }
        for (VttFogArea area : scene.getFogOfWar().getRevealedAreas()) {
            if (!validHistoryFogArea(area, fogIds)) return false;
        }
        return true;
    }

    private boolean validHistoryFogArea(VttFogArea area, Set<String> ids) {
        return area != null && area.getId() != null && !area.getId().isBlank()
                && area.getId().length() <= 128 && ids.add(area.getId())
                && validGeometry(area.getTransform(), area.getSize());
    }

    private void rebuildSceneIndexes() {
        objectSpatialIndex.rebuild(activeScene);
        visionGeometryIndex.rebuild(activeScene);
        movementCollision.rebuildObstacleIndex(activeScene);
    }

    private void clearDirtySceneState() {
        activeSceneDirty = false;
        activeSceneDirtySince = 0L;
        lastSceneMutationAt = 0L;
        pendingSceneMutationCount = 0L;
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
        List<VttSceneObject> matchingObjects = activeScene.getObjects().stream()
                .filter(object -> object != null && definitionId.equals(object.getSourceTokenDefinitionId()))
                .toList();
        if (matchingObjects.stream().allMatch(object -> Objects.equals(object.getOwnerId(), ownerId))) return;
        matchingObjects.forEach(object -> object.setOwnerId(ownerId));
        markActiveSceneDirty();
        flushActiveSceneNow("token ownership change");
    }

    public synchronized boolean setTokenOwner(String objectId, String ownerId) {
        if (activeScene == null || objectId == null || objectId.isBlank()) return false;
        VttSceneObject object = activeScene.getObjects().stream()
                .filter(candidate -> candidate != null && objectId.equals(candidate.getId()))
                .findFirst().orElse(null);
        if (object == null || object.getSourceTokenDefinitionId() == null
                || object.getSourceTokenDefinitionId().isBlank()
                || Objects.equals(object.getOwnerId(), ownerId)) return false;
        object.setOwnerId(ownerId);
        markActiveSceneDirty();
        flushActiveSceneNow("placed token ownership change");
        return true;
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
            if (scene == activeScene) {
                markActiveSceneDirty();
                flushActiveSceneNow("token definition removal");
            } else {
                saveSceneImmediately(scene, "token definition removal");
            }
            removedCount += removedIds.size();
        }
        objectSpatialIndex.rebuild(activeScene);
        return removedCount;
    }

    public synchronized List<String> scenesUsingTokenDefinition(String definitionId) {
        if (definitionId == null || definitionId.isBlank()) return List.of();
        List<String> usages = new ArrayList<>();
        for (String sceneId : List.copyOf(tabletop.getSceneIds())) {
            VttScene scene = activeScene != null && sceneId.equals(activeScene.getId())
                    ? activeScene : storage.loadScene(tabletop.getId(), sceneId);
            if (scene == null) continue;
            long count = scene.getObjects().stream()
                    .filter(object -> object != null
                            && definitionId.equals(object.getSourceTokenDefinitionId()))
                    .count();
            if (count > 0) {
                usages.add(tabletop.getSceneDisplayName(sceneId)
                        + " (" + count + (count == 1 ? " instance)" : " instances)"));
            }
        }
        return List.copyOf(usages);
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

        boolean attachment = object.isAttachment();
        VttAttachmentBinding binding = attachment ? object.getAttachmentBinding() : null;
        boolean followsPosition = binding != null && binding.isBound() && binding.isFollowPosition();
        boolean followsRotation = binding != null && binding.isBound() && binding.isFollowRotation();
        boolean followsScale = binding != null && binding.isBound() && binding.isFollowScale();
        // Masters edit a bound attachment's local offset through the regular transform tools.
        // The resulting world transform is captured back into the binding below.
        boolean editingAttachmentOffset = master && attachment && binding != null && binding.isBound();
        Vec2d currentPosition = new Vec2d(object.getTransform().getX(), object.getTransform().getY());
        Vec2d requestedDelta = followsPosition && !editingAttachmentOffset ? Vec2d.ZERO
                : new Vec2d(request.x(), request.y()).subtract(currentPosition);
        boolean bypassCollision = attachment || master && request.bypassCollision();
        Vec2d allowedDelta;
        if (!bypassCollision && requestedDelta.lengthSquared() > 4096.0 * 4096.0) {
            allowedDelta = Vec2d.ZERO;
        } else {
            allowedDelta = bypassCollision ? requestedDelta
                    : movementCollision.clipSceneObjectMovement(activeScene, object, requestedDelta);
        }
        boolean movementAccepted = allowedDelta.subtract(requestedDelta).lengthSquared() <= 0.0000001;
        int previousLayerIndex = currentLayerIndex(object);
        boolean tokenStateChanged = !attachment
                && !Objects.equals(object.getState().getActiveStateId(), request.activeStateId());
        com.petrick.vtt.feature.tabletop.VttTokenStateAppearance stateAppearance = null;
        if (tokenStateChanged) {
            stateAppearance = object.getStateAppearances().get(request.activeStateId());
            if (stateAppearance == null) stateAppearance = object.getGlobalStateAppearance();
        }
        double requestedScaleX = stateAppearance == null
                ? request.scaleX() : stateAppearance.getScaleX();
        double requestedScaleY = stateAppearance == null
                ? request.scaleY() : stateAppearance.getScaleY();
        int requestedTint = stateAppearance == null
                ? request.tintColorRgb() : stateAppearance.getTintColorRgb();
        boolean masterFieldsAccepted = master
                || (nearlyEqual(request.scaleX(), requestedScaleX)
                && nearlyEqual(request.scaleY(), requestedScaleY)
                && request.layerIndex() == previousLayerIndex
                && request.visible() == object.getState().isVisible()
                && Objects.equals(request.displayName(), object.getDisplayName()));
        Vec2d acceptedPosition = currentPosition.add(allowedDelta);
        double acceptedRotation = stateAppearance != null
                ? normalizeRotation(stateAppearance.getRotationDegrees())
                : followsRotation && !editingAttachmentOffset
                ? object.getTransform().getRotationDegrees()
                : normalizeRotation(request.rotationDegrees());
        boolean contentChanged = !nearlyEqual(acceptedPosition.x(), object.getTransform().getX())
                || !nearlyEqual(acceptedPosition.y(), object.getTransform().getY())
                || !nearlyEqual(acceptedRotation, object.getTransform().getRotationDegrees())
                || (!followsPosition || editingAttachmentOffset) && (object.getState().isFlippedHorizontally()
                != request.flippedHorizontally())
                || !Objects.equals(object.getState().getActiveStateId(), request.activeStateId())
                || object.getState().getTintColorRgb() != (requestedTint & 0x00FFFFFF)
                || master && (
                (!followsScale || editingAttachmentOffset)
                && (!nearlyEqual(requestedScaleX, object.getTransform().getScaleX())
                || !nearlyEqual(requestedScaleY, object.getTransform().getScaleY()))
                || request.layerIndex() != previousLayerIndex
                || request.visible() != object.getState().isVisible()
                || !Objects.equals(request.displayName(), object.getDisplayName()));
        object.getTransform().setX(acceptedPosition.x());
        object.getTransform().setY(acceptedPosition.y());
        object.getTransform().setRotationDegrees(acceptedRotation);
        if (master || stateAppearance != null) {
            if (!followsScale || editingAttachmentOffset) {
                object.getTransform().setScaleX(requestedScaleX);
                object.getTransform().setScaleY(requestedScaleY);
            }
        }
        if (master) {
            moveObjectToLayer(object, request.layerIndex());
            object.getState().setVisible(request.visible());
            object.setDisplayName(request.displayName());
        }
        if (!followsPosition || editingAttachmentOffset) {
            object.getState().setFlippedHorizontally(request.flippedHorizontally());
        }
        object.getState().setActiveStateId(request.activeStateId());
        object.getState().setTintColorRgb(requestedTint);
        if (editingAttachmentOffset) {
            captureAttachmentBindingFromTransform(object, binding);
        }
        objectSpatialIndex.addOrUpdate(object);
        if (contentChanged) markActiveSceneDirty();

        long entityRevision = nextRevision(tokenTransformRevisions, object.getId());
        return new VttTokenTransformUpdatePayload(authorityRevision, entityRevision, request.clientSequence(),
                activeScene.getId(), object.getId(), object.getTransform().getX(),
                object.getTransform().getY(), object.getTransform().getRotationDegrees(),
                object.getTransform().getScaleX(), object.getTransform().getScaleY(), currentLayerIndex(object),
                object.getState().isFlippedHorizontally(), object.getState().isVisible(),
                object.getDisplayName(), object.getState().getActiveStateId(),
                object.getState().getTintColorRgb(),
                playerId, movementAccepted && masterFieldsAccepted);
    }

    /** Returns the authoritative binding after a master edits a bound attachment's offset. */
    public synchronized VttEnvironmentCommandUpdatePayload currentAttachmentBindingUpdate(
            String attachmentId
    ) {
        if (activeScene == null || attachmentId == null || attachmentId.isBlank()) return null;
        VttSceneObject attachment = activeScene.getObjects().stream()
                .filter(object -> object != null && attachmentId.equals(object.getId()))
                .findFirst().orElse(null);
        if (attachment == null || !attachment.isAttachment()
                || attachment.getAttachmentBinding() == null
                || !attachment.getAttachmentBinding().isBound()) return null;
        String key = VttEnvironmentCommandPayload.ATTACHMENT_BINDING + "\u0000" + attachmentId;
        return new VttEnvironmentCommandUpdatePayload(authorityRevision,
                nextRevision(environmentRevisions, key), 0L,
                VttEnvironmentCommandPayload.UPSERT, activeScene.getId(),
                VttEnvironmentCommandPayload.ATTACHMENT_BINDING, attachmentId,
                GSON.toJson(attachment.getAttachmentBinding()), "server");
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
                object.getDisplayName(), object.getState().getActiveStateId(),
                object.getState().getTintColorRgb(), playerId, false);
    }

    /** Resolves bound attachments and attachment-owned lights after a token/attachment changes. */
    public synchronized AttachmentDependencyUpdates synchronizeAttachmentDependencies(
            String changedObjectId
    ) {
        if (activeScene == null || changedObjectId == null || changedObjectId.isBlank()) {
            return AttachmentDependencyUpdates.EMPTY;
        }
        VttSceneObject root = activeScene.getObjects().stream()
                .filter(object -> object != null && changedObjectId.equals(object.getId()))
                .findFirst().orElse(null);
        List<VttTokenTransformUpdatePayload> transforms = new ArrayList<>();
        Set<String> attachmentIds = new HashSet<>();
        if (root != null && root.isAttachment()) attachmentIds.add(root.getId());
        if (root != null) {
            List<VttSceneObject> level = List.of(root);
            Set<String> visited = new HashSet<>();
            visited.add(root.getId());
            for (int depth = 0;
                 depth < com.petrick.vtt.feature.attachment.AttachmentBindingService.MAX_BINDING_DEPTH
                         && !level.isEmpty(); depth++) {
                List<VttSceneObject> next = new ArrayList<>();
                for (VttSceneObject parent : level) {
                    for (VttSceneObject child : activeScene.getObjects()) {
                        if (child == null || !child.isAttachment()
                                || !visited.add(child.getId())) continue;
                        VttAttachmentBinding binding = child.getAttachmentBinding();
                        if (binding == null || !binding.isBound()
                                || !parent.getId().equals(binding.getTargetObjectId())) {
                            visited.remove(child.getId());
                            continue;
                        }
                        attachmentIds.add(child.getId());
                        next.add(child);
                        if (resolveBoundAttachment(child, parent, binding)) {
                            objectSpatialIndex.addOrUpdate(child);
                            transforms.add(transformUpdate(child,
                                    nextRevision(tokenTransformRevisions, child.getId())));
                        }
                    }
                }
                level = next;
            }
        }
        List<VttEnvironmentCommandUpdatePayload> lights = new ArrayList<>();
        for (VttLight light : activeScene.getLights()) {
            if (light == null || !light.isAttached()
                    || !attachmentIds.contains(light.getAttachedToObjectId())) continue;
            VttSceneObject attachment = activeScene.getObjects().stream()
                    .filter(object -> object != null
                            && light.getAttachedToObjectId().equals(object.getId()))
                    .findFirst().orElse(null);
            if (attachment == null || !resolveAttachedLight(light, attachment)) continue;
            String key = VttEnvironmentCommandPayload.LIGHT + "\u0000" + light.getId();
            lights.add(new VttEnvironmentCommandUpdatePayload(authorityRevision,
                    nextRevision(environmentRevisions, key), 0L,
                    VttEnvironmentCommandPayload.UPSERT, activeScene.getId(),
                    VttEnvironmentCommandPayload.LIGHT, light.getId(), GSON.toJson(light), "server"));
        }
        if (!transforms.isEmpty() || !lights.isEmpty()) markActiveSceneDirty();
        return transforms.isEmpty() && lights.isEmpty() ? AttachmentDependencyUpdates.EMPTY
                : new AttachmentDependencyUpdates(List.copyOf(transforms), List.copyOf(lights));
    }

    private boolean resolveBoundAttachment(
            VttSceneObject child, VttSceneObject parent, VttAttachmentBinding binding
    ) {
        String previousActiveState = child.getState().getActiveStateId();
        if (binding.isStateMappingEnabled()) {
            VttSceneObject rootToken = attachmentRootToken(parent);
            if (rootToken != null && rootToken.getState() != null) {
                String mapped = binding.getParentStateMappings().get(
                        rootToken.getState().getActiveStateId());
                if (mapped == null) mapped = binding.getFallbackAttachmentStateId();
                if (mapped != null) child.getState().setActiveStateId(mapped);
            }
        }
        var childTransform = child.getTransform();
        var parentTransform = parent.getTransform();
        double x = childTransform.getX();
        double y = childTransform.getY();
        if (binding.isFollowPosition()) {
            double anchorX = parent.getSize().getWidth() * 0.5
                    * binding.getAnchor().horizontal() * parentTransform.getScaleX();
            double anchorY = parent.getSize().getHeight() * 0.5
                    * binding.getAnchor().vertical() * parentTransform.getScaleY();
            double residualX = binding.getOffsetX();
            double residualY = binding.getOffsetY();
            if (binding.isFollowScale()) {
                if (binding.isInheritScaleX()) residualX *= parentTransform.getScaleX();
                if (binding.isInheritScaleY()) residualY *= parentTransform.getScaleY();
            }
            double localX = anchorX + residualX;
            double localY = anchorY + residualY;
            double offsetX = parent.getState().isFlippedHorizontally()
                    ? -localX : localX;
            double offsetY = localY;
            if (binding.isFollowRotation()) {
                double radians = Math.toRadians(parentTransform.getRotationDegrees());
                double rotatedX = offsetX * Math.cos(radians) - offsetY * Math.sin(radians);
                offsetY = offsetX * Math.sin(radians) + offsetY * Math.cos(radians);
                offsetX = rotatedX;
            }
            x = parentTransform.getX() + offsetX;
            y = parentTransform.getY() + offsetY;
        }
        double rotation = binding.isFollowRotation()
                ? normalizeRotation(parentTransform.getRotationDegrees()
                + binding.getRotationOffsetDegrees()) : childTransform.getRotationDegrees();
        double scaleX = binding.isFollowScale()
                ? (binding.isInheritScaleX()
                ? parentTransform.getScaleX() * binding.getScaleMultiplierX()
                : binding.getScaleMultiplierX())
                : childTransform.getScaleX();
        double scaleY = binding.isFollowScale()
                ? (binding.isInheritScaleY()
                ? parentTransform.getScaleY() * binding.getScaleMultiplierY()
                : binding.getScaleMultiplierY())
                : childTransform.getScaleY();
        scaleX = binding.constrainScale(scaleX);
        scaleY = binding.constrainScale(scaleY);
        boolean flipped = parent.getState().isFlippedHorizontally() ^ binding.isFlipOffset();
        boolean changed = !nearlyEqual(x, childTransform.getX()) || !nearlyEqual(y, childTransform.getY())
                || !nearlyEqual(rotation, childTransform.getRotationDegrees())
                || !nearlyEqual(scaleX, childTransform.getScaleX())
                || !nearlyEqual(scaleY, childTransform.getScaleY())
                || !java.util.Objects.equals(previousActiveState,
                child.getState().getActiveStateId())
                || child.getState().isFlippedHorizontally() != flipped;
        if (!changed) return false;
        childTransform.setX(x); childTransform.setY(y);
        childTransform.setRotationDegrees(rotation);
        childTransform.setScaleX(scaleX); childTransform.setScaleY(scaleY);
        child.getState().setFlippedHorizontally(flipped);
        return true;
    }

    private VttSceneObject attachmentRootToken(VttSceneObject start) {
        VttSceneObject current = start;
        Set<String> visited = new java.util.HashSet<>();
        while (current != null && current.getId() != null && visited.add(current.getId())) {
            if (current.getSourceTokenDefinitionId() != null
                    && !current.getSourceTokenDefinitionId().isBlank()) return current;
            VttAttachmentBinding binding = current.getAttachmentBinding();
            if (binding == null || !binding.isBound()) return null;
            String parentId = binding.getTargetObjectId();
            current = activeScene.getObjects().stream()
                    .filter(object -> object != null && parentId.equals(object.getId()))
                    .findFirst().orElse(null);
        }
        return null;
    }

    private void captureAttachmentBindingFromTransform(
            VttSceneObject attachment, VttAttachmentBinding binding
    ) {
        VttSceneObject parent = activeScene.getObjects().stream()
                .filter(object -> object != null
                        && binding.getTargetObjectId().equals(object.getId()))
                .findFirst().orElse(null);
        if (parent == null) return;
        var childTransform = attachment.getTransform();
        var parentTransform = parent.getTransform();
        double offsetX = childTransform.getX() - parentTransform.getX();
        double offsetY = childTransform.getY() - parentTransform.getY();
        if (binding.isFollowRotation()) {
            double radians = Math.toRadians(-parentTransform.getRotationDegrees());
            double rotatedX = offsetX * Math.cos(radians) - offsetY * Math.sin(radians);
            offsetY = offsetX * Math.sin(radians) + offsetY * Math.cos(radians);
            offsetX = rotatedX;
        }
        if (parent.getState().isFlippedHorizontally()) offsetX = -offsetX;
        offsetX -= parent.getSize().getWidth() * 0.5
                * binding.getAnchor().horizontal() * parentTransform.getScaleX();
        offsetY -= parent.getSize().getHeight() * 0.5
                * binding.getAnchor().vertical() * parentTransform.getScaleY();
        if (binding.isFollowScale()) {
            if (binding.isInheritScaleX()) offsetX /= nonZeroScale(parentTransform.getScaleX());
            if (binding.isInheritScaleY()) offsetY /= nonZeroScale(parentTransform.getScaleY());
        }
        if (!binding.isLockOffsetX()) binding.setOffsetX(offsetX);
        if (!binding.isLockOffsetY()) binding.setOffsetY(offsetY);
        binding.setRotationOffsetDegrees(childTransform.getRotationDegrees()
                - parentTransform.getRotationDegrees());
        binding.setScaleMultiplierX(binding.isInheritScaleX()
                ? childTransform.getScaleX() / nonZeroScale(parentTransform.getScaleX())
                : childTransform.getScaleX());
        binding.setScaleMultiplierY(binding.isInheritScaleY()
                ? childTransform.getScaleY() / nonZeroScale(parentTransform.getScaleY())
                : childTransform.getScaleY());
        binding.setFlipOffset(attachment.getState().isFlippedHorizontally()
                ^ parent.getState().isFlippedHorizontally());
    }

    private static double nonZeroScale(double value) {
        return Double.isFinite(value) && Math.abs(value) > 0.0001 ? value : 1.0;
    }

    private boolean resolveAttachedLight(VttLight light, VttSceneObject attachment) {
        var transform = attachment.getTransform();
        double localX = attachment.getState().isFlippedHorizontally()
                ? -light.getAttachmentOffsetX() : light.getAttachmentOffsetX();
        double localY = light.getAttachmentOffsetY();
        double radians = Math.toRadians(transform.getRotationDegrees());
        double x = transform.getX() + localX * transform.getScaleX() * Math.cos(radians)
                - localY * transform.getScaleY() * Math.sin(radians);
        double y = transform.getY() + localX * transform.getScaleX() * Math.sin(radians)
                + localY * transform.getScaleY() * Math.cos(radians);
        double localDirection = attachment.getState().isFlippedHorizontally()
                ? mirrorAttachmentDirection(light.getAttachmentDirectionOffsetDegrees())
                : light.getAttachmentDirectionOffsetDegrees();
        double direction = normalizeRotation(transform.getRotationDegrees() + localDirection);
        if (nearlyEqual(x, light.getX()) && nearlyEqual(y, light.getY())
                && nearlyEqual(direction, light.getDirectionDegrees())) return false;
        light.setX(x); light.setY(y); light.setDirectionDegrees(direction);
        return true;
    }

    private VttTokenTransformUpdatePayload transformUpdate(VttSceneObject object, long revision) {
        return new VttTokenTransformUpdatePayload(authorityRevision, revision, 0L,
                activeScene.getId(), object.getId(), object.getTransform().getX(),
                object.getTransform().getY(), object.getTransform().getRotationDegrees(),
                object.getTransform().getScaleX(), object.getTransform().getScaleY(),
                currentLayerIndex(object), object.getState().isFlippedHorizontally(),
                object.getState().isVisible(), object.getDisplayName(),
                object.getState().getActiveStateId(), object.getState().getTintColorRgb(),
                "server", true);
    }

    private static double mirrorAttachmentDirection(double degrees) {
        double value = (180.0 - degrees) % 360.0;
        return value < 0.0 ? value + 360.0 : value;
    }

    public record AttachmentDependencyUpdates(
            List<VttTokenTransformUpdatePayload> transforms,
            List<VttEnvironmentCommandUpdatePayload> lights
    ) {
        private static final AttachmentDependencyUpdates EMPTY =
                new AttachmentDependencyUpdates(List.of(), List.of());
    }

    public synchronized VttTokenLifecycleUpdatePayload applyTokenLifecycle(
            VttTokenLifecycleRequestPayload request, String playerId, boolean master
    ) {
        if (request == null || playerId == null || request.operation() == null
                || request.objectId() == null || request.objectId().isBlank()) return null;
        if ("CREATE".equals(request.operation())) {
            return master ? createSceneToken(request, playerId) : null;
        }
        if ("DELETE".equals(request.operation())) {
            VttSceneObject object = activeScene == null ? null : activeScene.getObjects().stream()
                    .filter(candidate -> candidate != null
                            && request.objectId().equals(candidate.getId()))
                    .findFirst().orElse(null);
            if (object == null || !master && !playerId.equals(object.getOwnerId())) return null;
            return deleteSceneToken(request.objectId(), playerId);
        }
        return null;
    }

    public synchronized VttTokenLifecycleUpdatePayload tokenLifecycleCorrection(String objectId) {
        if (activeScene == null || objectId == null || objectId.isBlank()) return null;
        VttSceneObject object = activeScene.getObjects().stream()
                .filter(candidate -> candidate != null && objectId.equals(candidate.getId()))
                .findFirst().orElse(null);
        if (object == null) {
            return new VttTokenLifecycleUpdatePayload(
                    "DELETE", objectId, objectId, "", "server");
        }
        return new VttTokenLifecycleUpdatePayload(
                "CREATE", objectId, objectId, GSON.toJson(object), "server");
    }

    private VttTokenLifecycleUpdatePayload createSceneToken(
            VttTokenLifecycleRequestPayload request, String playerId
    ) {
        if (request.objectJson() == null || request.objectJson().length() > 1_000_000
                || VttSceneLimits.tokenCreation(activeScene) != null) return null;
        try {
            VttSceneObject object = GSON.fromJson(request.objectJson(), VttSceneObject.class);
            if (!validSceneObject(object)) return null;
            String requestedId = normalizeObjectId(request.objectId());
            String authoritativeId = createUniqueObjectId(requestedId);
            object.setId(authoritativeId);
            int layerIndex = Math.max(0, Math.min(object.getLayerIndex(), activeScene.getObjects().size()));
            activeScene.getObjects().add(layerIndex, object);
            objectSpatialIndex.addOrUpdate(object);
            normalizeLayerIndices();
            markActiveSceneDirty();
            flushActiveSceneNow("token creation");
            return new VttTokenLifecycleUpdatePayload("CREATE", request.objectId(), authoritativeId,
                    GSON.toJson(object), playerId);
        } catch (RuntimeException exception) {
            VTT.LOGGER.warn("Could not decode VTT token creation", exception);
            return null;
        }
    }

    public synchronized CompositeAttachmentPlacementResult applyCompositeAttachmentPlacement(
            VttCompositeAttachmentPlacementData data, String playerId
    ) {
        if (activeScene == null || data == null || playerId == null
                || data.objects().isEmpty() || data.objects().size() > 65
                || data.lights().size() > 2_080
                || activeScene.getObjects().size() + data.objects().size() > VttSceneLimits.MAX_TOKENS
                || activeScene.getLights().size() + data.lights().size() > VttSceneLimits.MAX_LIGHTS) {
            return null;
        }
        java.util.LinkedHashSet<String> objectIds = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> lightIds = new java.util.LinkedHashSet<>();
        for (VttSceneObject object : data.objects()) {
            if (!validSceneObject(object) || !object.isAttachment()
                    || !objectIds.add(object.getId())
                    || activeScene.getObjects().stream().anyMatch(existing ->
                    existing != null && object.getId().equals(existing.getId()))) return null;
        }
        String rootId = data.objects().get(0).getId();
        if (data.objects().get(0).getAttachmentBinding() != null
                && data.objects().get(0).getAttachmentBinding().isBound()) return null;
        Map<String, VttSceneObject> requestedObjects = new HashMap<>();
        data.objects().forEach(object -> requestedObjects.put(object.getId(), object));
        for (int index = 1; index < data.objects().size(); index++) {
            VttSceneObject object = data.objects().get(index);
            var binding = object.getAttachmentBinding();
            if (binding == null || !binding.isBound()
                    || !objectIds.contains(binding.getTargetObjectId())) return null;
            Set<String> visited = new HashSet<>();
            String current = object.getId();
            boolean reachesRoot = false;
            for (int depth = 0;
                 depth <= com.petrick.vtt.feature.attachment.AttachmentBindingService.MAX_BINDING_DEPTH;
                 depth++) {
                if (!visited.add(current)) return null;
                if (rootId.equals(current)) { reachesRoot = true; break; }
                VttSceneObject currentObject = requestedObjects.get(current);
                var currentBinding = currentObject == null
                        ? null : currentObject.getAttachmentBinding();
                if (currentBinding == null || !currentBinding.isBound()) break;
                current = currentBinding.getTargetObjectId();
            }
            if (!reachesRoot) return null;
        }
        for (VttLight light : data.lights()) {
            if (!validCompositeLight(light) || !lightIds.add(light.getId())
                    || light.getAttachedToObjectId() == null
                    || !objectIds.contains(light.getAttachedToObjectId())
                    || activeScene.getLights().stream().anyMatch(existing ->
                    existing != null && light.getId().equals(existing.getId()))) return null;
        }

        List<VttTokenLifecycleUpdatePayload> objectUpdates = new ArrayList<>();
        List<VttEnvironmentCommandUpdatePayload> lightUpdates = new ArrayList<>();
        int layer = activeScene.getObjects().size();
        for (VttSceneObject object : data.objects()) {
            object.setLayerIndex(layer++);
            activeScene.addObject(object);
            objectSpatialIndex.addOrUpdate(object);
        }
        for (VttLight light : data.lights()) {
            activeScene.addLight(light);
        }
        // Resolve the complete hierarchy only after every referenced parent and light exists.
        synchronizeAttachmentDependencies(data.objects().get(0).getId());
        for (VttSceneObject object : data.objects()) {
            objectUpdates.add(new VttTokenLifecycleUpdatePayload(
                    "CREATE", object.getId(), object.getId(), GSON.toJson(object), playerId));
        }
        for (VttLight light : data.lights()) {
            String key = VttEnvironmentCommandPayload.LIGHT + "\u0000" + light.getId();
            lightUpdates.add(new VttEnvironmentCommandUpdatePayload(
                    authorityRevision, nextRevision(environmentRevisions, key), 0L,
                    VttEnvironmentCommandPayload.UPSERT, activeScene.getId(),
                    VttEnvironmentCommandPayload.LIGHT, light.getId(),
                    GSON.toJson(light), playerId));
        }
        normalizeLayerIndices();
        markActiveSceneDirty();
        flushActiveSceneNow("composite attachment placement");
        return new CompositeAttachmentPlacementResult(
                List.copyOf(objectUpdates), List.copyOf(lightUpdates));
    }

    private boolean validCompositeLight(VttLight light) {
        return light != null && light.getId() != null && !light.getId().isBlank()
                && Double.isFinite(light.getX()) && Double.isFinite(light.getY())
                && Double.isFinite(light.getInnerRadius())
                && Double.isFinite(light.getOuterRadius())
                && Double.isFinite(light.getIntensity())
                && light.getInnerRadius() >= 0.0 && light.getOuterRadius() >= 1.0
                && light.getInnerRadius() <= light.getOuterRadius()
                && light.getOuterRadius() <= 100_000.0
                && light.getIntensity() >= VttLight.MIN_INTENSITY
                && light.getIntensity() <= VttLight.MAX_INTENSITY;
    }

    public record CompositeAttachmentPlacementResult(
            List<VttTokenLifecycleUpdatePayload> objects,
            List<VttEnvironmentCommandUpdatePayload> lights
    ) {}

    private VttTokenLifecycleUpdatePayload deleteSceneToken(String objectId, String playerId) {
        VttSceneObject object = activeScene.getObjects().stream()
                .filter(candidate -> candidate != null && objectId.equals(candidate.getId()))
                .findFirst().orElse(null);
        if (object == null) return null;
        activeScene.removeObject(objectId);
        objectSpatialIndex.remove(objectId);
        activeScene.removeVisionSourceObjectId(objectId);
        normalizeLayerIndices();
        markActiveSceneDirty();
        flushActiveSceneNow("token deletion");
        return new VttTokenLifecycleUpdatePayload("DELETE", objectId, objectId, "", playerId);
    }

    private boolean validSceneObject(VttSceneObject object) {
        boolean token = object != null && object.getSourceTokenDefinitionId() != null
                && !object.getSourceTokenDefinitionId().isBlank();
        boolean attachment = object != null && object.getSourceAttachmentDefinitionId() != null
                && !object.getSourceAttachmentDefinitionId().isBlank();
        if (object == null || object.getDisplayName() == null || object.getDisplayName().isBlank()
                || token == attachment
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
            if (walls == null || doors == null || fog == null || visionStates == null) return null;
            int requestedFogAreas = fog.getHiddenAreas().size() + fog.getRevealedAreas().size();
            int requestedVisionSources = (int) visionStates.stream()
                    .filter(vision -> vision != null && vision.enabled()).count();
            if (!VttSceneLimits.replacementAllowed(
                    activeScene.getWalls().size(), walls.size(), VttSceneLimits.MAX_WALLS)
                    || !VttSceneLimits.replacementAllowed(
                    activeScene.getDoors().size(), doors.size(), VttSceneLimits.MAX_DOORS)
                    || !VttSceneLimits.replacementAllowed(
                    VttSceneLimits.fogAreaCount(activeScene), requestedFogAreas,
                    VttSceneLimits.MAX_FOG_AREAS)
                    || !VttSceneLimits.replacementAllowed(
                    activeScene.getObjects().size(), visionStates.size(),
                    VttSceneLimits.MAX_TOKENS)
                    || !VttSceneLimits.replacementAllowed(
                    VttSceneLimits.enabledVisionSourceCount(activeScene), requestedVisionSources,
                    VttSceneLimits.MAX_VISION_SOURCES)) return null;
            activeScene.clearWalls();
            walls.stream().filter(wall -> wall != null && wall.getId() != null && !wall.getId().isBlank())
                    .forEach(activeScene::addWall);
            activeScene.clearDoors();
            doors.stream().filter(door -> door != null && door.getId() != null && !door.getId().isBlank())
                    .forEach(activeScene::addDoor);
            activeScene.setFogOfWar(fog);
            visionGeometryIndex.rebuild(activeScene);
            movementCollision.rebuildObstacleIndex(activeScene);
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
                            object.setVisionOwnLightEnabled(vision.ownLightEnabled());
                            object.getState().setTintColorRgb(vision.tintColorRgb());
                            if (validCollisionBox(vision.collisionBox(), false)) {
                                object.setCollisionBox(vision.collisionBox());
                            }
                        });
            }
            markActiveSceneDirty();
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
                case VttEnvironmentCommandPayload.MAP -> delete
                        ? activeScene.removeMap(command.entityId())
                        : upsertMap(command.entityId(), command.entityJson());
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
                case VttEnvironmentCommandPayload.GRID_CONFIG -> !delete
                        && applyGridConfig(command.entityJson());
                case VttEnvironmentCommandPayload.LIGHTING_CONFIG -> !delete
                        && applyLightingConfig(command.entityJson());
                case VttEnvironmentCommandPayload.LIGHTING_COLOR -> !delete
                        && applyLightingColor(command.entityJson());
                case VttEnvironmentCommandPayload.BACKGROUND_CONFIG -> !delete
                        && applyBackgroundConfig(command.entityJson());
                case VttEnvironmentCommandPayload.CAMERA_CONFIG -> delete
                        ? clearInitialCameraView()
                        : applyInitialCameraView(command.entityJson());
                case VttEnvironmentCommandPayload.VISION -> delete
                        || applyVisionState(command.entityId(), command.entityJson());
                case VttEnvironmentCommandPayload.LIGHT -> delete
                        ? activeScene.removeLight(command.entityId())
                        : upsertLight(command.entityId(), command.entityJson());
                case VttEnvironmentCommandPayload.ATTACHMENT_BINDING -> delete
                        ? clearAttachmentBinding(command.entityId())
                        : applyAttachmentBinding(command.entityId(), command.entityJson());
                case VttEnvironmentCommandPayload.TOKEN_STATE_OVERRIDE -> !delete
                        && applyTokenStateOverrides(command.entityId(), command.entityJson());
                default -> false;
            };
            if (!changed) return null;
            if (VttEnvironmentCommandPayload.WALL.equals(command.entityType())
                    || VttEnvironmentCommandPayload.DOOR.equals(command.entityType())) {
                visionGeometryIndex.rebuild(activeScene);
                movementCollision.rebuildObstacleIndex(activeScene);
            }
            lastEnvironmentSequences.put(sequenceKey, command.clientSequence());

            if (!delete) confirmedJson = authoritativeEnvironmentJson(
                    command.entityType(), command.entityId());
            markActiveSceneDirty();
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

    public synchronized VttEnvironmentCommandUpdatePayload environmentCorrection(
            VttEnvironmentCommandPayload command
    ) {
        if (command == null || activeScene == null || command.entityType() == null
                || command.entityId() == null || command.entityId().isBlank()
                || !activeScene.getId().equals(command.sceneId())) return null;
        String revisionKey = command.entityType() + "\u0000" + command.entityId();
        String authoritativeJson = authoritativeEnvironmentJsonOrNull(
                command.entityType(), command.entityId());
        String operation = authoritativeJson == null
                ? VttEnvironmentCommandPayload.DELETE : VttEnvironmentCommandPayload.UPSERT;
        return new VttEnvironmentCommandUpdatePayload(
                authorityRevision, nextRevision(environmentRevisions, revisionKey),
                Math.max(0L, command.clientSequence()), operation, activeScene.getId(),
                command.entityType(), command.entityId(),
                authoritativeJson == null ? "" : authoritativeJson, "server");
    }

    public synchronized VttSceneLimits.Violation environmentLimitViolation(
            VttEnvironmentCommandPayload command
    ) {
        if (command == null || !VttEnvironmentCommandPayload.UPSERT.equals(command.operation())) {
            return null;
        }
        if (activeScene == null || command.authorityRevision() != authorityRevision
                || command.sceneId() == null || !activeScene.getId().equals(command.sceneId())) {
            return null;
        }
        if (VttEnvironmentCommandPayload.VISION.equals(command.entityType())) {
            try {
                VisionState vision = GSON.fromJson(command.entityJson(), VisionState.class);
                return vision == null ? null : VttSceneLimits.visionEnable(
                        activeScene, command.entityId(), vision.enabled());
            } catch (RuntimeException exception) {
                return null;
            }
        }
        return VttSceneLimits.environmentUpsert(
                activeScene, command.entityType(), command.entityId());
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
                wall.getTransform(), wall.getSize())
                || !exists && VttSceneLimits.environmentUpsert(
                activeScene, VttEnvironmentCommandPayload.WALL, id) != null) return false;
        activeScene.getWalls().removeIf(value -> value != null && id.equals(value.getId()));
        activeScene.addWall(wall);
        return true;
    }

    private boolean applyAttachmentBinding(String attachmentId, String json) {
        VttSceneObject attachment = activeScene.getObjects().stream()
                .filter(object -> object != null && attachmentId.equals(object.getId()))
                .findFirst().orElse(null);
        VttAttachmentBinding binding = GSON.fromJson(json, VttAttachmentBinding.class);
        if (attachment == null || !attachment.isAttachment() || binding == null
                || !binding.isBound() || binding.getTargetObjectId() == null
                || attachmentId.equals(binding.getTargetObjectId())) return false;
        if (!validAttachmentBindingChain(attachmentId, binding.getTargetObjectId())) return false;
        attachment.setAttachmentBinding(binding);
        return true;
    }

    private boolean validAttachmentBindingChain(String attachmentId, String targetId) {
        String current = targetId;
        Set<String> visited = new HashSet<>();
        for (int depth = 0;
             depth < com.petrick.vtt.feature.attachment.AttachmentBindingService.MAX_BINDING_DEPTH;
             depth++) {
            if (current == null || attachmentId.equals(current) || !visited.add(current)) return false;
            String lookupId = current;
            VttSceneObject target = activeScene.getObjects().stream()
                    .filter(object -> object != null && lookupId.equals(object.getId()))
                    .findFirst().orElse(null);
            if (target == null) return false;
            if (target.getSourceTokenDefinitionId() != null
                    && !target.getSourceTokenDefinitionId().isBlank()) return true;
            if (!target.isAttachment() || target.getAttachmentBinding() == null
                    || !target.getAttachmentBinding().isBound()) return true;
            current = target.getAttachmentBinding().getTargetObjectId();
        }
        return false;
    }

    private boolean clearAttachmentBinding(String attachmentId) {
        VttSceneObject attachment = activeScene.getObjects().stream()
                .filter(object -> object != null && attachmentId.equals(object.getId()))
                .findFirst().orElse(null);
        if (attachment == null || !attachment.isAttachment()
                || attachment.getAttachmentBinding() == null) return false;
        attachment.setAttachmentBinding(null);
        return true;
    }

    private boolean applyTokenStateOverrides(String tokenId, String json) {
        VttSceneObject token = activeScene.getObjects().stream()
                .filter(object -> object != null && tokenId.equals(object.getId())
                        && object.getSourceTokenDefinitionId() != null)
                .findFirst().orElse(null);
        TokenStateOverrideSnapshot snapshot = GSON.fromJson(json, TokenStateOverrideSnapshot.class);
        if (token == null || snapshot == null
                || !validAppearance(snapshot.globalAppearance())
                || snapshot.stateAppearances() == null
                || snapshot.stateAppearances().size() > 64
                || snapshot.stateAppearances().entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getKey().isBlank()
                        || entry.getKey().length() > 128 || !validAppearance(entry.getValue()))) {
            return false;
        }
        token.setGlobalStateAppearance(snapshot.globalAppearance().copy());
        Map<String, com.petrick.vtt.feature.tabletop.VttTokenStateAppearance> copies =
                new LinkedHashMap<>();
        snapshot.stateAppearances().forEach(
                (stateId, appearance) -> copies.put(stateId, appearance.copy()));
        token.setStateAppearances(copies);
        return true;
    }

    private boolean validAppearance(
            com.petrick.vtt.feature.tabletop.VttTokenStateAppearance appearance
    ) {
        return appearance != null && Double.isFinite(appearance.getScaleX())
                && Double.isFinite(appearance.getScaleY())
                && Double.isFinite(appearance.getRotationDegrees())
                && appearance.getScaleX() >= 0.0001
                && appearance.getScaleX() <= 1_000.0
                && appearance.getScaleY() >= 0.0001
                && appearance.getScaleY() <= 1_000.0;
    }

    private boolean upsertMap(String id, String json) {
        com.petrick.vtt.feature.tabletop.VttSceneMap map = GSON.fromJson(
                json, com.petrick.vtt.feature.tabletop.VttSceneMap.class);
        boolean exists = activeScene.getMaps().stream().anyMatch(
                value -> value != null && id.equals(value.getId()));
        if (map == null || !id.equals(map.getId())
                || map.getDisplayName() == null || map.getDisplayName().isBlank()
                || map.getDisplayName().length() > 128
                || map.getSourceMapDefinitionId() == null
                || map.getSourceMapDefinitionId().isBlank()
                || map.getSourceMapDefinitionId().length() > 128
                || map.getAssetId() == null || map.getAssetId().length() > 512
                || validateBackgroundAssetId(map.getAssetId()) == null
                || map.getLayerIndex() < 0 || map.getLayerIndex() > VttSceneLimits.MAX_MAPS
                || !validBackgroundTransform(map.getTransform())
                || !exists && VttSceneLimits.environmentUpsert(
                activeScene, VttEnvironmentCommandPayload.MAP, id) != null) return false;
        activeScene.removeMap(id);
        activeScene.addMap(map);
        return true;
    }

    private boolean validBackgroundTransform(
            com.petrick.vtt.feature.tabletop.VttSceneBackgroundTransform transform
    ) {
        return transform != null && Double.isFinite(transform.getX())
                && Double.isFinite(transform.getY())
                && Double.isFinite(transform.getScaleX())
                && Double.isFinite(transform.getScaleY())
                && Math.abs(transform.getX()) <= 10_000_000.0
                && Math.abs(transform.getY()) <= 10_000_000.0
                && transform.getScaleX() >= 0.01 && transform.getScaleX() <= 1_000.0
                && transform.getScaleY() >= 0.01 && transform.getScaleY() <= 1_000.0;
    }

    private boolean upsertDoor(String id, String json) {
        VttDoor door = GSON.fromJson(json, VttDoor.class);
        boolean exists = activeScene.getDoors().stream().anyMatch(
                value -> value != null && id.equals(value.getId()));
        if (door == null || !id.equals(door.getId()) || !validGeometry(
                door.getTransform(), door.getSize())
                || !exists && VttSceneLimits.environmentUpsert(
                activeScene, VttEnvironmentCommandPayload.DOOR, id) != null) return false;
        if (door.getWallId() != null && activeScene.getWalls().stream().noneMatch(
                wall -> wall != null && door.getWallId().equals(wall.getId()))) return false;
        activeScene.getDoors().removeIf(value -> value != null && id.equals(value.getId()));
        activeScene.addDoor(door);
        return true;
    }

    private boolean upsertFogArea(String id, String json, boolean revealed) {
        VttFogArea area = GSON.fromJson(json, VttFogArea.class);
        boolean exists = activeScene.getFogOfWar().getHiddenAreas().stream().anyMatch(
                value -> value != null && id.equals(value.getId()))
                || activeScene.getFogOfWar().getRevealedAreas().stream().anyMatch(
                value -> value != null && id.equals(value.getId()));
        if (area == null || !id.equals(area.getId()) || !validGeometry(
                area.getTransform(), area.getSize())
                || !exists && VttSceneLimits.environmentUpsert(activeScene,
                revealed ? VttEnvironmentCommandPayload.FOG_REVEALED
                        : VttEnvironmentCommandPayload.FOG_HIDDEN, id) != null) return false;
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

    private boolean applyGridConfig(String json) {
        VttSceneGrid grid = GSON.fromJson(json, VttSceneGrid.class);
        if (grid == null) return false;
        grid.normalize();
        activeScene.setGrid(grid);
        return true;
    }

    private boolean applyLightingConfig(String json) {
        LightingRaycastConfig lighting = GSON.fromJson(json, LightingRaycastConfig.class);
        if (lighting == null) return false;
        activeScene.getLighting().setVisionRayCount(lighting.visionRayCount());
        return true;
    }

    private boolean applyLightingColor(String json) {
        DarknessColorConfig color = GSON.fromJson(json, DarknessColorConfig.class);
        if (color == null) return false;
        activeScene.getLighting().setDarknessColorRgb(color.darknessColorRgb());
        return true;
    }

    private boolean applyBackgroundConfig(String json) {
        var transform = GSON.fromJson(
                json, com.petrick.vtt.feature.tabletop.VttSceneBackgroundTransform.class);
        if (transform == null || !Double.isFinite(transform.getX())
                || !Double.isFinite(transform.getY())
                || !Double.isFinite(transform.getScaleX())
                || !Double.isFinite(transform.getScaleY())
                || Math.abs(transform.getX()) > 10_000_000.0
                || Math.abs(transform.getY()) > 10_000_000.0
                || transform.getScaleX() < 0.01 || transform.getScaleX() > 1_000.0
                || transform.getScaleY() < 0.01 || transform.getScaleY() > 1_000.0) {
            return false;
        }
        activeScene.setBackgroundTransform(transform);
        return true;
    }

    private boolean applyInitialCameraView(String json) {
        var view = GSON.fromJson(
                json, com.petrick.vtt.feature.tabletop.VttSceneCameraView.class);
        if (view == null || !Double.isFinite(view.getX())
                || !Double.isFinite(view.getY()) || !Double.isFinite(view.getZoom())
                || Math.abs(view.getX()) > 10_000_000.0
                || Math.abs(view.getY()) > 10_000_000.0
                || view.getZoom() < 0.1 || view.getZoom() > 8.0) return false;
        activeScene.setInitialCameraView(view);
        return true;
    }

    private boolean clearInitialCameraView() {
        activeScene.clearInitialCameraView();
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
        if (object == null || VttSceneLimits.visionEnable(
                activeScene, id, vision.enabled()) != null) return false;
        object.setVisionInnerRadius(vision.innerRadius());
        object.setVisionOuterRadius(vision.outerRadius());
        object.setVisionEnabled(vision.enabled());
        object.setVisionOwnLightEnabled(vision.ownLightEnabled());
        object.getState().setTintColorRgb(vision.tintColorRgb());
        object.setCollisionBox(vision.collisionBox());
        return true;
    }

    private boolean upsertLight(String id, String json) {
        var light = GSON.fromJson(json, com.petrick.vtt.feature.tabletop.VttLight.class);
        boolean exists = activeScene.getLights().stream().anyMatch(
                value -> value != null && id.equals(value.getId()));
        if (light == null || !id.equals(light.getId())
                || light.getType() != com.petrick.vtt.feature.tabletop.VttLightType.POINT
                && light.getType() != com.petrick.vtt.feature.tabletop.VttLightType.SPOT
                || !Double.isFinite(light.getX()) || !Double.isFinite(light.getY())
                || !Double.isFinite(light.getInnerRadius())
                || !Double.isFinite(light.getOuterRadius())
                || !Double.isFinite(light.getIntensity())
                || !Double.isFinite(light.getDirectionDegrees())
                || !Double.isFinite(light.getConeAngleDegrees())
                || !Double.isFinite(light.getInnerConeAngleDegrees())
                || Math.abs(light.getX()) > 10_000_000.0
                || Math.abs(light.getY()) > 10_000_000.0
                || light.getInnerRadius() < 0.0
                || light.getOuterRadius() < 1.0
                || light.getOuterRadius() > 100_000.0
                || light.getInnerRadius() > light.getOuterRadius()
                || light.getIntensity() < com.petrick.vtt.feature.tabletop.VttLight.MIN_INTENSITY
                || light.getIntensity() > com.petrick.vtt.feature.tabletop.VttLight.MAX_INTENSITY
                || light.getConeAngleDegrees()
                < com.petrick.vtt.feature.tabletop.VttLight.MIN_CONE_ANGLE_DEGREES
                || light.getConeAngleDegrees()
                > com.petrick.vtt.feature.tabletop.VttLight.MAX_CONE_ANGLE_DEGREES
                || light.getInnerConeAngleDegrees() <= 0.0
                || light.getInnerConeAngleDegrees() > light.getConeAngleDegrees()
                || !exists && VttSceneLimits.environmentUpsert(
                activeScene, VttEnvironmentCommandPayload.LIGHT, id) != null) return false;
        activeScene.addLight(light);
        return true;
    }

    private String authoritativeEnvironmentJson(String type, String id) {
        return switch (type) {
            case VttEnvironmentCommandPayload.MAP -> GSON.toJson(activeScene.getMaps().stream()
                    .filter(value -> value != null && id.equals(value.getId()))
                    .findFirst().orElseThrow());
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
            case VttEnvironmentCommandPayload.GRID_CONFIG ->
                    GSON.toJson(activeScene.getGrid());
            case VttEnvironmentCommandPayload.LIGHTING_CONFIG ->
                    GSON.toJson(new LightingRaycastConfig(
                            activeScene.getLighting().getVisionRayCount()));
            case VttEnvironmentCommandPayload.LIGHTING_COLOR ->
                    GSON.toJson(new DarknessColorConfig(
                            activeScene.getLighting().getDarknessColorRgb()));
            case VttEnvironmentCommandPayload.BACKGROUND_CONFIG ->
                    GSON.toJson(activeScene.getBackgroundTransform());
            case VttEnvironmentCommandPayload.CAMERA_CONFIG -> {
                if (activeScene.getInitialCameraView() == null) {
                    throw new IllegalStateException("Scene has no initial camera view");
                }
                yield GSON.toJson(activeScene.getInitialCameraView());
            }
            case VttEnvironmentCommandPayload.VISION -> {
                VttSceneObject object = activeScene.getObjects().stream()
                        .filter(value -> value != null && id.equals(value.getId())).findFirst().orElseThrow();
                yield GSON.toJson(new VisionState(id, object.getVisionInnerRadius(),
                        object.getVisionOuterRadius(), object.isVisionEnabled(),
                        object.isVisionOwnLightEnabled(), object.getState().getTintColorRgb(),
                        object.getCollisionBox()));
            }
            case VttEnvironmentCommandPayload.LIGHT -> GSON.toJson(
                    activeScene.getLights().stream()
                            .filter(value -> value != null && id.equals(value.getId()))
                            .findFirst().orElseThrow());
            case VttEnvironmentCommandPayload.ATTACHMENT_BINDING -> GSON.toJson(
                    activeScene.getObjects().stream()
                            .filter(value -> value != null && id.equals(value.getId()))
                            .map(VttSceneObject::getAttachmentBinding)
                            .filter(java.util.Objects::nonNull)
                            .findFirst().orElseThrow());
            case VttEnvironmentCommandPayload.TOKEN_STATE_OVERRIDE -> {
                VttSceneObject object = activeScene.getObjects().stream()
                        .filter(value -> value != null && id.equals(value.getId()))
                        .findFirst().orElseThrow();
                yield GSON.toJson(new TokenStateOverrideSnapshot(
                        object.getGlobalStateAppearance(), object.getStateAppearances()));
            }
            default -> throw new IllegalArgumentException("Unknown environment entity type");
        };
    }

    private String authoritativeEnvironmentJsonOrNull(String type, String id) {
        try {
            return authoritativeEnvironmentJson(type, id);
        } catch (RuntimeException exception) {
            return null;
        }
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
    private record LightingRaycastConfig(int visionRayCount) {}
    private record DarknessColorConfig(int darknessColorRgb) {}
    private record TokenStateOverrideSnapshot(
            com.petrick.vtt.feature.tabletop.VttTokenStateAppearance globalAppearance,
            Map<String, com.petrick.vtt.feature.tabletop.VttTokenStateAppearance> stateAppearances
    ) {}

    public synchronized VttEnvironmentStateUpdatePayload currentEnvironmentState() {
        return currentEnvironmentState(null);
    }

    public synchronized VttEnvironmentStateUpdatePayload currentEnvironmentState(ServerPlayer player) {
        var allowedIds = player == null || VttServerPlayerEvents.canViewFullTabletop(player)
                ? null : VttServerVisionSourceSync.visibleObjectsFor(player, this).stream()
                .map(VttSceneObject::getId).collect(java.util.stream.Collectors.toSet());
        return new VttEnvironmentStateUpdatePayload(
                GSON.toJson(activeScene.getWalls()), GSON.toJson(activeScene.getDoors()),
                GSON.toJson(activeScene.getFogOfWar()),
                GSON.toJson(activeScene.getObjects().stream()
                        .filter(object -> object != null && object.getId() != null)
                        .filter(object -> allowedIds == null || allowedIds.contains(object.getId()))
                        .map(object -> new VisionState(object.getId(), object.getVisionInnerRadius(),
                                object.getVisionOuterRadius() > 0.0
                                        ? object.getVisionOuterRadius() : 512.0,
                                object.isVisionEnabled(), object.isVisionOwnLightEnabled(),
                                object.getState().getTintColorRgb(), object.getCollisionBox())).toList()));
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

    private record VisionState(
            String objectId, double innerRadius, double outerRadius, boolean enabled,
            boolean ownLightEnabled, int tintColorRgb, VttSceneCollisionBox collisionBox) {}

    private boolean valid(VttTokenTransformRequestPayload request) {
        return request.authorityRevision() > 0L && request.clientSequence() > 0L
                && request.objectId() != null && !request.objectId().isBlank()
                && request.displayName() != null && !request.displayName().isBlank()
                && request.displayName().length() <= 128
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
