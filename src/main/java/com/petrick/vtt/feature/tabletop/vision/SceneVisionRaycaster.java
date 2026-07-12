package com.petrick.vtt.feature.tabletop.vision;

import com.petrick.vtt.core.math.Vec2d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Casts rays and produces an ordered visibility boundary around one source. */
public final class SceneVisionRaycaster {
    private static final double ANGLE_EPSILON = 0.00001;
    private static final double INTERSECTION_EPSILON = 0.0000001;
    private static final int BASE_RAY_COUNT = 256;

    public VisionRayHit castRay(
            Vec2d origin, double angleRadians, double maxDistance, List<VisionSegment> segments
    ) {
        if (origin == null) throw new IllegalArgumentException("Vision origin cannot be null");
        double distanceLimit = Math.max(1.0, maxDistance);
        Vec2d direction = new Vec2d(Math.cos(angleRadians), Math.sin(angleRadians));
        double nearestDistance = distanceLimit;
        VisionSegment nearestSegment = null;
        if (segments != null) {
            for (VisionSegment segment : segments) {
                if (segment == null) continue;
                double distance = raySegmentDistance(origin, direction, segment);
                if (distance >= 0.0 && distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestSegment = segment;
                }
            }
        }
        return new VisionRayHit(origin.add(direction.multiply(nearestDistance)),
                nearestDistance, nearestSegment);
    }

    public List<Vec2d> buildVisibilityPolygon(
            Vec2d origin, double maxDistance, List<VisionSegment> segments
    ) {
        if (origin == null) return List.of();
        List<VisionSegment> safeSegments = segments == null ? List.of() : segments;
        List<AngularPoint> hits = new ArrayList<>(BASE_RAY_COUNT + safeSegments.size() * 6);
        for (int index = 0; index < BASE_RAY_COUNT; index++) {
            double angle = Math.PI * 2.0 * index / BASE_RAY_COUNT - Math.PI;
            addRay(hits, origin, angle, maxDistance, safeSegments);
        }
        for (VisionSegment segment : safeSegments) {
            if (segment == null) continue;
            addEndpointRays(hits, origin, segment.start(), maxDistance, safeSegments);
            addEndpointRays(hits, origin, segment.end(), maxDistance, safeSegments);
        }
        hits.sort(Comparator.comparingDouble(AngularPoint::angle));
        List<Vec2d> polygon = new ArrayList<>(hits.size());
        for (AngularPoint hit : hits) polygon.add(hit.point());
        return List.copyOf(polygon);
    }

    private void addEndpointRays(
            List<AngularPoint> target, Vec2d origin, Vec2d endpoint,
            double maxDistance, List<VisionSegment> segments
    ) {
        double angle = Math.atan2(endpoint.y() - origin.y(), endpoint.x() - origin.x());
        addRay(target, origin, angle - ANGLE_EPSILON, maxDistance, segments);
        addRay(target, origin, angle, maxDistance, segments);
        addRay(target, origin, angle + ANGLE_EPSILON, maxDistance, segments);
    }

    private void addRay(
            List<AngularPoint> target, Vec2d origin, double angle,
            double maxDistance, List<VisionSegment> segments
    ) {
        target.add(new AngularPoint(angle, castRay(origin, angle, maxDistance, segments).point()));
    }

    private double raySegmentDistance(Vec2d origin, Vec2d direction, VisionSegment segment) {
        Vec2d edge = segment.end().subtract(segment.start());
        double denominator = cross(direction, edge);
        if (Math.abs(denominator) <= INTERSECTION_EPSILON) return -1.0;
        Vec2d offset = segment.start().subtract(origin);
        double rayDistance = cross(offset, edge) / denominator;
        double segmentPosition = cross(offset, direction) / denominator;
        if (rayDistance < INTERSECTION_EPSILON
                || segmentPosition < -INTERSECTION_EPSILON
                || segmentPosition > 1.0 + INTERSECTION_EPSILON) return -1.0;
        return rayDistance;
    }

    private double cross(Vec2d first, Vec2d second) {
        return first.x() * second.y() - first.y() * second.x();
    }

    private record AngularPoint(double angle, Vec2d point) {}
}
