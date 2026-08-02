package com.petrick.vtt.network.server;

import com.petrick.vtt.feature.tabletop.VttSceneLimits;
import com.petrick.vtt.network.payload.VttTokenLifecycleRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class VttServerTokenLifecycleHandler {
    private VttServerTokenLifecycleHandler() {}

    public static void handle(VttTokenLifecycleRequestPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!VttServerRequestRateLimiter.allow(
                player, VttServerRequestRateLimiter.Category.TOKEN_LIFECYCLE)) return;
        if (request == null) {
            VttServerRequestRateLimiter.reject(
                    player, VttServerRequestRateLimiter.Category.TOKEN_LIFECYCLE,
                    "null request");
            return;
        }
        var state = VttServerTabletopState.get();
        boolean master = VttServerPlayerEvents.isMaster(player);
        if (!master && !"DELETE".equals(request.operation())) {
            VttServerRequestRateLimiter.reject(
                    player, VttServerRequestRateLimiter.Category.TOKEN_LIFECYCLE,
                    "permission denied");
            return;
        }
        if ("CREATE".equals(request.operation())) {
            var violation = VttSceneLimits.tokenCreation(state.activeScene());
            if (violation != null) {
                VttServerRequestRateLimiter.reject(
                        player, VttServerRequestRateLimiter.Category.TOKEN_LIFECYCLE,
                        violation.code());
                VttServerFeedback.showLimit(player, violation.message());
                var correction = state.tokenLifecycleCorrection(request.objectId());
                if (correction != null) PacketDistributor.sendToPlayer(player, correction);
                return;
            }
        }
        var update = state.applyTokenLifecycle(
                request, player.getUUID().toString(), master);
        if (update == null) {
            VttServerRequestRateLimiter.reject(
                    player, VttServerRequestRateLimiter.Category.TOKEN_LIFECYCLE,
                    "invalid " + request.operation() + " command");
            return;
        }
        for (ServerPlayer connected : player.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(connected, update);
        }
        VttServerVisionSourceSync.broadcast(player.getServer(), state);
    }
}
