package com.petrick.vtt.feature.tabletop.vision;

import com.petrick.vtt.core.math.Vec2d;

/** Nearest result of one ray cast against the scene vision geometry. */
public record VisionRayHit(Vec2d point, double distance, VisionSegment segment) {
    public boolean blocked() { return segment != null; }
}
