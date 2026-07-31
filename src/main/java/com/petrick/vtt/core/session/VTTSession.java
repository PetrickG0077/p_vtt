package com.petrick.vtt.core.session;

import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.DebugAssets;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.token.DebugTokenDefinitions;
import com.petrick.vtt.feature.token.TokenDefinitionRegistry;
import com.petrick.vtt.feature.token.persistence.CreatedTokenStorage;
import com.petrick.vtt.feature.map.MapDefinitionRegistry;
import com.petrick.vtt.feature.map.MapDefinition;
import com.petrick.vtt.feature.map.persistence.CreatedMapStorage;
import com.petrick.vtt.feature.tabletop.persistence.TabletopStoragePaths;
import com.petrick.vtt.feature.asset.library.AssetLibraryConfig;
import com.petrick.vtt.feature.asset.library.AssetLibraryService;
import com.petrick.vtt.feature.asset.library.AssetLibraryScanResult;
import com.petrick.vtt.feature.asset.library.AssetLibraryPath;
import com.petrick.vtt.feature.asset.library.AssetLibraryScanner;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnailLoader;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnailRegistry;
import com.petrick.vtt.feature.asset.animation.AnimatedTextureService;
import com.petrick.vtt.feature.asset.folder.VttAssetFolderService;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneLimits;
import com.petrick.vtt.feature.tabletop.VttSceneMap;
import com.petrick.vtt.feature.tabletop.VttTabletop;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.feature.tabletop.persistence.TabletopStorage;
import com.petrick.vtt.feature.tabletop.persistence.CanvasSceneToVttSceneMapper;
import com.petrick.vtt.feature.tabletop.persistence.VttSceneDuplicator;
import com.petrick.vtt.feature.tabletop.persistence.VttSceneToCanvasSceneMapper;
import com.petrick.vtt.feature.tabletop.vision.AuthoritativeVisionRegion;
import net.minecraft.client.Minecraft;
import com.petrick.vtt.network.client.VttClientAssetCache;

import java.nio.file.Path;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.transform.Transform2D;
import com.petrick.vtt.network.payload.VttTokenTransformUpdatePayload;
import com.petrick.vtt.network.payload.VttSceneCommandPayload;
import com.petrick.vtt.network.payload.VttAssetFolderCommandPayload;
import com.petrick.vtt.network.payload.VttReplicationResyncRequestPayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Representa uma sessão ativa do VTT.
 *
 * Por enquanto ela existe apenas em memória.
 */
public final class VTTSession {

    private final AssetRegistry assetRegistry;

    private final TokenDefinitionRegistry tokenDefinitionRegistry;

    private final MapDefinitionRegistry mapDefinitionRegistry;

    private final TabletopStoragePaths tabletopStoragePaths;

    private final CanvasScene canvasScene;

    private final AssetLibraryService assetLibraryService;

    private final AssetThumbnailRegistry assetThumbnailRegistry;

    private final AssetThumbnailLoader assetThumbnailLoader;

    private final AnimatedTextureService animatedTextureService;

    private final TabletopStorage tabletopStorage;
    private final VttAssetFolderService assetFolderService;

    private AssetLibraryScanResult assetLibraryScanResult;

    private VttTabletop activeTabletop;

    private VttScene activeScene;

    private VttRole localRole = VttRole.MASTER;
    private boolean localSpectator;

    private String localPlayerId;
    private long networkSnapshotVersion;
    private long networkAuthorityRevision;
    private long networkVisionRevision = -1L;
    private long networkReplicationRevision = -1L;
    private List<AuthoritativeVisionRegion> networkVisionRegions = List.of();
    private List<VttPlayerRosterEntry> networkPlayerRoster = List.of();
    private List<String> networkVisibleObjectIds = List.of();
    private boolean networkMaskWhenVisionEmpty = true;
    private boolean networkAuthorityActive;
    private boolean networkResyncPending;
    private long networkRevisionGapCount;
    private long networkResyncRequestCount;
    private long networkSpawnCount;
    private long networkDespawnCount;
    private long lastNetworkResyncRequestAt;
    private String lastNetworkRecoveryReason = "none";

    public VTTSession() {
        this.assetRegistry = new AssetRegistry();
        DebugAssets.registerAll(assetRegistry);

        this.assetLibraryService = new AssetLibraryService(
                new AssetLibraryConfig(Minecraft.getInstance().gameDirectory.toPath())
        );
        assetLibraryService.ensureDirectoriesExist();

        this.assetLibraryScanResult = assetLibraryService.scanLibrary();

        this.assetThumbnailRegistry = new AssetThumbnailRegistry();
        this.assetThumbnailLoader = new AssetThumbnailLoader(assetThumbnailRegistry);

        this.animatedTextureService = new AnimatedTextureService();

        this.tokenDefinitionRegistry = new TokenDefinitionRegistry();
        DebugTokenDefinitions.registerAll(tokenDefinitionRegistry, assetRegistry);
        this.mapDefinitionRegistry = new MapDefinitionRegistry();

        this.tabletopStoragePaths = new TabletopStoragePaths(
                Minecraft.getInstance().gameDirectory.toPath()
        );
        tabletopStoragePaths.ensureBaseFoldersExist();
        tabletopStoragePaths.ensureTabletopFoldersExist("default");

        this.tabletopStorage = new TabletopStorage(tabletopStoragePaths);
        this.activeTabletop = tabletopStorage.loadOrCreateDefaultTabletop();
        this.activeScene = tabletopStorage.loadOrCreateActiveScene(activeTabletop);
        this.activeTabletop.setSceneDisplayName(activeScene.getId(), activeScene.getDisplayName());

        CreatedTokenStorage.loadCreatedTokens(
                tokenDefinitionRegistry,
                assetRegistry
        );
        CreatedMapStorage.loadCreatedMaps(mapDefinitionRegistry);
        this.assetFolderService = new VttAssetFolderService(
                Minecraft.getInstance().gameDirectory.toPath(), activeTabletop.getId());
        this.assetFolderService.applyMetadata(
                activeTabletop, mapDefinitionRegistry, tokenDefinitionRegistry);
        tabletopStorage.saveTabletop(activeTabletop);

        this.canvasScene = CanvasScene.createDebugScene(assetRegistry);
        loadActiveSceneToCanvasScene();

    }

    public VttRole getLocalRole() {
        return localRole;
    }

    public void setLocalRole(VttRole localRole) {
        if (localRole == null) {
            this.localRole = VttRole.PLAYER;
            return;
        }

        this.localRole = localRole;
    }

    public boolean isLocalMaster() {
        return localRole == VttRole.MASTER;
    }

    public boolean requestAssetFolderCommand(
            String operation,
            VttAssetFolderService.Section section,
            String source,
            String value
    ) {
        if (!isLocalMaster() || operation == null || section == null) return false;
        String safeSource = source == null ? "" : source;
        String safeValue = value == null ? "" : value;
        if (networkAuthorityActive) {
            PacketDistributor.sendToServer(new VttAssetFolderCommandPayload(
                    networkAuthorityRevision, operation, section.name(),
                    safeSource, safeValue));
            return true;
        }
        boolean changed = switch (operation) {
            case VttAssetFolderCommandPayload.CREATE_FOLDER ->
                    assetFolderService.createFolder(section, safeSource, safeValue);
            case VttAssetFolderCommandPayload.RENAME_FOLDER ->
                    assetFolderService.renameFolder(section, safeSource, safeValue) != null;
            case VttAssetFolderCommandPayload.MOVE_FOLDER ->
                    assetFolderService.moveFolder(section, safeSource, safeValue) != null;
            case VttAssetFolderCommandPayload.MOVE_ITEM ->
                    assetFolderService.moveItem(section, safeSource, safeValue);
            case VttAssetFolderCommandPayload.DELETE_FOLDER ->
                    assetFolderService.deleteEmptyFolder(section, safeSource);
            case VttAssetFolderCommandPayload.MOVE_CONTENTS_AND_DELETE_FOLDER ->
                    assetFolderService.moveContentsToParentAndDelete(
                            section, safeSource);
            default -> false;
        };
        if (!changed) return false;
        assetFolderService.applyMetadata(
                activeTabletop, mapDefinitionRegistry, tokenDefinitionRegistry);
        tabletopStorage.saveTabletop(activeTabletop);
        return true;
    }

    public VttAssetFolderService.FolderInspection inspectAssetFolder(
            VttAssetFolderService.Section section, String folder
    ) {
        return assetFolderService.inspectFolder(section, folder);
    }

    public boolean isLocalSpectator() {
        return localSpectator && !isLocalMaster();
    }

    public String getLocalPlayerId() {
        if (localPlayerId == null) {
            var player = Minecraft.getInstance().player;
            localPlayerId = player == null ? "local_player" : player.getUUID().toString();
        }
        return localPlayerId;
    }

    public void setLocalPlayerId(String localPlayerId) {
        if (localPlayerId == null || localPlayerId.isBlank()) {
            return;
        }
        this.localPlayerId = localPlayerId.trim();
    }

    public void applyNetworkIdentity(String playerId, VttRole role, boolean spectator) {
        setLocalPlayerId(playerId);
        setLocalRole(role);
        localSpectator = spectator && role != VttRole.MASTER;
        networkAuthorityActive = true;
    }

    public boolean isNetworkAuthorityActive() {
        return networkAuthorityActive;
    }

    public List<VttPlayerRosterEntry> getNetworkPlayerRoster() {
        return networkPlayerRoster;
    }

    public void applyNetworkPlayerRoster(List<VttPlayerRosterEntry> roster) {
        networkPlayerRoster = roster == null ? List.of() : roster.stream()
                .filter(entry -> entry != null && !entry.id().isBlank())
                .distinct()
                .toList();
        networkPlayerRoster.stream()
                .filter(entry -> entry.id().equals(getLocalPlayerId()))
                .findFirst()
                .ifPresent(entry -> localSpectator =
                        entry.spectator() && entry.role() != VttRole.MASTER);
    }

    public Path getSyncedServerTokensFolder() {
        return VttClientAssetCache.activeCacheRoot().resolve("tokens");
    }

    public void applyNetworkSnapshot(VttTabletop tabletop, VttScene scene, long authorityRevision) {
        if (tabletop == null || scene == null) {
            VTT.LOGGER.warn("Ignored invalid VTT network snapshot");
            return;
        }

        boolean visionScopeChanged = this.activeScene == null
                || !scene.getId().equals(this.activeScene.getId())
                || this.networkAuthorityRevision != authorityRevision;
        this.activeTabletop = tabletop;
        this.activeScene = scene;
        this.networkAuthorityRevision = Math.max(0L, authorityRevision);
        networkVisionRevision = -1L;
        networkReplicationRevision = -1L;
        if (visionScopeChanged) {
            networkVisionRegions = List.of();
            networkMaskWhenVisionEmpty = true;
        }
        loadActiveSceneToCanvasScene();
        networkVisibleObjectIds = scene.getObjects().stream()
                .filter(object -> object != null && object.getId() != null && !object.getId().isBlank())
                .map(VttSceneObject::getId)
                .distinct()
                .toList();
        networkSnapshotVersion++;
        if (networkResyncPending) lastNetworkRecoveryReason = "recovered " + lastNetworkRecoveryReason;
        networkResyncPending = false;
        VTT.LOGGER.info("Applied VTT network snapshot for scene: {}", scene.getId());
    }

    public long getNetworkSnapshotVersion() {
        return networkSnapshotVersion;
    }

    public long getNetworkAuthorityRevision() {
        return networkAuthorityRevision;
    }

    public boolean hasNetworkSnapshot() {
        return networkSnapshotVersion > 0;
    }

    public void applyNetworkVisionSources(
            long authorityRevision, long visionRevision, String sceneId,
            boolean maskWhenEmpty, List<AuthoritativeVisionRegion> regions
    ) {
        if (!validNetworkScope(authorityRevision, sceneId, "VISION_SCOPE")) return;
        if (visionRevision <= networkVisionRevision) return;
        if (networkVisionRevision >= 0L && visionRevision > networkVisionRevision + 1L) {
            requestNetworkResync("VISION_GAP_" + networkVisionRevision + "_TO_" + visionRevision);
        }
        networkVisionRevision = visionRevision;
        networkVisionRegions = regions == null ? List.of()
                : regions.stream()
                .filter(region -> region != null && region.sourceObjectId() != null
                        && !region.sourceObjectId().isBlank() && region.origin() != null
                        && Double.isFinite(region.innerRadius())
                        && Double.isFinite(region.outerRadius())
                        && region.innerRadius() >= 0.0
                        && region.outerRadius() >= region.innerRadius()
                        && region.outerPolygon().size() >= 3)
                .toList();
        networkMaskWhenVisionEmpty = maskWhenEmpty;
    }

    public List<AuthoritativeVisionRegion> getNetworkVisionRegions() {
        return networkVisionRegions;
    }

    public List<String> getNetworkVisibleObjectIds() {
        return networkVisibleObjectIds;
    }

    public boolean shouldMaskWhenNetworkVisionEmpty() {
        return networkMaskWhenVisionEmpty;
    }

    public void applyNetworkReplication(
            long authorityRevision, long replicationRevision, String sceneId,
            List<VttSceneObject> spawnedObjects, List<String> despawnObjectIds
    ) {
        if (!validNetworkScope(authorityRevision, sceneId, "REPLICATION_SCOPE")) return;
        if (replicationRevision <= networkReplicationRevision || networkResyncPending) return;
        if (networkReplicationRevision >= 0L
                && replicationRevision > networkReplicationRevision + 1L) {
            requestNetworkResync(
                    "REPLICATION_GAP_" + networkReplicationRevision + "_TO_" + replicationRevision);
            return;
        }
        networkReplicationRevision = replicationRevision;
        if (activeScene == null || isLocalMaster()) return;
        List<VttSceneObject> safeObjects = spawnedObjects == null ? List.of()
                : spawnedObjects.stream()
                .filter(object -> object != null && object.getId() != null && !object.getId().isBlank())
                .toList();
        Set<String> despawnIds = despawnObjectIds == null ? Set.of()
                : despawnObjectIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        canvasScene.removeObjects(despawnIds);
        despawnIds.forEach(activeScene::removeObject);
        despawnIds.forEach(activeScene::removeVisionSourceObjectId);

        LinkedHashSet<String> visibleIds = new LinkedHashSet<>(networkVisibleObjectIds);
        visibleIds.removeAll(despawnIds);
        for (VttSceneObject spawned : safeObjects) {
            activeScene.removeObject(spawned.getId());
            activeScene.addObject(spawned);
            visibleIds.add(spawned.getId());
            if (canvasScene.findObjectById(spawned.getId()) != null) continue;
            var canvasObject = VttSceneToCanvasSceneMapper.convertObject(
                    spawned, tokenDefinitionRegistry);
            if (canvasObject == null) continue;
            canvasScene.addObject(canvasObject);
            canvasScene.moveObjectToLayer(canvasObject.id(), spawned.getLayerIndex());
        }
        networkVisibleObjectIds = List.copyOf(visibleIds);
        networkSpawnCount += safeObjects.size();
        networkDespawnCount += despawnIds.size();
        VTT.LOGGER.debug("Applied VTT replication R{}: {} spawn(s), {} despawn(s)",
                replicationRevision, safeObjects.size(), despawnIds.size());
    }

    private boolean validNetworkScope(long authorityRevision, String sceneId, String reason) {
        if (!networkAuthorityActive || activeScene == null) return false;
        boolean valid = authorityRevision == networkAuthorityRevision
                && sceneId != null && sceneId.equals(activeScene.getId());
        if (!valid && authorityRevision >= networkAuthorityRevision) {
            requestNetworkResync(reason);
        }
        return valid;
    }

    private void requestNetworkResync(String reason) {
        long now = System.currentTimeMillis();
        if (!networkAuthorityActive || activeScene == null
                || now - lastNetworkResyncRequestAt < 2_000L) return;
        lastNetworkResyncRequestAt = now;
        networkResyncPending = true;
        networkRevisionGapCount++;
        networkResyncRequestCount++;
        lastNetworkRecoveryReason = reason;
        VTT.LOGGER.warn("Requesting VTT replication recovery: {} (A={}, V={}, R={})",
                reason, networkAuthorityRevision, networkVisionRevision, networkReplicationRevision);
        PacketDistributor.sendToServer(new VttReplicationResyncRequestPayload(
                networkAuthorityRevision, activeScene.getId(), networkVisionRevision,
                networkReplicationRevision, reason));
    }

    public String getNetworkReplicationDiagnostics() {
        if (!networkAuthorityActive) return "Network: local session";
        return "Network: A=" + networkAuthorityRevision + " V=" + networkVisionRevision
                + " R=" + networkReplicationRevision + " visible=" + networkVisibleObjectIds.size();
    }

    public String getNetworkRecoveryDiagnostics() {
        return "Replication: spawn=" + networkSpawnCount + " despawn=" + networkDespawnCount
                + " gaps=" + networkRevisionGapCount + " resync=" + networkResyncRequestCount
                + (networkResyncPending ? " PENDING" : "") + " last=" + lastNetworkRecoveryReason;
    }

    public void applyConfirmedTokenTransform(VttTokenTransformUpdatePayload update) {
        if (update == null) return;
        var object = canvasScene.findObjectById(update.objectId());
        if (object == null) return;

        Transform2D transform = new Transform2D(
                new Vec2d(update.x(), update.y()), update.rotationDegrees(),
                new Vec2d(update.scaleX(), update.scaleY())
        );
        var replacement = object.withTransform(transform)
                .withFlippedHorizontally(update.flippedHorizontally())
                .withVisible(update.visible())
                .withActiveState(update.activeStateId())
                .withDisplayName(update.displayName());
        canvasScene.replaceObject(replacement);
        canvasScene.moveObjectToLayer(update.objectId(), update.layerIndex());

        if (activeScene != null) {
            activeScene.getObjects().stream()
                    .filter(sceneObject -> sceneObject != null && update.objectId().equals(sceneObject.getId()))
                    .findFirst().ifPresent(sceneObject -> {
                        sceneObject.getTransform().setX(update.x());
                        sceneObject.getTransform().setY(update.y());
                        sceneObject.getTransform().setRotationDegrees(update.rotationDegrees());
                        sceneObject.getTransform().setScaleX(update.scaleX());
                        sceneObject.getTransform().setScaleY(update.scaleY());
                        sceneObject.getState().setFlippedHorizontally(update.flippedHorizontally());
                        sceneObject.getState().setVisible(update.visible());
                        sceneObject.getState().setActiveStateId(update.activeStateId());
                        sceneObject.setDisplayName(update.displayName());
                    });
            for (var sceneObject : activeScene.getObjects()) {
                if (sceneObject == null) continue;
                int layerIndex = canvasScene.getObjectLayerIndex(sceneObject.getId());
                if (layerIndex >= 0) sceneObject.setLayerIndex(layerIndex);
            }
        }
    }

    public TabletopStoragePaths getTabletopStoragePaths() {
        return tabletopStoragePaths;
    }

    public TabletopStorage getTabletopStorage() {
        return tabletopStorage;
    }

    public VttTabletop getActiveTabletop() {
        return activeTabletop;
    }

    public VttScene getActiveScene() {
        return activeScene;
    }

    public boolean switchToScene(String sceneId) {
        if (networkAuthorityActive) {
            if (!isLocalMaster() || activeTabletop == null || sceneId == null || sceneId.isBlank()
                    || !activeTabletop.getSceneIds().contains(sceneId)) return false;
            if (activeScene != null && sceneId.equals(activeScene.getId())) return true;
            PacketDistributor.sendToServer(new VttSceneCommandPayload(
                    networkAuthorityRevision, VttSceneCommandPayload.SWITCH,
                    sceneId, "", "", ""));
            return true;
        }
        if (activeTabletop == null || sceneId == null || sceneId.isBlank()) return false;
        if (!activeTabletop.getSceneIds().contains(sceneId)) {
            VTT.LOGGER.warn("Cannot switch to scene not registered in tabletop: {}", sceneId);
            return false;
        }
        if (activeScene != null && sceneId.equals(activeScene.getId())) return true;

        VttScene targetScene = tabletopStorage.loadScene(activeTabletop.getId(), sceneId);
        if (targetScene == null) {
            VTT.LOGGER.warn("Cannot switch to missing scene: {}", sceneId);
            return false;
        }

        saveCanvasSceneToActiveScene();
        activeScene = targetScene;
        activeTabletop.setActiveSceneId(sceneId);
        tabletopStorage.saveTabletop(activeTabletop);
        loadActiveSceneToCanvasScene();
        VTT.LOGGER.info("Switched active VTT scene to: {}", sceneId);
        return true;
    }

    public VttScene createScene(String displayName) {
        return createScene(displayName, null);
    }

    public VttScene createScene(String displayName, String backgroundAssetId) {
        if (networkAuthorityActive) {
            VTT.LOGGER.warn("Scene creation is not network-authoritative yet");
            return null;
        }
        if (activeTabletop == null || displayName == null || displayName.isBlank()) return null;
        String baseId = displayName.trim().toLowerCase().replace('\\', '/')
                .replaceAll("[^a-z0-9/_-]", "_").replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (baseId.isBlank()) baseId = "new_scene";
        String sceneId = baseId;
        int suffix = 2;
        while (activeTabletop.getSceneIds().contains(sceneId)) sceneId = baseId + "_" + suffix++;

        saveCanvasSceneToActiveScene();
        VttScene createdScene = new VttScene(sceneId, displayName.trim());
        createdScene.setBackgroundAssetId(backgroundAssetId);
        tabletopStorage.saveScene(activeTabletop.getId(), createdScene);
        activeTabletop.addSceneId(sceneId);
        activeTabletop.setSceneDisplayName(sceneId, displayName.trim());
        activeTabletop.setActiveSceneId(sceneId);
        activeScene = createdScene;
        tabletopStorage.saveTabletop(activeTabletop);
        loadActiveSceneToCanvasScene();
        VTT.LOGGER.info("Created and activated VTT scene: {}", sceneId);
        return createdScene;
    }

    public boolean requestCreateScene(String displayName, String backgroundAssetId) {
        if (!networkAuthorityActive) {
            return createScene(displayName, backgroundAssetId) != null;
        }
        if (!isLocalMaster() || displayName == null || displayName.isBlank()
                || displayName.length() > 48
                || backgroundAssetId != null && backgroundAssetId.length() > 512) return false;
        PacketDistributor.sendToServer(new VttSceneCommandPayload(
                networkAuthorityRevision, VttSceneCommandPayload.CREATE, "",
                displayName.trim(), backgroundAssetId == null ? "" : backgroundAssetId, ""));
        return true;
    }

    public boolean requestCreateScene(String displayName, MapDefinition initialMap) {
        if (!networkAuthorityActive) {
            VttScene created = createScene(displayName, null);
            if (created == null) return false;
            if (initialMap != null) {
                created.addMap(createInitialSceneMap(initialMap));
                tabletopStorage.saveScene(activeTabletop.getId(), created);
            }
            return true;
        }
        if (!isLocalMaster() || displayName == null || displayName.isBlank()
                || displayName.length() > 48
                || initialMap != null && (initialMap.id().length() > 128
                || initialMap.assetId().length() > 512)) return false;
        PacketDistributor.sendToServer(new VttSceneCommandPayload(
                networkAuthorityRevision, VttSceneCommandPayload.CREATE,
                initialMap == null ? "" : initialMap.id(),
                displayName.trim(), initialMap == null ? "" : initialMap.assetId(),
                initialMap == null ? "" : initialMap.textureMode().name()));
        return true;
    }

    private VttSceneMap createInitialSceneMap(MapDefinition definition) {
        return new VttSceneMap(
                "map_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                definition.displayName(), definition.id(), definition.assetId(),
                definition.textureMode());
    }

    public boolean requestDuplicateScene(String sceneId) {
        if (!isLocalMaster() || activeTabletop == null || sceneId == null
                || sceneId.isBlank() || !activeTabletop.getSceneIds().contains(sceneId)) {
            return false;
        }
        if (VttSceneLimits.sceneCreation(activeTabletop) != null) return false;
        if (networkAuthorityActive) {
            PacketDistributor.sendToServer(new VttSceneCommandPayload(
                    networkAuthorityRevision, VttSceneCommandPayload.DUPLICATE,
                    sceneId, "", "", ""));
            return true;
        }
        saveCanvasSceneToActiveScene();
        VttScene source = activeScene != null && sceneId.equals(activeScene.getId())
                ? activeScene : tabletopStorage.loadScene(activeTabletop.getId(), sceneId);
        if (source == null) return false;
        String displayName = activeTabletop.getSceneDisplayName(sceneId) + " Copy";
        String baseId = displayName.trim().toLowerCase().replace('\\', '/')
                .replaceAll("[^a-z0-9/_-]", "_").replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (baseId.isBlank()) baseId = "scene_copy";
        String duplicateId = baseId;
        int suffix = 2;
        while (activeTabletop.getSceneIds().contains(duplicateId)) {
            duplicateId = baseId + "_" + suffix++;
        }
        VttScene duplicate = VttSceneDuplicator.duplicate(
                source, duplicateId, displayName);
        if (duplicate == null
                || !tabletopStorage.saveScene(activeTabletop.getId(), duplicate)) {
            return false;
        }
        String sourceFolder = activeTabletop.getSceneFolder(sceneId);
        if (sourceFolder != null && !sourceFolder.isBlank()) {
            assetFolderService.moveItem(
                    VttAssetFolderService.Section.SCENES,
                    duplicateId, sourceFolder);
            assetFolderService.applyMetadata(
                    activeTabletop, mapDefinitionRegistry, tokenDefinitionRegistry);
        }
        activeTabletop.addSceneId(duplicateId);
        activeTabletop.setSceneDisplayName(duplicateId, displayName);
        activeTabletop.setActiveSceneId(duplicateId);
        activeScene = duplicate;
        if (!tabletopStorage.saveTabletop(activeTabletop)) return false;
        loadActiveSceneToCanvasScene();
        VTT.LOGGER.info("Duplicated and activated VTT scene: {} -> {}",
                sceneId, duplicateId);
        return true;
    }

    public boolean setActiveSceneBackground(String assetId) {
        if (activeScene == null || !isLocalMaster()) return false;
        if (networkAuthorityActive) {
            activeScene.setBackgroundAssetId(assetId);
            PacketDistributor.sendToServer(new VttSceneCommandPayload(
                    networkAuthorityRevision, VttSceneCommandPayload.SET_BACKGROUND,
                    "", assetId == null ? "" : assetId, "", ""));
            return true;
        }
        activeScene.setBackgroundAssetId(assetId);
        saveActiveTabletopAndScene();
        return true;
    }

    public boolean setActiveSceneTokenOwner(String objectId, String ownerId) {
        if (activeScene == null || !isLocalMaster()
                || objectId == null || objectId.isBlank()) return false;
        String normalizedOwner = ownerId == null || ownerId.isBlank() ? null : ownerId.trim();
        VttSceneObject object = activeScene.getObjects().stream()
                .filter(candidate -> candidate != null && objectId.equals(candidate.getId()))
                .findFirst().orElse(null);
        if (object == null || java.util.Objects.equals(
                object.getOwnerId(), normalizedOwner)) return false;
        object.setOwnerId(normalizedOwner);
        if (!networkAuthorityActive) saveActiveTabletopAndScene();
        return true;
    }

    public boolean renameScene(String sceneId, String displayName) {
        if (!isLocalMaster() || sceneId == null || sceneId.isBlank() || displayName == null
                || displayName.isBlank() || displayName.length() > 48
                || !activeTabletop.getSceneIds().contains(sceneId)) return false;
        if (networkAuthorityActive) {
            PacketDistributor.sendToServer(new VttSceneCommandPayload(
                    networkAuthorityRevision, VttSceneCommandPayload.RENAME,
                    sceneId, displayName.trim(), "", ""));
            return true;
        }
        VttScene target = sceneId.equals(activeScene.getId())
                ? activeScene : tabletopStorage.loadScene(activeTabletop.getId(), sceneId);
        if (target == null) return false;
        target.setDisplayName(displayName.trim());
        activeTabletop.setSceneDisplayName(sceneId, displayName.trim());
        tabletopStorage.saveScene(activeTabletop.getId(), target);
        tabletopStorage.saveTabletop(activeTabletop);
        return true;
    }

    public boolean deleteScene(String sceneId) {
        if (!isLocalMaster() || sceneId == null || sceneId.isBlank()
                || activeTabletop.getSceneIds().size() <= 1
                || !activeTabletop.getSceneIds().contains(sceneId)) return false;
        if (networkAuthorityActive) {
            PacketDistributor.sendToServer(new VttSceneCommandPayload(
                    networkAuthorityRevision, VttSceneCommandPayload.DELETE,
                    sceneId, "", "", ""));
            return true;
        }
        boolean deletingActive = sceneId.equals(activeScene.getId());
        VttScene replacement = null;
        if (deletingActive) {
            String replacementId = activeTabletop.getSceneIds().stream()
                    .filter(id -> !sceneId.equals(id)).findFirst().orElse(null);
            replacement = tabletopStorage.loadScene(activeTabletop.getId(), replacementId);
            if (replacement == null) return false;
        }
        if (!tabletopStorage.deleteScene(activeTabletop.getId(), sceneId)) return false;
        activeTabletop.removeSceneId(sceneId);
        if (deletingActive) {
            activeScene = replacement;
            activeTabletop.setActiveSceneId(replacement.getId());
            loadActiveSceneToCanvasScene();
        }
        tabletopStorage.saveTabletop(activeTabletop);
        return true;
    }

    public void saveActiveTabletopAndScene() {
        if (networkAuthorityActive) return;
        tabletopStorage.saveTabletop(activeTabletop);
        tabletopStorage.saveScene(activeTabletop.getId(), activeScene);
    }

    public void loadActiveSceneToCanvasScene() {
        if (activeScene == null) {
            return;
        }

        VttSceneToCanvasSceneMapper.copySceneObjectsToCanvas(
                activeScene,
                canvasScene,
                tokenDefinitionRegistry
        );
    }

    public void saveCanvasSceneToActiveScene() {
        if (networkAuthorityActive) return;
        if (activeTabletop == null || activeScene == null) {
            return;
        }

        CanvasSceneToVttSceneMapper.copyCanvasObjectsToScene(
                canvasScene,
                activeScene
        );

        tabletopStorage.saveTabletop(activeTabletop);
        tabletopStorage.saveScene(
                activeTabletop.getId(),
                activeScene
        );
    }

    public AssetRegistry getAssetRegistry() {
        return assetRegistry;
    }

    public AnimatedTextureService getAnimatedTextureService() {
        return animatedTextureService;
    }

    public AssetLibraryService getAssetLibraryService() {
        return assetLibraryService;
    }

    public AssetLibraryScanResult getAssetLibraryScanResult() {
        return assetLibraryScanResult;
    }

    public void refreshAssetLibrary() {
        if (networkAuthorityActive) {
            reloadSyncedServerAssets();
            return;
        }
        this.assetLibraryScanResult = assetLibraryService.scanLibrary();
        loadAssetThumbnails();
    }

    public void reloadSyncedServerAssets() {
        Path cacheRoot = VttClientAssetCache.activeCacheRoot();
        AssetLibraryScanResult synced = new AssetLibraryScanner(
                new AssetLibraryPath(cacheRoot.resolve("assets"))
        ).scan();
        this.assetLibraryScanResult = synced;

        assetRegistry.clear();
        DebugAssets.registerAll(assetRegistry);
        tokenDefinitionRegistry.clear();
        DebugTokenDefinitions.registerAll(tokenDefinitionRegistry, assetRegistry);
        assetThumbnailRegistry.clear();
        animatedTextureService.setServerCacheRoot(cacheRoot);
        loadAssetThumbnails();
        CreatedTokenStorage.loadCreatedTokensFromFolder(
                cacheRoot.resolve("tokens"), tokenDefinitionRegistry, assetRegistry
        );
        VTT.LOGGER.info("Loaded {} synchronized VTT assets from server", synced.totalCount());
    }

    public void restoreLocalSessionAfterDisconnect() {
        if (!networkAuthorityActive) return;
        networkAuthorityActive = false;
        networkSnapshotVersion = 0L;
        networkAuthorityRevision = 0L;
        networkVisionRevision = -1L;
        networkReplicationRevision = -1L;
        networkVisionRegions = List.of();
        networkPlayerRoster = List.of();
        networkVisibleObjectIds = List.of();
        networkMaskWhenVisionEmpty = true;
        networkResyncPending = false;
        networkRevisionGapCount = 0L;
        networkResyncRequestCount = 0L;
        networkSpawnCount = 0L;
        networkDespawnCount = 0L;
        lastNetworkResyncRequestAt = 0L;
        lastNetworkRecoveryReason = "none";
        localPlayerId = null;
        localRole = VttRole.MASTER;
        localSpectator = false;

        assetRegistry.clear();
        DebugAssets.registerAll(assetRegistry);
        tokenDefinitionRegistry.clear();
        DebugTokenDefinitions.registerAll(tokenDefinitionRegistry, assetRegistry);
        CreatedTokenStorage.loadCreatedTokens(tokenDefinitionRegistry, assetRegistry);

        assetLibraryScanResult = assetLibraryService.scanLibrary();
        assetThumbnailRegistry.clear();
        animatedTextureService.setUseServerCache(false);
        loadAssetThumbnails();

        activeTabletop = tabletopStorage.loadOrCreateDefaultTabletop();
        activeScene = tabletopStorage.loadOrCreateActiveScene(activeTabletop);
        assetFolderService.refresh();
        assetFolderService.applyMetadata(
                activeTabletop, mapDefinitionRegistry, tokenDefinitionRegistry);
        loadActiveSceneToCanvasScene();
        VTT.LOGGER.info("Restored local VTT session after leaving multiplayer server");
    }

    public TokenDefinitionRegistry getTokenDefinitionRegistry() {
        return tokenDefinitionRegistry;
    }

    public MapDefinitionRegistry getMapDefinitionRegistry() {
        return mapDefinitionRegistry;
    }

    public AssetThumbnailRegistry getAssetThumbnailRegistry() {
        return assetThumbnailRegistry;
    }

    private void loadAssetThumbnails() {
        for (var entry : assetLibraryScanResult.entries()) {
            assetThumbnailLoader.loadThumbnailIfNeeded(entry);
        }
    }

    public CanvasScene getCanvasScene() {
        return canvasScene;
    }
}
