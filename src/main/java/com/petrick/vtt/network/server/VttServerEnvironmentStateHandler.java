package com.petrick.vtt.network.server;

import com.petrick.vtt.network.payload.VttEnvironmentStateRequestPayload;
import com.petrick.vtt.network.payload.VttEnvironmentCommandPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class VttServerEnvironmentStateHandler {
    private VttServerEnvironmentStateHandler() {}

    public static void handle(VttEnvironmentStateRequestPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!VttServerRequestRateLimiter.allow(
                player, VttServerRequestRateLimiter.Category.ENVIRONMENT)) return;
        if (!VttServerPlayerEvents.isMaster(player)) {
            VttServerRequestRateLimiter.reject(
                    player, VttServerRequestRateLimiter.Category.ENVIRONMENT,
                    "permission denied");
            return;
        }
        if (request == null) {
            VttServerRequestRateLimiter.reject(
                    player, VttServerRequestRateLimiter.Category.ENVIRONMENT,
                    "null request");
            return;
        }
        var state = VttServerTabletopState.get();
        var update = state.applyEnvironmentState(request);
        if (update == null) {
            VttServerRequestRateLimiter.reject(
                    player, VttServerRequestRateLimiter.Category.ENVIRONMENT,
                    "invalid door/fog update");
            return;
        }
        VttServerVisionSourceSync.broadcast(player.getServer(), state, true);
        for (ServerPlayer connected : player.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(connected, state.currentEnvironmentState(connected));
        }
    }

    public static void handleCommand(VttEnvironmentCommandPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        boolean mapRequest = request != null
                && VttEnvironmentCommandPayload.MAP.equals(request.entityType());
        VttServerRequestRateLimiter.Category category = mapRequest
                ? VttServerRequestRateLimiter.Category.SCENE_MAP
                : VttServerRequestRateLimiter.Category.ENVIRONMENT;
        if (!VttServerRequestRateLimiter.allow(
                player, category)) {
            showEnvironmentRejection(player, request,
                    mapRequest
                            ? "Too many map changes; try again shortly"
                            : "Too many environment changes; try again shortly");
            sendCorrection(player, request);
            return;
        }
        if (!VttServerPlayerEvents.isMaster(player)) {
            VttServerRequestRateLimiter.reject(
                    player, category,
                    "permission denied");
            showEnvironmentRejection(player, request,
                    "Only masters can edit the tabletop environment");
            sendCorrection(player, request);
            return;
        }
        if (request == null) {
            VttServerRequestRateLimiter.reject(
                    player, VttServerRequestRateLimiter.Category.ENVIRONMENT,
                    "null request");
            return;
        }
        var state = VttServerTabletopState.get();
        if (VttEnvironmentCommandPayload.UPSERT.equals(request.operation())) {
            var violation = state.environmentLimitViolation(request);
            if (violation != null) {
                VttServerRequestRateLimiter.reject(
                        player, category,
                        violation.code());
                VttServerFeedback.showLimit(player, violation.message());
                sendCorrection(player, request);
                return;
            }
        }
        boolean existingMap = VttEnvironmentCommandPayload.MAP.equals(request.entityType())
                && state.activeScene().getMaps().stream().anyMatch(
                map -> map != null && request.entityId().equals(map.getId()));
        var update = state.applyEnvironmentCommand(request, player.getUUID().toString());
        if (update == null) {
            VttServerRequestRateLimiter.reject(
                    player, category,
                    "invalid environment command");
            showEnvironmentRejection(player, request,
                    "The server rejected the environment change");
            sendCorrection(player, request);
            return;
        }
        boolean changesVisionGeometry = VttEnvironmentCommandPayload.WALL.equals(update.entityType())
                || VttEnvironmentCommandPayload.DOOR.equals(update.entityType())
                || VttEnvironmentCommandPayload.VISION.equals(update.entityType())
                || VttEnvironmentCommandPayload.LIGHTING_CONFIG.equals(update.entityType());
        VttServerVisionSourceSync.broadcast(
                player.getServer(), state, changesVisionGeometry);
        boolean changesAssets = VttEnvironmentCommandPayload.MAP.equals(update.entityType())
                && VttEnvironmentCommandPayload.UPSERT.equals(update.operation())
                && !existingMap;
        for (ServerPlayer connected : player.getServer().getPlayerList().getPlayers()) {
            if (changesAssets) {
                VttServerVisionSourceSync.markCurrentAssetsSent(connected, state);
                VttServerAssetSyncService.sendActiveSceneAssets(
                        connected, state.replicatedSceneFor(connected),
                        VttServerPlayerEvents.isMaster(connected),
                        () -> PacketDistributor.sendToPlayer(connected, update));
                continue;
            }
            if (!VttEnvironmentCommandPayload.VISION.equals(update.entityType())
                    || VttServerVisionSourceSync.canReceiveObject(
                    connected, state, update.entityId())) {
                PacketDistributor.sendToPlayer(connected, update);
            }
        }
    }

    private static void sendCorrection(
            ServerPlayer player, VttEnvironmentCommandPayload request
    ) {
        if (player == null || request == null) return;
        VttServerTabletopState state = VttServerTabletopState.get();
        var correction = state.environmentCorrection(request);
        if (correction != null) {
            PacketDistributor.sendToPlayer(player, correction);
            return;
        }
        VttServerVisionSourceSync.markCurrentAssetsSent(player, state);
        VttServerAssetSyncService.sendActiveSceneAssets(
                player, state.replicatedSceneFor(player),
                VttServerPlayerEvents.isMaster(player), () -> {
                    VttServerSceneSnapshotSync.sendToPlayer(player, state);
                    VttServerVisionSourceSync.sendToPlayer(player, state);
                });
    }

    private static void showEnvironmentRejection(
            ServerPlayer player, VttEnvironmentCommandPayload request, String message
    ) {
        if (request != null) VttServerFeedback.show(player, message);
    }
}
