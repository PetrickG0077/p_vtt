package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.petrick.vtt.network.payload.VttCompositeAttachmentPlacementData;
import com.petrick.vtt.network.payload.VttCompositeAttachmentPlacementPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Applies a composite attachment placement as one authoritative transaction. */
public final class VttServerCompositeAttachmentPlacementHandler {
    private static final Gson GSON = new Gson();
    private VttServerCompositeAttachmentPlacementHandler() {}

    public static void handle(VttCompositeAttachmentPlacementPayload request,
                              IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || request == null) return;
        if (!VttServerPlayerEvents.isMaster(player)) return;
        if (!VttServerRequestRateLimiter.allow(
                player, VttServerRequestRateLimiter.Category.TOKEN_LIFECYCLE)) return;
        VttServerTabletopState state = VttServerTabletopState.get();
        if (request.authorityRevision() != state.authorityRevision()
                || state.activeScene() == null
                || !request.sceneId().equals(state.activeScene().getId())
                || request.placementJson() == null
                || request.placementJson().length()
                > VttCompositeAttachmentPlacementPayload.MAX_JSON_LENGTH) {
            VttServerFeedback.show(player, "Composite placement is stale or invalid; resynchronize");
            return;
        }
        try {
            var data = GSON.fromJson(
                    request.placementJson(), VttCompositeAttachmentPlacementData.class);
            var result = state.applyCompositeAttachmentPlacement(
                    data, player.getUUID().toString());
            if (result == null) {
                VttServerFeedback.show(player, "Composite attachment placement was rejected");
                return;
            }
            for (ServerPlayer connected : player.getServer().getPlayerList().getPlayers()) {
                result.objects().forEach(update ->
                        PacketDistributor.sendToPlayer(connected, update));
                result.lights().forEach(update ->
                        PacketDistributor.sendToPlayer(connected, update));
            }
            VttServerVisionSourceSync.broadcast(player.getServer(), state);
        } catch (RuntimeException ignored) {
            VttServerFeedback.show(player, "Invalid composite attachment data");
        }
    }
}
