package com.petrick.vtt.feature.token;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.editor.token.TokenCreationDraft;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.LibraryTextureAssetRef;
import com.petrick.vtt.feature.canvas.CanvasObjectState;
import com.petrick.vtt.feature.canvas.visual.TextureVisual;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cria TokenDefinitions a partir do menu Create Token.
 */
public final class CreatedTokenDefinitions {

    private static final String CREATED_TOKEN_ID_PREFIX = "created/token/";
    private static final String CREATED_ASSET_ID_PREFIX = "created/asset/";

    private static final double MAX_DEFAULT_TOKEN_SIZE = 96.0;

    private CreatedTokenDefinitions() {}

    public static TokenDefinition createAndRegister(
            TokenCreationDraft draft,
            TokenDefinitionRegistry tokenDefinitionRegistry,
            AssetRegistry assetRegistry
    ) {
        if (draft == null) {
            throw new IllegalArgumentException("TokenCreationDraft cannot be null");
        }

        if (tokenDefinitionRegistry == null) {
            throw new IllegalArgumentException("TokenDefinitionRegistry cannot be null");
        }

        if (assetRegistry == null) {
            throw new IllegalArgumentException("AssetRegistry cannot be null");
        }

        if (!draft.hasSelectedImage()) {
            throw new IllegalStateException("Cannot create token without selected image");
        }

        String baseName = draft.getResolvedDisplayName();

        String safeName = sanitizeIdPart(baseName);

        String tokenDefinitionId = createUniqueTokenDefinitionId(
                tokenDefinitionRegistry,
                CREATED_TOKEN_ID_PREFIX + safeName
        );

        String assetId = createUniqueAssetId(
                assetRegistry,
                CREATED_ASSET_ID_PREFIX + safeName
        );

        LibraryTextureAssetRef assetRef = new LibraryTextureAssetRef(
                assetId,
                draft.getSelectedImageTexture(),
                draft.getSelectedImageWidth(),
                draft.getSelectedImageHeight(),
                draft.getSelectedImageId()
        );

        assetRegistry.register(assetRef);

        Map<String, CanvasObjectState> states = new LinkedHashMap<>();

        states.put(
                "1",
                new CanvasObjectState(
                        "1",
                        "Normal",
                        new TextureVisual(assetRef)
                )
        );

        TokenDefinition definition = new TokenDefinition(
                tokenDefinitionId,
                baseName,
                calculateDefaultSize(
                        draft.getSelectedImageWidth(),
                        draft.getSelectedImageHeight()
                ),
                states,
                "1"
        );

        tokenDefinitionRegistry.register(definition);

        return definition;
    }

    private static String createUniqueTokenDefinitionId(
            TokenDefinitionRegistry registry,
            String baseId
    ) {
        String id = baseId;
        int counter = 2;

        while (registry.findById(id).isPresent()) {
            id = baseId + "_" + counter;
            counter++;
        }

        return id;
    }

    private static String createUniqueAssetId(
            AssetRegistry registry,
            String baseId
    ) {
        String id = baseId;
        int counter = 2;

        while (registry.contains(id)) {
            id = baseId + "_" + counter;
            counter++;
        }

        return id;
    }

    private static String sanitizeIdPart(String value) {
        if (value == null || value.isBlank()) {
            return "token";
        }

        String sanitized = value
                .toLowerCase()
                .trim()
                .replace('\\', '/')
                .replaceAll("[^a-z0-9._/-]", "_")
                .replaceAll("_+", "_");

        if (sanitized.isBlank()) {
            return "token";
        }

        return sanitized;
    }

    private static Vec2d calculateDefaultSize(
            int textureWidth,
            int textureHeight
    ) {
        if (textureWidth <= 0 || textureHeight <= 0) {
            return new Vec2d(MAX_DEFAULT_TOKEN_SIZE, MAX_DEFAULT_TOKEN_SIZE);
        }

        double width = textureWidth;
        double height = textureHeight;

        double largestSide = Math.max(width, height);

        if (largestSide <= MAX_DEFAULT_TOKEN_SIZE) {
            return new Vec2d(width, height);
        }

        double scale = MAX_DEFAULT_TOKEN_SIZE / largestSide;

        return new Vec2d(
                width * scale,
                height * scale
        );
    }
}