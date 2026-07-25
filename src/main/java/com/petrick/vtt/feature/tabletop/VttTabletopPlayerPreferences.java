package com.petrick.vtt.feature.tabletop;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Versioned player properties belonging to one tabletop rather than to a scene. */
public final class VttTabletopPlayerPreferences {
    private int schemaVersion = 1;
    private Map<String, VttTabletopPlayerPreference> players = new LinkedHashMap<>();

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public Map<String, VttTabletopPlayerPreference> getPlayers() {
        if (players == null) players = new LinkedHashMap<>();
        return players;
    }

    public boolean isSpectator(UUID playerId) {
        if (playerId == null) return false;
        VttTabletopPlayerPreference preference = getPlayers().get(playerId.toString());
        return preference != null && preference.isSpectator();
    }

    public boolean setSpectator(UUID playerId, boolean spectator) {
        if (playerId == null) return false;
        String id = playerId.toString();
        VttTabletopPlayerPreference current = getPlayers().get(id);
        boolean previous = current != null && current.isSpectator();
        if (previous == spectator) return false;
        if (!spectator) {
            getPlayers().remove(id);
        } else {
            VttTabletopPlayerPreference preference = Objects.requireNonNullElseGet(
                    current, VttTabletopPlayerPreference::new);
            preference.setSpectator(true);
            getPlayers().put(id, preference);
        }
        return true;
    }
}
