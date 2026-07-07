package com.petrick.vtt.editor.catalog;

import com.petrick.vtt.feature.token.TokenDefinition;

/**
 * Resultado de um clique no Token Catalog.
 */
public record TokenCatalogClickResult(
        boolean consumesClick,
        TokenDefinition tokenToCreateAtCameraCenter
) {

    public static TokenCatalogClickResult none() {
        return new TokenCatalogClickResult(false, null);
    }

    public static TokenCatalogClickResult consumeClick() {
        return new TokenCatalogClickResult(true, null);
    }

    public static TokenCatalogClickResult createAtCameraCenter(TokenDefinition definition) {
        return new TokenCatalogClickResult(true, definition);
    }

    public boolean shouldCreateAtCameraCenter() {
        return tokenToCreateAtCameraCenter != null;
    }
}