package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.feature.tabletop.vision.AuthoritativeVisionRegion;
import com.petrick.vtt.feature.tabletop.vision.SceneVisionGeometry;
import com.petrick.vtt.feature.tabletop.vision.SceneVisionRaycaster;
import com.petrick.vtt.network.payload.VttVisionSourcesPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;

/** Resolves and sends each player's server-authoritative vision sources. */
public final class VttServerVisionSourceSync {
    private static final Gson GSON = new GsonBuilder().create();
    private static final double DEFAULT_OUTER_RADIUS = 512.0;
    private static final Map<UUID, Long> REVISIONS = new HashMap<>();
    private static final Map<UUID, KnownAssets> KNOWN_ASSETS = new HashMap<>();
    private static final Map<UUID, VisibleScope> LAST_VISIBILITY = new HashMap<>();
    private static final SceneVisionGeometry GEOMETRY = new SceneVisionGeometry();
    private static final SceneVisionRaycaster RAYCASTER = new SceneVisionRaycaster();

    private VttServerVisionSourceSync() {}

    public static void sendToPlayer(ServerPlayer player, VttServerTabletopState state) {
        if (player == null || state == null || state.activeScene() == null
                || VttServerPlayerEvents.isMaster(player)) return;
        VisionReplication replication = resolve(player, state);
        syncNewAssets(player, state, replication.visibleObjects());
        String replicatedObjectsJson = GSON.toJson(replication.visibleObjects());
        if (replicatedObjectsJson.length() > VttVisionSourcesPayload.MAX_OBJECTS_JSON_LENGTH) return;
        LAST_VISIBILITY.put(player.getUUID(), new VisibleScope(
                state.authorityRevision(), state.activeScene().getId(),
                Set.copyOf(replication.visibleObjectIds())));
        PacketDistributor.sendToPlayer(player, new VttVisionSourcesPayload(
                state.authorityRevision(), nextRevision(player.getUUID()), state.activeScene().getId(),
                replication.maskWhenEmpty(), replication.regions(), replication.visibleObjectIds(),
                replicatedObjectsJson));
    }

    public static List<VttSceneObject> visibleObjectsFor(
            ServerPlayer player, VttServerTabletopState state
    ) {
        if (player == null || state == null || state.activeScene() == null) return List.of();
        if (VttServerPlayerEvents.isMaster(player)) return List.copyOf(state.activeScene().getObjects());
        return resolve(player, state).visibleObjects();
    }

    private static VisionReplication resolve(ServerPlayer player, VttServerTabletopState state) {
        VttScene scene = state.activeScene();
        String ownerId = player.getUUID().toString();
        var segments = GEOMETRY.build(scene);
        List<AuthoritativeVisionRegion> regions = scene.getObjects().stream()
                .filter(object -> object != null && object.getId() != null
                        && object.getState() != null && object.getState().isVisible()
                        && ownerId.equals(object.getOwnerId()) && object.isVisionEnabled())
                .map(object -> {
                    double outerRadius = object.getVisionOuterRadius() > 0.0
                            ? object.getVisionOuterRadius() : DEFAULT_OUTER_RADIUS;
                    double innerRadius = Math.min(
                            Math.max(0.0, object.getVisionInnerRadius()), outerRadius);
                    Vec2d origin = new Vec2d(
                            object.getTransform().getX(), object.getTransform().getY());
                    return new AuthoritativeVisionRegion(
                            object.getId(), origin, innerRadius, outerRadius,
                            RAYCASTER.buildVisibilityPolygon(origin, outerRadius, segments));
                })
                .toList();
        boolean ownsDisabledToken = scene.getObjects().stream()
                .anyMatch(object -> object != null && ownerId.equals(object.getOwnerId())
                        && !object.isVisionEnabled());
        boolean maskWhenEmpty = !ownsDisabledToken;
        var visibleObjects = scene.getObjects().stream()
                .filter(object -> object != null && object.getId() != null
                        && object.getState() != null && object.getState().isVisible())
                .filter(object -> ownerId.equals(object.getOwnerId())
                        || regions.isEmpty() && !maskWhenEmpty
                        || regions.stream().anyMatch(region -> pointInsidePolygon(
                                new Vec2d(object.getTransform().getX(), object.getTransform().getY()),
                                region.outerPolygon())))
                .toList();
        List<String> visibleObjectIds = visibleObjects.stream()
                .map(object -> object.getId()).distinct().toList();
        return new VisionReplication(maskWhenEmpty, regions, visibleObjects, visibleObjectIds);
    }

    public static void broadcast(MinecraftServer server, VttServerTabletopState state) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendToPlayer(player, state);
    }

    public static void forget(UUID playerId) {
        if (playerId != null) {
            REVISIONS.remove(playerId);
            KNOWN_ASSETS.remove(playerId);
            LAST_VISIBILITY.remove(playerId);
        }
    }

    public static boolean canReceiveObject(
            ServerPlayer player, VttServerTabletopState state, String objectId
    ) {
        if (player == null || state == null || objectId == null) return false;
        if (VttServerPlayerEvents.isMaster(player)) return true;
        VisibleScope scope = LAST_VISIBILITY.get(player.getUUID());
        return scope != null && scope.authorityRevision() == state.authorityRevision()
                && scope.sceneId().equals(state.activeScene().getId())
                && scope.objectIds().contains(objectId);
    }

    public static void markCurrentAssetsSent(ServerPlayer player, VttServerTabletopState state) {
        if (player == null || state == null || state.activeScene() == null
                || VttServerPlayerEvents.isMaster(player)) return;
        Set<String> definitions = definitionIds(visibleObjectsFor(player, state));
        KNOWN_ASSETS.put(player.getUUID(), new KnownAssets(
                state.authorityRevision(), state.activeScene().getId(), definitions));
    }

    private static void syncNewAssets(
            ServerPlayer player, VttServerTabletopState state, List<VttSceneObject> visibleObjects
    ) {
        KnownAssets known = KNOWN_ASSETS.get(player.getUUID());
        boolean sameScope = known != null && known.authorityRevision() == state.authorityRevision()
                && known.sceneId().equals(state.activeScene().getId());
        Set<String> knownDefinitions = sameScope
                ? new LinkedHashSet<>(known.definitionIds()) : new LinkedHashSet<>();
        List<VttSceneObject> newlyVisibleDefinitions = visibleObjects.stream()
                .filter(object -> object.getSourceTokenDefinitionId() != null
                        && !knownDefinitions.contains(object.getSourceTokenDefinitionId()))
                .toList();
        if (!newlyVisibleDefinitions.isEmpty()) {
            VttServerAssetSyncService.sendVisibleTokenAssets(player, newlyVisibleDefinitions);
        }
        knownDefinitions.addAll(definitionIds(visibleObjects));
        KNOWN_ASSETS.put(player.getUUID(), new KnownAssets(
                state.authorityRevision(), state.activeScene().getId(), Set.copyOf(knownDefinitions)));
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

    private static long nextRevision(UUID playerId) {
        long current = REVISIONS.getOrDefault(playerId, 0L);
        long next = current == Long.MAX_VALUE ? 1L : current + 1L;
        REVISIONS.put(playerId, next);
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

    private record VisionReplication(
            boolean maskWhenEmpty, List<AuthoritativeVisionRegion> regions,
            List<VttSceneObject> visibleObjects, List<String> visibleObjectIds
    ) {}

    private record KnownAssets(
            long authorityRevision, String sceneId, Set<String> definitionIds
    ) {}

    private record VisibleScope(
            long authorityRevision, String sceneId, Set<String> objectIds
    ) {}
}
