package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.feature.tabletop.VttSceneLimits;
import com.petrick.vtt.feature.tabletop.vision.AuthoritativeVisionRegion;
import com.petrick.vtt.feature.tabletop.vision.SceneVisionRaycaster;
import com.petrick.vtt.network.payload.VttPlayerReplicationPayload;
import com.petrick.vtt.network.payload.VttVisionSourcesPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Maintains independent per-player vision geometry and object replication deltas. */
public final class VttServerVisionSourceSync {
    private static final Gson GSON = new GsonBuilder().create();
    private static final double DEFAULT_OUTER_RADIUS = 512.0;
    private static final Map<UUID, Long> VISION_REVISIONS = new HashMap<>();
    private static final Map<UUID, Long> REPLICATION_REVISIONS = new HashMap<>();
    private static final Map<UUID, KnownAssets> KNOWN_ASSETS = new HashMap<>();
    private static final Map<UUID, PlayerScope> PLAYER_SCOPES = new HashMap<>();
    private static final SceneVisionRaycaster RAYCASTER = new SceneVisionRaycaster();

    private VttServerVisionSourceSync() {}

    public static void sendToPlayer(ServerPlayer player, VttServerTabletopState state) {
        syncPlayer(player, state, true);
    }

    public static void heartbeat(ServerPlayer player, VttServerTabletopState state) {
        syncPlayer(player, state, false);
    }

    public static void afterTokenTransform(
            MinecraftServer server, VttServerTabletopState state, String movedObjectId
    ) {
        if (server == null || state == null || state.activeScene() == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean movedVisionSource = state.activeScene().getObjects().stream()
                    .anyMatch(object -> object != null && movedObjectId.equals(object.getId())
                            && player.getUUID().toString().equals(object.getOwnerId())
                            && object.isVisionEnabled());
            syncPlayer(player, state, movedVisionSource);
        }
    }

    public static List<VttSceneObject> visibleObjectsFor(ServerPlayer player, VttServerTabletopState state) {
        if (player == null || state == null || state.activeScene() == null) return List.of();
        if (VttServerPlayerEvents.canViewFullTabletop(player)) {
            return List.copyOf(state.activeScene().getObjects());
        }
        PlayerScope cached = PLAYER_SCOPES.get(player.getUUID());
        VisionState vision = sameScope(cached, state) ? cached.vision() : resolveVision(player, state);
        return resolveVisibleObjects(player, state, vision);
    }

    public static void broadcast(MinecraftServer server, VttServerTabletopState state) {
        broadcast(server, state, true);
    }

    public static void broadcast(
            MinecraftServer server, VttServerTabletopState state, boolean visionGeometryChanged
    ) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayer(player, state, visionGeometryChanged);
        }
    }

    private static void syncPlayer(
            ServerPlayer player, VttServerTabletopState state, boolean refreshVision
    ) {
        if (player == null || state == null || state.activeScene() == null
                || VttServerPlayerEvents.isMaster(player)) return;
        if (VttServerAssetSyncService.hasPending(player)) return;
        UUID playerId = player.getUUID();
        PlayerScope previous = PLAYER_SCOPES.get(playerId);
        boolean sameScope = sameScope(previous, state);
        VisionState vision = sameScope && !refreshVision
                ? previous.vision() : resolveVision(player, state);
        if (!sameScope || !vision.equals(previous.vision())) {
            PacketDistributor.sendToPlayer(player, new VttVisionSourcesPayload(
                    state.authorityRevision(), nextRevision(VISION_REVISIONS, playerId),
                    state.activeScene().getId(), vision.maskWhenEmpty(), vision.regions()));
        }

        List<VttSceneObject> visibleObjects = resolveVisibleObjects(player, state, vision);
        LinkedHashSet<String> currentIds = objectIds(visibleObjects);
        Set<String> previousIds = sameScope ? previous.objectIds() : currentIds;
        LinkedHashSet<String> spawnedIds = new LinkedHashSet<>(currentIds);
        spawnedIds.removeAll(previousIds);
        LinkedHashSet<String> despawnedIds = new LinkedHashSet<>(previousIds);
        despawnedIds.removeAll(currentIds);

        if (!spawnedIds.isEmpty() || !despawnedIds.isEmpty()) {
            List<VttSceneObject> spawnedObjects = visibleObjects.stream()
                    .filter(object -> spawnedIds.contains(object.getId())).toList();
            String json = GSON.toJson(spawnedObjects);
            if (json.length() <= VttPlayerReplicationPayload.MAX_OBJECTS_JSON_LENGTH) {
                VttPlayerReplicationPayload payload = new VttPlayerReplicationPayload(
                        state.authorityRevision(), nextRevision(REPLICATION_REVISIONS, playerId),
                        state.activeScene().getId(), json, List.copyOf(despawnedIds));
                Runnable sendDelta = () -> PacketDistributor.sendToPlayer(player, payload);
                if (!syncNewAssets(player, state, spawnedObjects, sendDelta)) {
                    sendDelta.run();
                }
                com.petrick.vtt.VTT.LOGGER.debug(
                        "Sent VTT replication delta to {}: {} spawn(s), {} despawn(s)",
                        player.getGameProfile().getName(), spawnedObjects.size(), despawnedIds.size());
            }
        }
        PLAYER_SCOPES.put(playerId, new PlayerScope(
                state.authorityRevision(), state.activeScene().getId(), vision, Set.copyOf(currentIds)));
    }

    private static VisionState resolveVision(ServerPlayer player, VttServerTabletopState state) {
        if (VttServerPlayerEvents.isSpectator(player)) {
            return new VisionState(false, List.of());
        }
        VttScene scene = state.activeScene();
        String ownerId = player.getUUID().toString();
        List<AuthoritativeVisionRegion> regions = new ArrayList<>();
        int remainingPoints = VttSceneLimits.MAX_TOTAL_VISION_POINTS;
        for (VttSceneObject object : scene.getObjects()) {
            if (object == null || object.getId() == null || object.getState() == null
                    || !object.getState().isVisible() || !ownerId.equals(object.getOwnerId())
                    || !object.isVisionEnabled()) continue;
            if (regions.size() >= VttSceneLimits.MAX_VISION_SOURCES || remainingPoints < 3) break;
            double outerRadius = object.getVisionOuterRadius() > 0.0
                    ? object.getVisionOuterRadius() : DEFAULT_OUTER_RADIUS;
            double innerRadius = Math.min(
                    Math.max(0.0, object.getVisionInnerRadius()), outerRadius);
            Vec2d origin = new Vec2d(
                    object.getTransform().getX(), object.getTransform().getY());
            var nearbySegments = state.queryVisionSegments(origin, outerRadius);
            List<Vec2d> polygon = boundedPolygon(
                    RAYCASTER.buildVisibilityPolygon(origin, outerRadius, nearbySegments),
                    Math.min(VttSceneLimits.MAX_POINTS_PER_VISION_REGION, remainingPoints));
            if (polygon.size() < 3) continue;
            regions.add(new AuthoritativeVisionRegion(
                    object.getId(), origin, innerRadius, outerRadius, polygon));
            remainingPoints -= polygon.size();
        }
        boolean ownsDisabledToken = scene.getObjects().stream()
                .anyMatch(object -> object != null && ownerId.equals(object.getOwnerId())
                        && !object.isVisionEnabled());
        return new VisionState(!ownsDisabledToken, regions);
    }

    private static List<Vec2d> boundedPolygon(List<Vec2d> polygon, int maximumPoints) {
        if (polygon == null || polygon.size() <= maximumPoints) {
            return polygon == null ? List.of() : List.copyOf(polygon);
        }
        if (maximumPoints < 3) return List.of();
        List<Vec2d> result = new ArrayList<>(maximumPoints);
        for (int index = 0; index < maximumPoints; index++) {
            int sourceIndex = (int) ((long) index * polygon.size() / maximumPoints);
            result.add(polygon.get(sourceIndex));
        }
        return List.copyOf(result);
    }

    private static List<VttSceneObject> resolveVisibleObjects(
            ServerPlayer player, VttServerTabletopState state, VisionState vision
    ) {
        VttScene scene = state.activeScene();
        String ownerId = player.getUUID().toString();
        if (vision.regions().isEmpty() && !vision.maskWhenEmpty()) {
            return scene.getObjects().stream()
                    .filter(object -> object != null && object.getId() != null
                            && object.getState() != null && object.getState().isVisible())
                    .toList();
        }

        Map<String, VttSceneObject> candidates = new LinkedHashMap<>();
        scene.getObjects().stream()
                .filter(object -> object != null && object.getId() != null
                        && ownerId.equals(object.getOwnerId()))
                .forEach(object -> candidates.put(object.getId(), object));
        for (AuthoritativeVisionRegion region : vision.regions()) {
            double minX = region.origin().x() - region.outerRadius();
            double minY = region.origin().y() - region.outerRadius();
            double maxX = region.origin().x() + region.outerRadius();
            double maxY = region.origin().y() + region.outerRadius();
            for (VttSceneObject object : state.querySceneObjects(minX, minY, maxX, maxY)) {
                if (object != null && object.getId() != null) candidates.put(object.getId(), object);
            }
        }

        return candidates.values().stream()
                .filter(object -> object != null && object.getId() != null
                        && object.getState() != null && object.getState().isVisible())
                .filter(object -> ownerId.equals(object.getOwnerId())
                        || vision.regions().stream().anyMatch(region -> pointInsidePolygon(
                                new Vec2d(object.getTransform().getX(), object.getTransform().getY()),
                                region.outerPolygon())))
                .sorted(java.util.Comparator.comparingInt(VttSceneObject::getLayerIndex))
                .toList();
    }

    public static void forget(UUID playerId) {
        if (playerId == null) return;
        VISION_REVISIONS.remove(playerId);
        REPLICATION_REVISIONS.remove(playerId);
        KNOWN_ASSETS.remove(playerId);
        PLAYER_SCOPES.remove(playerId);
    }

    public static void resetPlayerScope(UUID playerId) {
        if (playerId != null) PLAYER_SCOPES.remove(playerId);
    }

    public static boolean canReceiveObject(
            ServerPlayer player, VttServerTabletopState state, String objectId
    ) {
        if (player == null || state == null || objectId == null || state.activeScene() == null) return false;
        if (VttServerPlayerEvents.canViewFullTabletop(player)) return true;
        PlayerScope scope = PLAYER_SCOPES.get(player.getUUID());
        return sameScope(scope, state) && scope.objectIds().contains(objectId);
    }

    public static void markCurrentAssetsSent(ServerPlayer player, VttServerTabletopState state) {
        if (player == null || state == null || state.activeScene() == null
                || VttServerPlayerEvents.isMaster(player)) return;
        KNOWN_ASSETS.put(player.getUUID(), new KnownAssets(state.authorityRevision(),
                state.activeScene().getId(), definitionIds(visibleObjectsFor(player, state))));
    }

    private static boolean syncNewAssets(
            ServerPlayer player, VttServerTabletopState state,
            List<VttSceneObject> visibleObjects, Runnable completion
    ) {
        KnownAssets known = KNOWN_ASSETS.get(player.getUUID());
        boolean sameScope = known != null && known.authorityRevision() == state.authorityRevision()
                && known.sceneId().equals(state.activeScene().getId());
        Set<String> knownDefinitions = sameScope
                ? new LinkedHashSet<>(known.definitionIds()) : new LinkedHashSet<>();
        List<VttSceneObject> newlyVisibleDefinitions = visibleObjects.stream()
                .filter(object -> object.getSourceTokenDefinitionId() != null
                        && !knownDefinitions.contains(object.getSourceTokenDefinitionId())).toList();
        if (!newlyVisibleDefinitions.isEmpty()) {
            VttServerAssetSyncService.sendVisibleTokenAssets(
                    player, newlyVisibleDefinitions, completion);
        }
        knownDefinitions.addAll(definitionIds(visibleObjects));
        KNOWN_ASSETS.put(player.getUUID(), new KnownAssets(state.authorityRevision(),
                state.activeScene().getId(), Set.copyOf(knownDefinitions)));
        return !newlyVisibleDefinitions.isEmpty();
    }

    private static boolean sameScope(PlayerScope scope, VttServerTabletopState state) {
        return scope != null && state != null && state.activeScene() != null
                && scope.authorityRevision() == state.authorityRevision()
                && scope.sceneId().equals(state.activeScene().getId());
    }

    private static LinkedHashSet<String> objectIds(List<VttSceneObject> objects) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (VttSceneObject object : objects) {
            if (object != null && object.getId() != null) result.add(object.getId());
        }
        return result;
    }

    private static Set<String> definitionIds(List<VttSceneObject> objects) {
        Set<String> result = new LinkedHashSet<>();
        for (VttSceneObject object : objects) {
            if (object != null && object.getSourceTokenDefinitionId() != null) {
                result.add(object.getSourceTokenDefinitionId());
            }
        }
        return Set.copyOf(result);
    }

    private static long nextRevision(Map<UUID, Long> revisions, UUID playerId) {
        long current = revisions.getOrDefault(playerId, 0L);
        long next = current == Long.MAX_VALUE ? 1L : current + 1L;
        revisions.put(playerId, next);
        return next;
    }

    private static boolean pointInsidePolygon(Vec2d point, List<Vec2d> polygon) {
        if (point == null || polygon == null || polygon.size() < 3) return false;
        boolean inside = false;
        for (int first = 0, second = polygon.size() - 1; first < polygon.size(); second = first++) {
            Vec2d a = polygon.get(first);
            Vec2d b = polygon.get(second);
            boolean crosses = (a.y() > point.y()) != (b.y() > point.y())
                    && point.x() < (b.x() - a.x()) * (point.y() - a.y())
                    / (b.y() - a.y()) + a.x();
            if (crosses) inside = !inside;
        }
        return inside;
    }

    private record VisionState(boolean maskWhenEmpty, List<AuthoritativeVisionRegion> regions) {}
    private record PlayerScope(
            long authorityRevision, String sceneId, VisionState vision, Set<String> objectIds
    ) {}
    private record KnownAssets(long authorityRevision, String sceneId, Set<String> definitionIds) {}
}
