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
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnailLoader;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnailRegistry;
import com.petrick.vtt.feature.asset.animation.AnimatedTextureService;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttTabletop;
import com.petrick.vtt.feature.tabletop.persistence.TabletopStorage;
import com.petrick.vtt.feature.tabletop.persistence.CanvasSceneToVttSceneMapper;
import com.petrick.vtt.feature.tabletop.persistence.VttSceneToCanvasSceneMapper;
import net.minecraft.client.Minecraft;

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

    public void applyNetworkSnapshot(VttTabletop tabletop, VttScene scene) {
        if (tabletop == null || scene == null) {
            VTT.LOGGER.warn("Ignored invalid VTT network snapshot");
            return;
        }

        this.activeTabletop = tabletop;
        this.activeScene = scene;
        loadActiveSceneToCanvasScene();
        VTT.LOGGER.info("Applied VTT network snapshot for scene: {}", scene.getId());
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

    public void saveActiveTabletopAndScene() {
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
        this.assetLibraryScanResult = assetLibraryService.scanLibrary();
        loadAssetThumbnails();
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
