package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.VTT;
import com.petrick.vtt.core.session.VttPlayerRosterEntry;
import com.petrick.vtt.core.session.VttRole;
import com.petrick.vtt.network.payload.VttIdentityPayload;
import com.petrick.vtt.network.payload.VttPlayerRosterPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;

@EventBusSubscriber(modid = VTT.MOD_ID)
public final class VttServerPlayerEvents {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<UUID, VttRole> LAST_ROLES = new HashMap<>();

    private VttServerPlayerEvents() {
    }

    public static boolean isMaster(ServerPlayer player) {
        return player != null && player.hasPermissions(2);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        sendRole(player);
        broadcastRoster(player.getServer(), null);
        VttServerTabletopState state = VttServerTabletopState.get();
        VttServerVisionSourceSync.markCurrentAssetsSent(player, state);
        VttServerAssetSyncService.sendActiveSceneAssets(
                player, state.replicatedSceneFor(player), isMaster(player), () -> {
                    VttServerSceneSnapshotSync.sendToPlayer(player, state);
                    VttServerVisionSourceSync.sendToPlayer(player, state);
                });
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        VttRole current = isMaster(player) ? VttRole.MASTER : VttRole.PLAYER;
        VttServerTabletopState state = VttServerTabletopState.get();
        if (LAST_ROLES.get(player.getUUID()) != current) {
            sendRole(player);
            broadcastRoster(player.getServer(), null);
            VttServerVisionSourceSync.markCurrentAssetsSent(player, state);
            VttServerAssetSyncService.sendActiveSceneAssets(
                    player, state.replicatedSceneFor(player), current == VttRole.MASTER, () -> {
                        VttServerSceneSnapshotSync.sendToPlayer(player, state);
                        VttServerVisionSourceSync.sendToPlayer(player, state);
                    });
            return;
        }
        VttServerVisionSourceSync.heartbeat(player, state);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            broadcastRoster(player.getServer(), player.getUUID());
        }
        LAST_ROLES.remove(event.getEntity().getUUID());
        VttServerVisionSourceSync.forget(event.getEntity().getUUID());
        VttServerAssetSyncService.forget(event.getEntity().getUUID());
        VttServerRequestRateLimiter.forget(event.getEntity().getUUID());
    }

    private static VttRole sendRole(ServerPlayer player) {
        VttRole role = isMaster(player) ? VttRole.MASTER : VttRole.PLAYER;
        LAST_ROLES.put(player.getUUID(), role);
        VTT.LOGGER.info("Assigned VTT {} role to {} ({}) based on server permissions",
                role, player.getGameProfile().getName(), player.getUUID());
        PacketDistributor.sendToPlayer(player,
                new VttIdentityPayload(player.getUUID().toString(), role.name()));
        return role;
    }

    private static void broadcastRoster(MinecraftServer server, UUID excludedPlayerId) {
        if (server == null) return;
        List<VttPlayerRosterEntry> roster = server.getPlayerList().getPlayers().stream()
                .filter(player -> excludedPlayerId == null
                        || !excludedPlayerId.equals(player.getUUID()))
                .map(player -> new VttPlayerRosterEntry(
                        player.getUUID().toString(),
                        player.getGameProfile().getName(),
                        isMaster(player) ? VttRole.MASTER : VttRole.PLAYER))
                .toList();
        VttPlayerRosterPayload payload = new VttPlayerRosterPayload(GSON.toJson(roster));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (excludedPlayerId == null || !excludedPlayerId.equals(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
