package com.petrick.vtt.core.session;

/**
 * Papel local do usuário dentro do VTT.
 *
 * Por enquanto isso é apenas local/singleplayer.
 * Futuramente será integrado ao sistema multiplayer/server authority.
 */
public enum VttRole {

    MASTER,
    PLAYER;

    public boolean canEditTabletop() {
        return this == MASTER;
    }

    public boolean canUseCatalogs() {
        return this == MASTER;
    }

    public boolean canMoveAnyToken() {
        return this == MASTER;
    }
}