package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttEnvironmentStateRequestPayload;
import com.petrick.vtt.network.payload.VttEnvironmentCommandPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class VttServerEnvironmentStateHandler {
    private VttServerEnvironmentStateHandler() {}

    public static void handle(VttEnvironmentStateRequestPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        var state = VttServerTabletopState.get();
        var update = VttServerPlayerEvents.isMaster(player) ? state.applyEnvironmentState(request) : null;
        if (update == null) {
            VTT.LOGGER.warn("Rejected VTT door/fog update from {}", player.getGameProfile().getName());
            PacketDistributor.sendToPlayer(player, state.currentEnvironmentState(player));
            return;
        }
        VttServerVisionSourceSync.broadcast(player.getServer(), state, true);
        for (ServerPlayer connected : player.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(connected, state.currentEnvironmentState(connected));
        }
    }

    public static void handleCommand(VttEnvironmentCommandPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        var state = VttServerTabletopState.get();
        var update = VttServerPlayerEvents.isMaster(player)
                ? state.applyEnvironmentCommand(request, player.getUUID().toString()) : null;
        if (update == null) {
            VTT.LOGGER.warn("Rejected VTT environment command from {}",
                    player.getGameProfile().getName());
            PacketDistributor.sendToPlayer(player, state.currentEnvironmentState(player));
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
