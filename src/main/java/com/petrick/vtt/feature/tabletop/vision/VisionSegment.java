package com.petrick.vtt.feature.tabletop.vision;

import com.petrick.vtt.core.math.Vec2d;

/** One world-space edge capable of blocking a vision ray. */
public record VisionSegment(String sourceId, Vec2d start, Vec2d end) {
    public VisionSegment {
        sourceId = sourceId == null ? "" : sourceId;
        if (start == null || end == null) {
            throw new IllegalArgumentException("Vision segment points cannot be null");
        }
    }
}
