package com.petrick.vtt.editor.token;

/**
 * Lightweight HUD projection of a placed token owned by a connected player.
 *
 * The scene fields keep the UI ready for listing tokens from more than one scene later.
 */
public record VttOwnedTokenOption(
        String id,
        String displayName,
        String ownerId,
        String sceneId,
        String sceneName,
        boolean visible,
        boolean visionEnabled
) {
    public VttOwnedTokenOption {
        id = id == null ? "" : id;
        displayName = displayName == null || displayName.isBlank() ? "Unnamed token" : displayName;
        ownerId = ownerId == null ? "" : ownerId;
        sceneId = sceneId == null ? "" : sceneId;
        sceneName = sceneName == null ? "" : sceneName;
    }
}
