package com.petrick.vtt.feature.tabletop;

import java.util.ArrayList;
import java.util.List;

/** Central complexity budget shared by persistence and authoritative mutations. */
public final class VttSceneLimits {
    public static final int MAX_SCENES = 256;
    public static final int MAX_TOKENS = 4_096;
    public static final int MAX_MAPS = 256;
    public static final int MAX_WALLS = 8_192;
    public static final int MAX_DOORS = 2_048;
    public static final int MAX_FOG_AREAS = 4_096;
    public static final int MAX_VISION_SOURCES = 64;
    public static final int MAX_POINTS_PER_VISION_REGION = 32_768;
    public static final int MAX_TOTAL_VISION_POINTS = 131_072;

    private VttSceneLimits() {}

    public static Violation tokenCreation(VttScene scene) {
        return countViolation("tokens", size(scene == null ? null : scene.getObjects()), MAX_TOKENS);
    }

    public static Violation sceneCreation(VttTabletop tabletop) {
        return countViolation("scenes",
                size(tabletop == null ? null : tabletop.getSceneIds()), MAX_SCENES);
    }

    public static Violation environmentUpsert(VttScene scene, String entityType, String entityId) {
        if (scene == null || entityType == null || entityId == null || entityId.isBlank()) return null;
        return switch (entityType) {
            case "MAP" -> containsMap(scene, entityId) ? null
                    : countViolation("maps", scene.getMaps().size(), MAX_MAPS);
            case "WALL" -> containsWall(scene, entityId) ? null
                    : countViolation("walls", scene.getWalls().size(), MAX_WALLS);
            case "DOOR" -> containsDoor(scene, entityId) ? null
                    : countViolation("doors", scene.getDoors().size(), MAX_DOORS);
            case "FOG_HIDDEN", "FOG_REVEALED" -> containsFog(scene, entityId) ? null
                    : countViolation("fog areas", fogAreaCount(scene), MAX_FOG_AREAS);
            default -> null;
        };
    }

    public static Violation visionEnable(VttScene scene, String objectId, boolean enabled) {
        if (!enabled || scene == null || objectId == null) return null;
        VttSceneObject object = scene.getObjects().stream()
                .filter(value -> value != null && objectId.equals(value.getId()))
                .findFirst().orElse(null);
        if (object == null || object.isVisionEnabled()) return null;
        return countViolation(
                "enabled vision sources", enabledVisionSourceCount(scene), MAX_VISION_SOURCES);
    }

    public static boolean replacementAllowed(int currentCount, int requestedCount, int maximum) {
        return requestedCount <= maximum || requestedCount <= currentCount;
    }

    public static List<Violation> inspect(VttScene scene) {
        if (scene == null) return List.of();
        List<Violation> violations = new ArrayList<>();
        addExceeded(violations, "tokens", scene.getObjects().size(), MAX_TOKENS);
        addExceeded(violations, "maps", scene.getMaps().size(), MAX_MAPS);
        addExceeded(violations, "walls", scene.getWalls().size(), MAX_WALLS);
        addExceeded(violations, "doors", scene.getDoors().size(), MAX_DOORS);
        addExceeded(violations, "fog areas", fogAreaCount(scene), MAX_FOG_AREAS);
        addExceeded(violations, "vision sources",
                scene.getVisionSourceObjectIds().size(), MAX_VISION_SOURCES);
        addExceeded(violations, "enabled vision sources",
                enabledVisionSourceCount(scene), MAX_VISION_SOURCES);
        return List.copyOf(violations);
    }

    public static List<Violation> inspect(VttTabletop tabletop) {
        if (tabletop == null) return List.of();
        List<Violation> violations = new ArrayList<>();
        addExceeded(violations, "scenes", tabletop.getSceneIds().size(), MAX_SCENES);
        return List.copyOf(violations);
    }

    public static int fogAreaCount(VttScene scene) {
        if (scene == null) return 0;
        return scene.getFogOfWar().getHiddenAreas().size()
                + scene.getFogOfWar().getRevealedAreas().size();
    }

    public static int enabledVisionSourceCount(VttScene scene) {
        if (scene == null) return 0;
        return (int) scene.getObjects().stream()
                .filter(object -> object != null && object.isVisionEnabled())
                .count();
    }

    private static Violation countViolation(String resource, int currentCount, int maximum) {
        if (currentCount < maximum) return null;
        return new Violation("max_" + resource.replace(' ', '_'),
                "Scene limit reached: " + maximum + " " + resource,
                currentCount, maximum);
    }

    private static void addExceeded(
            List<Violation> result, String resource, int count, int maximum
    ) {
        if (count > maximum) {
            result.add(new Violation("max_" + resource.replace(' ', '_'),
                    "Scene contains " + count + " " + resource
                            + ", above the supported limit of " + maximum,
                    count, maximum));
        }
    }

    private static boolean containsWall(VttScene scene, String id) {
        return scene.getWalls().stream().anyMatch(
                value -> value != null && id.equals(value.getId()));
    }

    private static boolean containsMap(VttScene scene, String id) {
        return scene.getMaps().stream().anyMatch(
                value -> value != null && id.equals(value.getId()));
    }

    private static boolean containsDoor(VttScene scene, String id) {
        return scene.getDoors().stream().anyMatch(
                value -> value != null && id.equals(value.getId()));
    }

    private static boolean containsFog(VttScene scene, String id) {
        return scene.getFogOfWar().getHiddenAreas().stream().anyMatch(
                value -> value != null && id.equals(value.getId()))
                || scene.getFogOfWar().getRevealedAreas().stream().anyMatch(
                value -> value != null && id.equals(value.getId()));
    }

    private static int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    public record Violation(String code, String message, int actual, int maximum) {}
}
