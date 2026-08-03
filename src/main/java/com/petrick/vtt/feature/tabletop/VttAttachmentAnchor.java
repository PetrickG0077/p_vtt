package com.petrick.vtt.feature.tabletop;

/** Stable local-space anchor on a token's untransformed bounds. */
public enum VttAttachmentAnchor {
    CUSTOM(0.0, 0.0, "Custom"),
    CENTER(0.0, 0.0, "Center"),
    TOP(0.0, -1.0, "Top"),
    BOTTOM(0.0, 1.0, "Bottom"),
    LEFT(-1.0, 0.0, "Left"),
    RIGHT(1.0, 0.0, "Right"),
    TOP_LEFT(-1.0, -1.0, "Top Left"),
    TOP_RIGHT(1.0, -1.0, "Top Right"),
    BOTTOM_LEFT(-1.0, 1.0, "Bottom Left"),
    BOTTOM_RIGHT(1.0, 1.0, "Bottom Right");

    private final double horizontal;
    private final double vertical;
    private final String displayName;

    VttAttachmentAnchor(double horizontal, double vertical, String displayName) {
        this.horizontal = horizontal;
        this.vertical = vertical;
        this.displayName = displayName;
    }

    public double horizontal() { return horizontal; }
    public double vertical() { return vertical; }
    public String displayName() { return displayName; }
}
