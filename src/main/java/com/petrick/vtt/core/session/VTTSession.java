package com.petrick.vtt.core.session;

import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.DebugAssets;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.token.DebugTokenDefinitions;
import com.petrick.vtt.feature.token.TokenDefinitionRegistry;
import com.petrick.vtt.feature.token.persistence.CreatedTokenStorage;
import com.petrick.vtt.feature.tabletop.persistence.TabletopStoragePaths;
import com.petrick.vtt.feature.asset.library.AssetLibraryConfig;
import com.petrick.vtt.feature.asset.library.AssetLibraryService;
import com.petrick.vtt.feature.asset.library.AssetLibraryScanResult;
import com.petrick.vtt.feature.asset.library.AssetLibraryPath;
import com.petrick.vtt.feature.asset.library.AssetLibraryScanner;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnailLoader;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnailRegistry;
import com.petrick.vtt.feature.asset.animation.AnimatedTextureService;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttTabletop;
import com.petrick.vtt.feature.tabletop.persistence.TabletopStorage;
import com.petrick.vtt.feature.tabletop.persistence.CanvasSceneToVttSceneMapper;
import com.petrick.vtt.feature.tabletop.persistence.VttSceneToCanvasSceneMapper;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.transform.Transform2D;
import com.petrick.vtt.network.payload.VttTokenTransformUpdatePayload;
import com.petrick.vtt.network.payload.VttSceneCommandPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Representa uma sessão ativa do VTT.
 *
 * Por enquanto ela existe apenas em memória.
 */
public final class VTTSession {

    private final AssetRegistry assetRegistry;

    private final TokenDefinitionRegistry tokenDefinitionRegistry;

    private final TabletopStoragePaths tabletopStoragePaths;

    private final CanvasScene canvasScene;

    private final AssetLibraryService assetLibraryService;

    private final AssetThumbnailRegistry assetThumbnailRegistry;

    private final AssetThumbnailLoader assetThumbnailLoader;

    private final AnimatedTextureService animatedTextureService;

    private final TabletopStorage tabletopStorage;

    private AssetLibraryScanResult assetLibraryScanResult;

    private VttTabletop activeTabletop;

    private VttScene activeScene;

    private VttRole localRole = VttRole.MASTER;

    private String localPlayerId;
    private long networkSnapshotVersion;
    private boolean networkAuthorityActive;

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

        this.tabletopStoragePaths = new TabletopStoragePaths(
                Minecraft.getInstance().gameDirectory.toPath()
        );
        tabletopStoragePaths.ensureBaseFoldersExist();
        tabletopStoragePaths.ensureTabletopFoldersExist("default");

        this.tabletopStorage = new TabletopStorage(tabletopStoragePaths);
        this.activeTabletop = tabletopStorage.loadOrCreateDefaultTabletop();
        this.activeScene = tabletopStorage.loadOrCreateActiveScene(activeTabletop);

        CreatedTokenStorage.loadCreatedTokens(
                tokenDefinitionRegistry,
                assetRegistry
        );

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

    public void applyNetworkIdentity(String playerId, VttRole role) {
        setLocalPlayerId(playerId);
        setLocalRole(role);
        networkAuthorityActive = true;
    }

    public boolean isNetworkAuthorityActive() {
        return networkAuthorityActive;
    }

    public Path getSyncedServerTokensFolder() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config/vtt_assets/cache/server/tokens");
    }

    public void applyNetworkSnapshot(VttTabletop tabletop, VttScene scene) {
        if (tabletop == null || scene == null) {
            VTT.LOGGER.warn("Ignored invalid VTT network snapshot");
            return;
        }

        this.activeTabletop = tabletop;
        this.activeScene = scene;
        loadActiveSceneToCanvasScene();
        networkSnapshotVersion++;
        VTT.LOGGER.info("Applied VTT network snapshot for scene: {}", scene.getId());
    }

    public long getNetworkSnapshotVersion() {
        return networkSnapshotVersion;
    }

    public boolean hasNetworkSnapshot() {
        return networkSnapshotVersion > 0;
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
                .withActiveState(update.activeStateId());
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
                        sceneObject.getState().setActiveStateId(update.activeStateId());
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
                    VttSceneCommandPayload.SWITCH, sceneId));
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
        tabletopStorage.saveScene(activeTabletop.getId(), createdScene);
        activeTabletop.addSceneId(sceneId);
        activeTabletop.setActiveSceneId(sceneId);
        activeScene = createdScene;
        tabletopStorage.saveTabletop(activeTabletop);
        loadActiveSceneToCanvasScene();
        VTT.LOGGER.info("Created and activated VTT scene: {}", sceneId);
        return createdScene;
    }

    public boolean requestCreateScene(String displayName) {
        if (!networkAuthorityActive) return createScene(displayName) != null;
        if (!isLocalMaster() || displayName == null || displayName.isBlank()
                || displayName.length() > 48) return false;
        PacketDistributor.sendToServer(new VttSceneCommandPayload(
                VttSceneCommandPayload.CREATE, displayName.trim()));
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
        Path cacheRoot = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config/vtt_assets/cache/server");
        AssetLibraryScanResult synced = new AssetLibraryScanner(
                new AssetLibraryPath(cacheRoot.resolve("assets"))
        ).scan();
        this.assetLibraryScanResult = synced;

        assetRegistry.clear();
        DebugAssets.registerAll(assetRegistry);
        tokenDefinitionRegistry.clear();
        DebugTokenDefinitions.registerAll(tokenDefinitionRegistry, assetRegistry);
        assetThumbnailRegistry.clear();
        animatedTextureService.setUseServerCache(true);
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
        localPlayerId = null;
        localRole = VttRole.MASTER;

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
        loadActiveSceneToCanvasScene();
        VTT.LOGGER.info("Restored local VTT session after leaving multiplayer server");
    }

    public TokenDefinitionRegistry getTokenDefinitionRegistry() {
        return tokenDefinitionRegistry;
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
