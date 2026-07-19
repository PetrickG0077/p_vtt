package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttTokenLifecycleRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class VttServerTokenLifecycleHandler {
    private VttServerTokenLifecycleHandler() {}

    public static void handle(VttTokenLifecycleRequestPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        var state = VttServerTabletopState.get();
        var update = state.applyTokenLifecycle(
                request, player.getUUID().toString(), VttServerPlayerEvents.isMaster(player));
        if (update == null) {
            VTT.LOGGER.warn("Rejected VTT token lifecycle request {} from {}",
                    request.operation(), player.getGameProfile().getName());
            PacketDistributor.sendToPlayer(player, state.createSnapshotPayload());
            VttServerVisionSourceSync.sendToPlayer(player, state);
            return;
        }
        PacketDistributor.sendToAllPlayers(update);
        VttServerVisionSourceSync.broadcast(player.getServer(), state);
    }
}
