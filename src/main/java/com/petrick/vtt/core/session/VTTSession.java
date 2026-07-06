package com.petrick.vtt.core.session;

import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.DebugAssets;
import com.petrick.vtt.feature.canvas.CanvasScene;

/**
 * Representa uma sessão ativa do VTT.
 *
 * Por enquanto ela existe apenas em memória.
 * Isso significa que ela persiste enquanto o Minecraft está aberto,
 * mas ainda não é salva em disco.
 *
 * Futuramente, esta classe pode guardar:
 * - tabletop atual
 * - tokens
 * - assets
 * - permissões
 * - estado de fog of war
 * - cenas/mapas
 */
public final class VTTSession {

    private final AssetRegistry assetRegistry;

    private final CanvasScene canvasScene;

    public VTTSession() {
        this.assetRegistry = new AssetRegistry();
        DebugAssets.registerAll(assetRegistry);

        this.canvasScene = CanvasScene.createDebugScene(assetRegistry);
    }

    public AssetRegistry getAssetRegistry() {
        return assetRegistry;
    }

    public CanvasScene getCanvasScene() {
        return canvasScene;
    }
}