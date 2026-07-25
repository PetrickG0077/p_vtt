package com.petrick.vtt.editor.token;

import com.petrick.vtt.core.session.VttRole;

/** Lightweight connected-player entry used by token ownership editors. */
public record VttPlayerOption(
        String id,
        String displayName,
        VttRole role,
        int ownedTokenCount,
        boolean spectator
) {
    public VttPlayerOption {
        id = id == null ? "" : id.trim();
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        role = role == null ? VttRole.PLAYER : role;
        ownedTokenCount = Math.max(0, ownedTokenCount);
    }

    public VttPlayerOption(String id, String displayName) {
        this(id, displayName, VttRole.PLAYER, 0, false);
    }
}
