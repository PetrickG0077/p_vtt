package com.petrick.vtt.feature.map;

/** Defines how a map image fills the bounds of a scene map instance. */
public enum MapTextureMode {
    STRETCH,
    REPEAT;

    public static MapTextureMode normalize(MapTextureMode value) {
        return value == null ? STRETCH : value;
    }
}
