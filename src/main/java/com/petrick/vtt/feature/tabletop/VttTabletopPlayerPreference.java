package com.petrick.vtt.feature.tabletop;

/** Extensible server-side preferences for one player in a tabletop. */
public final class VttTabletopPlayerPreference {
    private boolean spectator;

    public boolean isSpectator() {
        return spectator;
    }

    public void setSpectator(boolean spectator) {
        this.spectator = spectator;
    }
}
