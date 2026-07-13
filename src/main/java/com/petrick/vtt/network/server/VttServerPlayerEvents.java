package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.core.session.VttRole;
import com.petrick.vtt.network.payload.VttIdentityPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

@EventBusSubscriber(modid = VTT.MOD_ID)
public final class VttServerPlayerEvents {

    private static UUID masterPlayerId;

    private VttServerPlayerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (masterPlayerId == null) {
            masterPlayerId = player.getUUID();
            VTT.LOGGER.info("Assigned VTT master role to {} ({})", player.getGameProfile().getName(), masterPlayerId);
        }

        VttRole role = masterPlayerId.equals(player.getUUID()) ? VttRole.MASTER : VttRole.PLAYER;
        PacketDistributor.sendToPlayer(
                player,
                new VttIdentityPayload(player.getUUID().toString(), role.name())
        );
        VttServerTabletopState state = VttServerTabletopState.get();
        VttServerAssetSyncService.sendActiveSceneAssets(player, state.activeScene());
        PacketDistributor.sendToPlayer(player, state.createSnapshotPayload());
    }
}
