package com.petrick.vtt.feature.tabletop.vision;

import com.petrick.vtt.core.math.Vec2d;

import java.util.List;

/** Immutable world-space visibility result calculated by the authoritative server. */
public record AuthoritativeVisionRegion(
        String sourceObjectId, Vec2d origin,
        double innerRadius, double outerRadius,
        List<Vec2d> outerPolygon
) {
    public AuthoritativeVisionRegion {
        outerPolygon = outerPolygon == null ? List.of() : List.copyOf(outerPolygon);
    }
}
