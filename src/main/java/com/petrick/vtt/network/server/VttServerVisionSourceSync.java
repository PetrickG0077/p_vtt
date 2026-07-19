package com.petrick.vtt.network.server;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.VttScene;
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

/** Resolves and sends each player's server-authoritative vision sources. */
public final class VttServerVisionSourceSync {
    private static final double DEFAULT_OUTER_RADIUS = 512.0;
    private static final Map<UUID, Long> REVISIONS = new HashMap<>();
    private static final SceneVisionGeometry GEOMETRY = new SceneVisionGeometry();
    private static final SceneVisionRaycaster RAYCASTER = new SceneVisionRaycaster();

    private VttServerVisionSourceSync() {}

    public static void sendToPlayer(ServerPlayer player, VttServerTabletopState state) {
        if (player == null || state == null || state.activeScene() == null
                || VttServerPlayerEvents.isMaster(player)) return;
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
        PacketDistributor.sendToPlayer(player, new VttVisionSourcesPayload(
                state.authorityRevision(), nextRevision(player.getUUID()), scene.getId(),
                !ownsDisabledToken, regions));
    }

    public static void broadcast(MinecraftServer server, VttServerTabletopState state) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendToPlayer(player, state);
    }

    public static void sendForMovedObject(
            MinecraftServer server, VttServerTabletopState state, String objectId
    ) {
        if (server == null || state == null || state.activeScene() == null || objectId == null) return;
        String ownerId = state.activeScene().getObjects().stream()
                .filter(object -> object != null && objectId.equals(object.getId())
                        && object.isVisionEnabled())
                .map(object -> object.getOwnerId())
                .filter(id -> id != null && !id.isBlank())
                .findFirst().orElse(null);
        if (ownerId == null) return;
        try {
            ServerPlayer owner = server.getPlayerList().getPlayer(UUID.fromString(ownerId));
            if (owner != null) sendToPlayer(owner, state);
        } catch (IllegalArgumentException ignored) {
            // Legacy owner strings are not authoritative multiplayer UUIDs.
        }
    }

    public static void forget(UUID playerId) {
        if (playerId != null) REVISIONS.remove(playerId);
    }

    private static long nextRevision(UUID playerId) {
        long current = REVISIONS.getOrDefault(playerId, 0L);
        long next = current == Long.MAX_VALUE ? 1L : current + 1L;
        REVISIONS.put(playerId, next);
        return next;
    }
}
