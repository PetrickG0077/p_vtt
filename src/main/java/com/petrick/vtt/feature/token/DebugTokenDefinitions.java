package com.petrick.vtt.feature.token;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.DebugAssets;
import com.petrick.vtt.feature.canvas.CanvasObjectState;
import com.petrick.vtt.feature.canvas.CanvasObjectStateFactory;

import java.util.Map;

/**
 * Registra definições de tokens temporárias para debug.
 *
 * Futuramente isso será substituído por tokens reais carregados
 * de biblioteca, arquivos ou servidor.
 */
public final class DebugTokenDefinitions {

    public static final String TEST_TOKEN_DEFINITION_ID = "debug/test_token_definition";

    private DebugTokenDefinitions() {
    }

    public static void registerAll(
            TokenDefinitionRegistry tokenDefinitionRegistry,
            AssetRegistry assetRegistry
    ) {
        Map<String, CanvasObjectState> states =
                CanvasObjectStateFactory.createTestTokenStates(
                        assetRegistry.getRequired(DebugAssets.TEST_TOKEN_ID)
                );

        tokenDefinitionRegistry.register(new TokenDefinition(
                TEST_TOKEN_DEFINITION_ID,
                "Test Token",
                new Vec2d(64.0, 64.0),
                states,
                "1"
        ));
    }
}