package com.petrick.vtt.editor.token;

/** Lightweight connected-player entry used by token ownership editors. */
public record VttPlayerOption(String id, String displayName) {
    public VttPlayerOption {
        id = id == null ? "" : id.trim();
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
    }
}
