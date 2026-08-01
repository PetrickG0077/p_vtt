package com.petrick.vtt.network.client;

import com.petrick.vtt.core.session.VTTSession;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.network.payload.VttTokenOwnerCommandPayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Detects master-authored placed-token ownership changes, including Undo/Redo. */
public final class VttClientTokenOwnershipSync {
    private static final Map<String, String> LAST_OWNERS = new LinkedHashMap<>();
    private static long snapshotVersion = -1L;

    private VttClientTokenOwnershipSync() {
    }

    public static void tick(VTTSession session) {
        if (session == null || !session.hasNetworkSnapshot()) return;
        if (snapshotVersion != session.getNetworkSnapshotVersion()) {
            snapshotVersion = session.getNetworkSnapshotVersion();
            capture(session);
            return;
        }
        if (VttClientSceneHistorySync.isPending()) return;
        if (!session.isLocalMaster() || session.getActiveScene() == null) return;

        Map<String, String> current = owners(session);
        current.forEach((objectId, ownerId) -> {
            if (!LAST_OWNERS.containsKey(objectId)
                    || Objects.equals(ownerId, LAST_OWNERS.get(objectId))) return;
            PacketDistributor.sendToServer(new VttTokenOwnerCommandPayload(
                    session.getNetworkAuthorityRevision(),
                    session.getActiveScene().getId(),
                    objectId,
                    ownerId
            ));
        });
        LAST_OWNERS.clear();
        LAST_OWNERS.putAll(current);
    }

    public static void reset() {
        snapshotVersion = -1L;
        LAST_OWNERS.clear();
    }

    private static void capture(VTTSession session) {
        LAST_OWNERS.clear();
        LAST_OWNERS.putAll(owners(session));
    }

    private static Map<String, String> owners(VTTSession session) {
        Map<String, String> result = new LinkedHashMap<>();
        if (session.getActiveScene() == null) return result;
        for (VttSceneObject object : session.getActiveScene().getObjects()) {
            if (object != null && object.getId() != null) {
                result.put(object.getId(), object.getOwnerId() == null ? "" : object.getOwnerId());
            }
        }
        return result;
    }
}
