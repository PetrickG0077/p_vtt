package com.petrick.vtt.network.client;

import com.petrick.vtt.core.session.VTTSession;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.network.payload.VttTokenTransformRequestPayload;
import com.petrick.vtt.network.payload.VttTokenTransformUpdatePayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.gui.screens.Screen;

import java.util.HashMap;
import java.util.Map;

public final class VttClientTokenTransformSync {
    private static final Map<String, TokenState> LAST_SENT = new HashMap<>();
    private static final Map<String, Long> LAST_SENT_AT = new HashMap<>();
    private static final Map<String, Long> COLLISION_BYPASS_UNTIL = new HashMap<>();
    private static final long SEND_INTERVAL_MS = 100L;
    private static long snapshotVersion = -1L;

    private VttClientTokenTransformSync() {}

    public static void tick(VTTSession session) {
        if (session == null || !session.hasNetworkSnapshot()) return;
        if (snapshotVersion != session.getNetworkSnapshotVersion()) {
            snapshotVersion = session.getNetworkSnapshotVersion();
            capture(session);
            return;
        }

        for (CanvasObject object : session.getCanvasScene().getObjects()) {
            if (!object.hasSourceTokenDefinition()) continue;
            TokenState current = TokenState.from(object);
            if (current.equals(LAST_SENT.get(object.id()))) continue;
            long now = System.currentTimeMillis();
            if (now - LAST_SENT_AT.getOrDefault(object.id(), 0L) < SEND_INTERVAL_MS) continue;
            LAST_SENT.put(object.id(), current);
            LAST_SENT_AT.put(object.id(), now);
            boolean bypassCollision = session.isLocalMaster()
                    && (Screen.hasAltDown() || consumeCollisionBypass(object.id(), now));
            PacketDistributor.sendToServer(new VttTokenTransformRequestPayload(
                    object.id(), current.x(), current.y(), current.rotationDegrees(),
                    current.flippedHorizontally(), current.activeStateId(), bypassCollision
            ));
        }
    }

    public static void markCollisionBypass(Iterable<String> objectIds) {
        if (objectIds == null) return;
        long until = System.currentTimeMillis() + 1_000L;
        for (String objectId : objectIds) {
            if (objectId != null && !objectId.isBlank()) COLLISION_BYPASS_UNTIL.put(objectId, until);
        }
    }

    private static boolean consumeCollisionBypass(String objectId, long now) {
        Long until = COLLISION_BYPASS_UNTIL.remove(objectId);
        return until != null && until >= now;
    }

    public static void acceptConfirmed(VTTSession session, VttTokenTransformUpdatePayload update) {
        if (session == null || update == null) return;
        boolean ownAcceptedUpdate = update.accepted()
                && update.originPlayerId().equals(session.getLocalPlayerId());
        if (ownAcceptedUpdate) {
            return;
        }
        LAST_SENT.put(update.objectId(), new TokenState(update.x(), update.y(), update.rotationDegrees(),
                update.flippedHorizontally(), update.activeStateId()));
        LAST_SENT_AT.put(update.objectId(), System.currentTimeMillis());
        session.applyConfirmedTokenTransform(update);
    }

    public static void reset() {
        snapshotVersion = -1L;
        LAST_SENT.clear();
        LAST_SENT_AT.clear();
        COLLISION_BYPASS_UNTIL.clear();
    }

    private static void capture(VTTSession session) {
        LAST_SENT.clear();
        LAST_SENT_AT.clear();
        for (CanvasObject object : session.getCanvasScene().getObjects()) {
            if (object.hasSourceTokenDefinition()) LAST_SENT.put(object.id(), TokenState.from(object));
        }
    }

    private record TokenState(double x, double y, double rotationDegrees,
                              boolean flippedHorizontally, String activeStateId) {
        private static TokenState from(CanvasObject object) {
            return new TokenState(object.transform().position().x(), object.transform().position().y(),
                    object.transform().rotationDegrees(), object.flippedHorizontally(), object.activeStateId());
        }
    }
}
