package com.petrick.vtt.feature.asset;

import net.minecraft.resources.ResourceLocation;

/**
 * Registra assets internos usados para debug/teste.
 *
 * Isso é temporário para o começo da Sprint 2.
 * Futuramente os assets reais virão do AssetProcessor/cache.
 */
public final class DebugAssets {

    public static final String TEST_TOKEN_ID = "debug/test_token";

    private DebugAssets() {
    }

    public static void registerAll(AssetRegistry assetRegistry) {
        assetRegistry.register(new BuiltInTextureAssetRef(
                TEST_TOKEN_ID,
                ResourceLocation.fromNamespaceAndPath("vtt", "textures/gui/test_token.png"),
                64,
                64
        ));
    }
}