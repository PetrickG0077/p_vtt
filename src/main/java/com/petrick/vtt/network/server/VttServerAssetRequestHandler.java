package com.petrick.vtt.network.server;

import com.petrick.vtt.network.payload.VttAssetRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Validates client cache misses against the server-created pending manifest. */
public final class VttServerAssetRequestHandler {
    private VttServerAssetRequestHandler() {}

    public static void handle(VttAssetRequestPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || request == null) return;
        if (!VttServerRequestRateLimiter.allow(
                player, VttServerRequestRateLimiter.Category.ASSET_SYNC_REQUEST)) return;
        VttServerAssetSyncService.handleRequest(player, request);
    }
}
