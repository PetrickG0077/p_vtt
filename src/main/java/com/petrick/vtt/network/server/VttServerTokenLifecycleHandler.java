package com.petrick.vtt.network.server;

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
        if (!VttServerPlayerEvents.isMaster(player)) {
            VttServerRequestRateLimiter.reject(
                    player, VttServerRequestRateLimiter.Category.TOKEN_LIFECYCLE,
                    "permission denied");
            return;
        }
        var state = VttServerTabletopState.get();
        var update = state.applyTokenLifecycle(
                request, player.getUUID().toString(), VttServerPlayerEvents.isMaster(player));
        if (update == null) {
            VttServerRequestRateLimiter.reject(
                    player, VttServerRequestRateLimiter.Category.TOKEN_LIFECYCLE,
                    "invalid " + request.operation() + " command");
            return;
        }
        for (ServerPlayer connected : player.getServer().getPlayerList().getPlayers()) {
            if (VttServerPlayerEvents.isMaster(connected)) {
                PacketDistributor.sendToPlayer(connected, update);
            }
        }
        VttServerVisionSourceSync.broadcast(player.getServer(), state);
    }
}
