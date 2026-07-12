package com.petrick.vtt.feature.token;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.editor.token.TokenCreationDraft;
import com.petrick.vtt.editor.token.TokenStateDraft;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.LibraryTextureAssetRef;
import com.petrick.vtt.feature.canvas.CanvasObjectState;
import com.petrick.vtt.feature.canvas.visual.TextureVisual;
import com.petrick.vtt.feature.asset.library.AssetLibraryFileType;
import com.petrick.vtt.feature.canvas.visual.AnimatedTextureVisual;
import com.petrick.vtt.feature.canvas.visual.CanvasVisual;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cria TokenDefinitions a partir do menu Create Token.
 */
public final class CreatedTokenDefinitions {

    private static final String USER_TOKEN_ID_PREFIX = "user/tokens/";
    private static final String USER_TOKEN_IMAGE_ID_PREFIX = "user/token_images/";

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

        if (!draft.hasAnyStateImage()) {
            throw new IllegalStateException("Cannot create token without at least one state image");
        }

        if (!draft.allStatesHaveImages()) {
            throw new IllegalStateException("Cannot create token while some states have no image");
        }

        String displayName = draft.getResolvedDisplayName();
        String safeName = sanitizeIdPart(displayName);

        String tokenDefinitionId = createUniqueTokenDefinitionId(
                tokenDefinitionRegistry,
                USER_TOKEN_ID_PREFIX + safeName
        );

        Map<String, CanvasObjectState> states = createCanvasObjectStates(
                draft,
                assetRegistry,
                safeName
        );

        String defaultStateId = draft.getDefaultStateIdForSave();

        TokenStateDraft defaultState = draft.getDefaultStateForSave();

        TokenDefinition definition = new TokenDefinition(
                tokenDefinitionId,
                displayName,
                calculateDefaultSize(
                        defaultState.getImageWidth(),
                        defaultState.getImageHeight()
                ),
                states,
                defaultStateId,
                draft.getPlayer()
        );

        tokenDefinitionRegistry.register(definition);

        return definition;
    }

    private static Map<String, CanvasObjectState> createCanvasObjectStates(
            TokenCreationDraft draft,
            AssetRegistry assetRegistry,
            String safeTokenName
    ) {
        Map<String, CanvasObjectState> states = new LinkedHashMap<>();

        for (TokenStateDraft stateDraft : draft.getStates()) {
            if (!stateDraft.hasImage()) {
                continue;
            }

            String assetId = createUniqueAssetId(
                    assetRegistry,
                    USER_TOKEN_IMAGE_ID_PREFIX + safeTokenName + "/" + stateDraft.getId()
            );

            LibraryTextureAssetRef assetRef = new LibraryTextureAssetRef(
                    assetId,
                    stateDraft.getImageTexture(),
                    stateDraft.getImageWidth(),
                    stateDraft.getImageHeight(),
                    stateDraft.getImageId()
            );

            assetRegistry.register(assetRef);

            states.put(
                    stateDraft.getId(),
                    new CanvasObjectState(
                            stateDraft.getId(),
                            stateDraft.getDisplayName(),
                            createVisualForState(assetRef, stateDraft.getImageFileType())
                    )
            );
        }

        return states;
    }

    private static CanvasVisual createVisualForState(
            LibraryTextureAssetRef assetRef,
            AssetLibraryFileType fileType
    ) {
        if (fileType == AssetLibraryFileType.ANIMATED_IMAGE) {
            return new AnimatedTextureVisual(assetRef);
        }

        return new TextureVisual(assetRef);
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
            return "new_token";
        }

        String sanitized = value
                .toLowerCase()
                .trim()
                .replace('\\', '/')
                .replaceAll("[^a-z0-9._/-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("/+", "/");

        if (sanitized.isBlank()) {
            return "new_token";
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
