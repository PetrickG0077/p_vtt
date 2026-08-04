package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.petrick.vtt.network.payload.VttCompositeAttachmentPlacementData;
import com.petrick.vtt.network.payload.VttSceneClipboardPastePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Validates and broadcasts one all-or-nothing scene clipboard paste. */
public final class VttServerSceneClipboardPasteHandler {
    private static final Gson GSON = new Gson();

    private VttServerSceneClipboardPasteHandler() {}

    public static void handle(VttSceneClipboardPastePayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || request == null
                || !VttServerPlayerEvents.isMaster(player)) return;
        if (!VttServerRequestRateLimiter.allow(
                player, VttServerRequestRateLimiter.Category.TOKEN_LIFECYCLE)) return;
        VttServerTabletopState state = VttServerTabletopState.get();
        if (request.authorityRevision() != state.authorityRevision()
                || state.activeScene() == null
                || !request.sceneId().equals(state.activeScene().getId())
                || request.clipboardJson() == null
                || request.clipboardJson().length()
                > VttSceneClipboardPastePayload.MAX_JSON_LENGTH) {
            VttServerFeedback.show(player, "Clipboard paste is stale; resynchronize and try again");
            return;
        }
        try {
            var data = GSON.fromJson(request.clipboardJson(),
                    VttCompositeAttachmentPlacementData.class);
            var result = state.applySceneClipboardPaste(
                    data, player.getUUID().toString());
            if (result == null) {
                VttServerFeedback.show(player, "Scene clipboard paste was rejected");
                return;
            }
            for (ServerPlayer connected : player.getServer().getPlayerList().getPlayers()) {
                result.objects().forEach(update ->
                        PacketDistributor.sendToPlayer(connected, update));
                result.lights().forEach(update ->
                        PacketDistributor.sendToPlayer(connected, update));
            }
            VttServerVisionSourceSync.broadcast(player.getServer(), state);
        } catch (RuntimeException exception) {
            VttServerFeedback.show(player, "Invalid scene clipboard data");
        }
    }
}
