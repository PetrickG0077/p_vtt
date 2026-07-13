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
        var update = VttServerTabletopState.get().applyTokenTransform(
                request, player.getUUID().toString(), VttServerPlayerEvents.isMaster(player)
        );
        if (update == null) {
            VTT.LOGGER.warn("Rejected VTT token transform from {} for object {}",
                    player.getGameProfile().getName(), request.objectId());
            var confirmed = VttServerTabletopState.get().currentTokenTransform(
                    request.objectId(), player.getUUID().toString());
            if (confirmed != null) PacketDistributor.sendToPlayer(player, confirmed);
            return;
        }
        PacketDistributor.sendToAllPlayers(update);
    }
}
