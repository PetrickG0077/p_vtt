package com.petrick.vtt.core.session;

/** Server-authoritative connected-player identity exposed to the VTT client HUD. */
public record VttPlayerRosterEntry(String id, String displayName, VttRole role) {
    public VttPlayerRosterEntry {
        id = id == null ? "" : id.trim();
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        role = role == null ? VttRole.PLAYER : role;
    }
}
