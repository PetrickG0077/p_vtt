package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.petrick.vtt.network.payload.VttSceneClipboardCutPayload;
import com.petrick.vtt.network.payload.VttSceneClipboardPasteResultPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Arrays;
import java.util.LinkedHashSet;

/** Applies one all-or-nothing authoritative clipboard cut. */
public final class VttServerSceneClipboardCutHandler {
    private static final Gson GSON = new Gson();

    private VttServerSceneClipboardCutHandler() {}

    public static void handle(VttSceneClipboardCutPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || request == null) return;
        if (request.requestId() == null || request.requestId().isBlank()
                || request.requestId().length() > 64) return;
        if (!VttServerPlayerEvents.isMaster(player)) {
            reply(player, request, false, "Only masters can cut scene objects");
            return;
        }
        if (!VttServerRequestRateLimiter.allow(
                player, VttServerRequestRateLimiter.Category.TOKEN_LIFECYCLE)) {
            reply(player, request, false, "Too many cut requests; try again shortly");
            return;
        }
        VttServerTabletopState state = VttServerTabletopState.get();
        if (request.authorityRevision() != state.authorityRevision()
                || state.activeScene() == null
                || !request.sceneId().equals(state.activeScene().getId())
                || request.objectIdsJson() == null || request.lightIdsJson() == null
                || request.objectIdsJson().length() > VttSceneClipboardCutPayload.MAX_IDS_JSON_LENGTH
                || request.lightIdsJson().length() > VttSceneClipboardCutPayload.MAX_IDS_JSON_LENGTH) {
            reply(player, request, false, "Clipboard cut is stale; resynchronize and try again");
            return;
        }
        try {
            String[] objects = GSON.fromJson(request.objectIdsJson(), String[].class);
            String[] lights = GSON.fromJson(request.lightIdsJson(), String[].class);
            var result = state.applySceneClipboardCut(
                    new LinkedHashSet<>(Arrays.asList(objects)),
                    new LinkedHashSet<>(Arrays.asList(lights)),
                    player.getUUID().toString());
            if (result == null) {
                reply(player, request, false, "Scene clipboard cut was rejected");
                return;
            }
            for (ServerPlayer connected : player.getServer().getPlayerList().getPlayers()) {
                result.objects().forEach(update -> PacketDistributor.sendToPlayer(connected, update));
                result.lights().forEach(update -> PacketDistributor.sendToPlayer(connected, update));
            }
            VttServerVisionSourceSync.broadcast(player.getServer(), state);
            reply(player, request, true, "Cut as one editor action");
        } catch (RuntimeException exception) {
            reply(player, request, false, "Invalid scene clipboard cut data");
        }
    }

    private static void reply(ServerPlayer player, VttSceneClipboardCutPayload request,
                              boolean success, String message) {
        PacketDistributor.sendToPlayer(player, new VttSceneClipboardPasteResultPayload(
                request.requestId(), success, message));
    }
}
