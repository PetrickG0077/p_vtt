package com.petrick.vtt.core.session;

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