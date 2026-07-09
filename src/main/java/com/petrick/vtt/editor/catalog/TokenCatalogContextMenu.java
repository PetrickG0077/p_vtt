package com.petrick.vtt.editor.catalog;

import com.petrick.vtt.feature.token.TokenDefinition;

/**
 * Estado do menu de contexto do Token Catalog.
 *
 * Ele abre com botão direito em cima de um token.
 * O menu não segue o mouse depois de aberto.
 */
public final class TokenCatalogContextMenu {

    private boolean open;

    private int x;

    private int y;

    private TokenDefinition tokenDefinition;

    public boolean isOpen() {
        return open;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public TokenDefinition getTokenDefinition() {
        return tokenDefinition;
    }

    public boolean hasTokenDefinition() {
        return tokenDefinition != null;
    }

    public void open(
            int x,
            int y,
            TokenDefinition tokenDefinition
    ) {
        if (tokenDefinition == null) {
            close();
            return;
        }

        this.open = true;
        this.x = x;
        this.y = y;
        this.tokenDefinition = tokenDefinition;
    }

    public void close() {
        this.open = false;
        this.x = 0;
        this.y = 0;
        this.tokenDefinition = null;
    }
}