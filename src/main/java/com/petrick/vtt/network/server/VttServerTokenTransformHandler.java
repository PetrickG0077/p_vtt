package com.petrick.vtt.network.server;

import com.petrick.vtt.network.payload.VttTokenTransformRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class VttServerTokenTransformHandler {
    private VttServerTokenTransformHandler() {}

    public static void handle(VttTokenTransformRequestPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        var state = VttServerTabletopState.get();
        if (!VttServerRequestRateLimiter.allow(
                player, VttServerRequestRateLimiter.Category.TOKEN_TRANSFORM)) {
            if (!VttServerRequestRateLimiter.allow(
                    player, VttServerRequestRateLimiter.Category.TOKEN_TRANSFORM_CORRECTION)) return;
            var confirmed = state.currentTokenTransform(
                    request.sceneId(), request.objectId(), player.getUUID().toString(),
                    request.clientSequence());
            if (confirmed != null && VttServerVisionSourceSync.canReceiveObject(
                    player, state, request.objectId())) {
                PacketDistributor.sendToPlayer(player, confirmed);
            }
            return;
        }
        if (VttServerPlayerEvents.isSpectator(player)) {
            VttServerRequestRateLimiter.reject(
                    player, VttServerRequestRateLimiter.Category.TOKEN_TRANSFORM,
                    "spectators cannot transform tokens");
            var confirmed = state.currentTokenTransform(
                    request.sceneId(), request.objectId(), player.getUUID().toString(),
                    request.clientSequence());
            if (confirmed != null) PacketDistributor.sendToPlayer(player, confirmed);
            return;
        }
        var update = state.applyTokenTransform(
                request, player.getUUID().toString(), VttServerPlayerEvents.isMaster(player)
        );
        if (update == null) {
            VttServerRequestRateLimiter.reject(
                    player, VttServerRequestRateLimiter.Category.TOKEN_TRANSFORM,
                    "invalid or unauthorized transform for object " + request.objectId());
            var confirmed = state.currentTokenTransform(
                    request.sceneId(), request.objectId(), player.getUUID().toString(),
                    request.clientSequence());
            if (confirmed != null && VttServerVisionSourceSync.canReceiveObject(
                    player, state, request.objectId())) {
                PacketDistributor.sendToPlayer(player, confirmed);
            }
            return;
        }
        var dependencies = state.synchronizeAttachmentDependencies(update.objectId());
        VttServerVisionSourceSync.afterTokenTransform(
                player.getServer(), state, update.objectId());
        for (ServerPlayer connected : player.getServer().getPlayerList().getPlayers()) {
            if (VttServerVisionSourceSync.canReceiveObject(connected, state, update.objectId())) {
                PacketDistributor.sendToPlayer(connected, update);
            }
            for (var attachmentUpdate : dependencies.transforms()) {
                if (VttServerVisionSourceSync.canReceiveObject(
                        connected, state, attachmentUpdate.objectId())) {
                    PacketDistributor.sendToPlayer(connected, attachmentUpdate);
                }
            }
            for (var lightUpdate : dependencies.lights()) {
                PacketDistributor.sendToPlayer(connected, lightUpdate);
            }
        }
    }
}
