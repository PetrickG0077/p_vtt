package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttTokenTransformRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class VttServerTokenTransformHandler {
    private VttServerTokenTransformHandler() {}

    public static void handle(VttTokenTransformRequestPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        var state = VttServerTabletopState.get();
        var update = state.applyTokenTransform(
                request, player.getUUID().toString(), VttServerPlayerEvents.isMaster(player)
        );
        if (update == null) {
            VTT.LOGGER.warn("Rejected VTT token transform from {} for object {}",
                    player.getGameProfile().getName(), request.objectId());
            var confirmed = state.currentTokenTransform(
                    request.sceneId(), request.objectId(), player.getUUID().toString(),
                    request.clientSequence());
            if (confirmed != null) {
                PacketDistributor.sendToPlayer(player, confirmed);
            } else {
                PacketDistributor.sendToPlayer(player, state.createSnapshotPayload());
                VttServerVisionSourceSync.sendToPlayer(player, state);
            }
            return;
        }
        PacketDistributor.sendToAllPlayers(update);
        VttServerVisionSourceSync.broadcast(player.getServer(), state);
    }
}
