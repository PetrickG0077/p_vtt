package com.petrick.vtt.core.session;

import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.DebugAssets;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.token.DebugTokenDefinitions;
import com.petrick.vtt.feature.token.TokenDefinitionRegistry;
import com.petrick.vtt.feature.asset.library.AssetLibraryConfig;
import com.petrick.vtt.feature.asset.library.AssetLibraryService;
import net.minecraft.client.Minecraft;

/**
 * Representa uma sessão ativa do VTT.
 *
 * Por enquanto ela existe apenas em memória.
 */
public final class VTTSession {

    private final AssetRegistry assetRegistry;

    private final TokenDefinitionRegistry tokenDefinitionRegistry;

    private final CanvasScene canvasScene;

    private final AssetLibraryService assetLibraryService;

    public VTTSession() {
        this.assetRegistry = new AssetRegistry();
        DebugAssets.registerAll(assetRegistry);

        this.assetLibraryService = new AssetLibraryService(
                new AssetLibraryConfig(Minecraft.getInstance().gameDirectory.toPath())
        );
        assetLibraryService.ensureDirectoriesExist();

        this.tokenDefinitionRegistry = new TokenDefinitionRegistry();
        DebugTokenDefinitions.registerAll(tokenDefinitionRegistry, assetRegistry);

        this.canvasScene = CanvasScene.createDebugScene(assetRegistry);

    }

    public AssetRegistry getAssetRegistry() {
        return assetRegistry;
    }

    public AssetLibraryService getAssetLibraryService() {
        return assetLibraryService;
    }

    public TokenDefinitionRegistry getTokenDefinitionRegistry() {
        return tokenDefinitionRegistry;
    }

    public CanvasScene getCanvasScene() {
        return canvasScene;
    }
}