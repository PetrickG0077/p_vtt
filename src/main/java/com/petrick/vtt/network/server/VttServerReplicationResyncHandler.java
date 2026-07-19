package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttReplicationResyncRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sends a private recovery snapshot, with a server-side rate limit against request spam. */
public final class VttServerReplicationResyncHandler {
    private static final long MIN_REQUEST_INTERVAL_MS = 2_000L;
    private static final Map<UUID, Long> LAST_REQUEST_AT = new HashMap<>();

    private VttServerReplicationResyncHandler() {}

    public static void handle(
            VttReplicationResyncRequestPayload request, IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player) || request == null) return;
        long now = System.currentTimeMillis();
        long previous = LAST_REQUEST_AT.getOrDefault(player.getUUID(), 0L);
        if (now - previous < MIN_REQUEST_INTERVAL_MS) return;
        LAST_REQUEST_AT.put(player.getUUID(), now);

        VttServerTabletopState state = VttServerTabletopState.get();
        VttServerVisionSourceSync.resetPlayerScope(player.getUUID());
        var replicatedScene = state.replicatedSceneFor(player);
        VttServerAssetSyncService.sendActiveSceneAssets(
                player, replicatedScene, VttServerPlayerEvents.isMaster(player));
        VttServerVisionSourceSync.markCurrentAssetsSent(player, state);
        PacketDistributor.sendToPlayer(player, state.createSnapshotPayload(player));
        VttServerVisionSourceSync.sendToPlayer(player, state);
        VTT.LOGGER.warn(
                "Recovered VTT replication for {} after {} (client A={}, V={}, R={})",
                player.getGameProfile().getName(), request.reason(), request.authorityRevision(),
                request.lastVisionRevision(), request.lastReplicationRevision());
    }

    public static void forget(UUID playerId) {
        if (playerId != null) LAST_REQUEST_AT.remove(playerId);
    }
}
