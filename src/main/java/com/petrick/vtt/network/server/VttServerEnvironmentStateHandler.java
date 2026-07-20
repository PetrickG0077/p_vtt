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
        if (!VttServerRequestRateLimiter.allow(
                player, VttServerRequestRateLimiter.Category.ENVIRONMENT)) return;
        if (!VttServerPlayerEvents.isMaster(player)) {
            VttServerRequestRateLimiter.reject(
                    player, VttServerRequestRateLimiter.Category.ENVIRONMENT,
                    "permission denied");
            return;
        }
        var state = VttServerTabletopState.get();
        var update = state.applyEnvironmentCommand(request, player.getUUID().toString());
        if (update == null) {
            VttServerRequestRateLimiter.reject(
                    player, VttServerRequestRateLimiter.Category.ENVIRONMENT,
                    "invalid environment command");
            return;
        }
        boolean changesVisionGeometry = VttEnvironmentCommandPayload.WALL.equals(update.entityType())
                || VttEnvironmentCommandPayload.DOOR.equals(update.entityType())
                || VttEnvironmentCommandPayload.VISION.equals(update.entityType());
        VttServerVisionSourceSync.broadcast(
                player.getServer(), state, changesVisionGeometry);
        for (ServerPlayer connected : player.getServer().getPlayerList().getPlayers()) {
            if (!VttEnvironmentCommandPayload.VISION.equals(update.entityType())
                    || VttServerVisionSourceSync.canReceiveObject(
                    connected, state, update.entityId())) {
                PacketDistributor.sendToPlayer(connected, update);
            }
        }
    }
}
