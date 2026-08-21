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
    private static final Map<String, Interpolation> INTERPOLATIONS = new HashMap<>();
    private static final Map<String, Long> NEXT_SEQUENCE = new HashMap<>();
    private static final Map<String, Long> LATEST_REVISION = new HashMap<>();
    private static final long SEND_INTERVAL_MS = 100L;
    private static final long INTERPOLATION_DURATION_MS = 140L;
    private static final double TELEPORT_DISTANCE_SQUARED = 512.0 * 512.0;
    private static long snapshotVersion = -1L;
    private static long authorityRevision = -1L;

    private VttClientTokenTransformSync() {}

    public static void tick(VTTSession session) {
        if (session == null || !session.hasNetworkSnapshot()) return;
        if (snapshotVersion != session.getNetworkSnapshotVersion()) {
            snapshotVersion = session.getNetworkSnapshotVersion();
            INTERPOLATIONS.clear();
            if (authorityRevision != session.getNetworkAuthorityRevision()) {
                authorityRevision = session.getNetworkAuthorityRevision();
                NEXT_SEQUENCE.clear();
                LATEST_REVISION.clear();
            }
            capture(session);
            return;
        }

        if (VttClientSceneHistorySync.isPending()) return;

        long now = System.currentTimeMillis();
        advanceInterpolations(session, now);

        for (CanvasObject object : session.getCanvasScene().getObjects()) {
            if (!isSynchronizableObject(object)) continue;
            if (INTERPOLATIONS.containsKey(object.id())) continue;
            TokenState current = TokenState.from(object,
                    session.getCanvasScene().getObjectLayerIndex(object.id()),
                    tintColor(session, object.id()), renderAboveMasks(session, object.id()));
            if (current.equals(LAST_SENT.get(object.id()))) continue;
            if (now - LAST_SENT_AT.getOrDefault(object.id(), 0L) < SEND_INTERVAL_MS) continue;
            LAST_SENT.put(object.id(), current);
            LAST_SENT_AT.put(object.id(), now);
            boolean bypassCollision = session.isLocalMaster()
                    && (Screen.hasAltDown() || consumeCollisionBypass(object.id(), now));
            long clientSequence = NEXT_SEQUENCE.merge(object.id(), 1L, Long::sum);
            PacketDistributor.sendToServer(new VttTokenTransformRequestPayload(
                    session.getNetworkAuthorityRevision(), clientSequence,
                    session.getActiveScene().getId(), object.id(),
                    current.x(), current.y(), current.rotationDegrees(),
                    current.scaleX(), current.scaleY(), current.layerIndex(),
                    current.flippedHorizontally(), current.visible(),
                    current.displayName(), current.activeStateId(), current.tintColorRgb(),
                    current.renderAboveMasks(),
                    bypassCollision
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
        if (session.getActiveScene() == null
                || update.authorityRevision() != session.getNetworkAuthorityRevision()
                || !update.sceneId().equals(session.getActiveScene().getId())) return;
        long latestRevision = LATEST_REVISION.getOrDefault(update.objectId(), -1L);
        if (update.entityRevision() < latestRevision
                || update.entityRevision() == latestRevision && update.accepted()) return;
        LATEST_REVISION.put(update.objectId(), update.entityRevision());
        boolean ownAcceptedUpdate = update.accepted()
                && update.originPlayerId().equals(session.getLocalPlayerId());
        if (ownAcceptedUpdate) {
            INTERPOLATIONS.remove(update.objectId());
            return;
        }
        LAST_SENT.put(update.objectId(), new TokenState(update.x(), update.y(), update.rotationDegrees(),
                update.scaleX(), update.scaleY(), update.layerIndex(),
                update.flippedHorizontally(), update.visible(),
                update.displayName(), update.activeStateId(), update.tintColorRgb(),
                update.renderAboveMasks()));
        LAST_SENT_AT.put(update.objectId(), System.currentTimeMillis());

        CanvasObject object = session.getCanvasScene().findObjectById(update.objectId());
        if (object == null) {
            INTERPOLATIONS.remove(update.objectId());
            session.applyConfirmedTokenTransform(update);
            return;
        }

        // A rejected transform is an authoritative correction, normally caused by
        // collision, ownership or rate-limit validation. Applying it immediately
        // prevents the locally predicted token from visually interpolating through
        // a wall before returning to the last valid server position.
        if (!update.accepted()) {
            INTERPOLATIONS.remove(update.objectId());
            session.applyConfirmedTokenTransform(update);
            return;
        }

        TokenState start = TokenState.from(object,
                session.getCanvasScene().getObjectLayerIndex(object.id()),
                tintColor(session, object.id()), renderAboveMasks(session, object.id()));
        TokenState target = TokenState.from(update);
        if (shouldSnap(start, target) || !hasContinuousDifference(start, target)) {
            INTERPOLATIONS.remove(update.objectId());
            session.applyConfirmedTokenTransform(update);
            return;
        }

        INTERPOLATIONS.put(update.objectId(), new Interpolation(
                start, target, update.originPlayerId(), update.accepted(),
                update.authorityRevision(), update.entityRevision(), update.clientSequence(),
                System.currentTimeMillis(), INTERPOLATION_DURATION_MS, start));
    }

    public static void reset() {
        snapshotVersion = -1L;
        LAST_SENT.clear();
        LAST_SENT_AT.clear();
        COLLISION_BYPASS_UNTIL.clear();
        INTERPOLATIONS.clear();
        NEXT_SEQUENCE.clear();
        LATEST_REVISION.clear();
        authorityRevision = -1L;
    }

    private static void capture(VTTSession session) {
        LAST_SENT.clear();
        LAST_SENT_AT.clear();
        for (CanvasObject object : session.getCanvasScene().getObjects()) {
            if (isSynchronizableObject(object)) LAST_SENT.put(object.id(), TokenState.from(
                    object, session.getCanvasScene().getObjectLayerIndex(object.id()),
                    tintColor(session, object.id()), renderAboveMasks(session, object.id())));
        }
    }

    private static void advanceInterpolations(VTTSession session, long now) {
        INTERPOLATIONS.entrySet().removeIf(entry ->
                session.getCanvasScene().findObjectById(entry.getKey()) == null);
        for (var entry : Map.copyOf(INTERPOLATIONS).entrySet()) {
            Interpolation interpolation = entry.getValue();
            CanvasObject object = session.getCanvasScene().findObjectById(entry.getKey());
            TokenState current = TokenState.from(object,
                    session.getCanvasScene().getObjectLayerIndex(object.id()),
                    tintColor(session, object.id()), renderAboveMasks(session, object.id()));
            if (!current.equals(interpolation.lastDisplayed())) {
                INTERPOLATIONS.remove(entry.getKey());
                continue;
            }
            double progress = Math.min(1.0,
                    (now - interpolation.startedAtMs()) / (double) interpolation.durationMs());
            TokenState displayed = interpolate(interpolation.start(), interpolation.target(), progress);
            session.applyConfirmedTokenTransform(displayed.toUpdate(
                    interpolation.authorityRevision(), interpolation.entityRevision(),
                    interpolation.clientSequence(), session.getActiveScene().getId(), entry.getKey(),
                    interpolation.originPlayerId(), interpolation.accepted()));
            if (progress >= 1.0) {
                INTERPOLATIONS.remove(entry.getKey());
            } else {
                INTERPOLATIONS.put(entry.getKey(), interpolation.withLastDisplayed(displayed));
            }
        }
    }

    private static boolean shouldSnap(TokenState start, TokenState target) {
        double deltaX = target.x() - start.x();
        double deltaY = target.y() - start.y();
        return deltaX * deltaX + deltaY * deltaY > TELEPORT_DISTANCE_SQUARED
                || !finite(start) || !finite(target);
    }

    private static boolean hasContinuousDifference(TokenState start, TokenState target) {
        return Double.compare(start.x(), target.x()) != 0
                || Double.compare(start.y(), target.y()) != 0
                || Double.compare(start.rotationDegrees(), target.rotationDegrees()) != 0
                || Double.compare(start.scaleX(), target.scaleX()) != 0
                || Double.compare(start.scaleY(), target.scaleY()) != 0;
    }

    private static boolean finite(TokenState state) {
        return Double.isFinite(state.x()) && Double.isFinite(state.y())
                && Double.isFinite(state.rotationDegrees())
                && Double.isFinite(state.scaleX()) && Double.isFinite(state.scaleY());
    }

    private static TokenState interpolate(TokenState start, TokenState target, double progress) {
        return new TokenState(
                lerp(start.x(), target.x(), progress),
                lerp(start.y(), target.y(), progress),
                lerpAngle(start.rotationDegrees(), target.rotationDegrees(), progress),
                lerp(start.scaleX(), target.scaleX(), progress),
                lerp(start.scaleY(), target.scaleY(), progress),
                target.layerIndex(), target.flippedHorizontally(), target.visible(),
                target.displayName(), target.activeStateId(), target.tintColorRgb(),
                target.renderAboveMasks());
    }

    private static double lerp(double start, double end, double progress) {
        return start + (end - start) * progress;
    }

    private static double lerpAngle(double start, double end, double progress) {
        double delta = ((end - start + 540.0) % 360.0) - 180.0;
        double result = (start + delta * progress) % 360.0;
        return result < 0.0 ? result + 360.0 : result;
    }

    private record TokenState(double x, double y, double rotationDegrees,
                              double scaleX, double scaleY, int layerIndex,
                              boolean flippedHorizontally, boolean visible,
                              String displayName, String activeStateId, int tintColorRgb,
                              boolean renderAboveMasks) {
        private static TokenState from(CanvasObject object, int layerIndex, int tintColorRgb,
                                       boolean renderAboveMasks) {
            return new TokenState(object.transform().position().x(), object.transform().position().y(),
                    object.transform().rotationDegrees(), object.transform().scale().x(),
                    object.transform().scale().y(), layerIndex,
                    object.flippedHorizontally(), object.visible(),
                    object.displayName(), object.activeStateId(), tintColorRgb, renderAboveMasks);
        }

        private static TokenState from(VttTokenTransformUpdatePayload update) {
            return new TokenState(update.x(), update.y(), update.rotationDegrees(),
                    update.scaleX(), update.scaleY(), update.layerIndex(),
                    update.flippedHorizontally(), update.visible(),
                    update.displayName(), update.activeStateId(), update.tintColorRgb(),
                    update.renderAboveMasks());
        }

        private VttTokenTransformUpdatePayload toUpdate(
                long authorityRevision, long entityRevision, long clientSequence,
                String sceneId, String objectId, String originPlayerId, boolean accepted) {
            return new VttTokenTransformUpdatePayload(
                    authorityRevision, entityRevision, clientSequence,
                    sceneId, objectId, x, y, rotationDegrees,
                    scaleX, scaleY, layerIndex, flippedHorizontally, visible,
                    displayName, activeStateId, tintColorRgb, renderAboveMasks,
                    originPlayerId, accepted);
        }
    }

    private static int tintColor(VTTSession session, String objectId) {
        if (session.getActiveScene() == null) return 0xFFFFFF;
        return session.getActiveScene().getObjects().stream()
                .filter(object -> object != null && objectId.equals(object.getId()))
                .findFirst().map(object -> object.getState().getTintColorRgb())
                .orElse(0xFFFFFF);
    }

    private static boolean renderAboveMasks(VTTSession session, String objectId) {
        if (session.getActiveScene() == null) return false;
        return session.getActiveScene().getObjects().stream()
                .filter(object -> object != null && objectId.equals(object.getId()))
                .findFirst().map(object -> object.getState().isRenderAboveMasks()).orElse(false);
    }

    private static boolean isSynchronizableObject(CanvasObject object) {
        return object != null && (object.hasSourceTokenDefinition()
                || object.hasSourceAttachmentDefinition());
    }

    private record Interpolation(TokenState start, TokenState target,
                                 String originPlayerId, boolean accepted,
                                 long authorityRevision, long entityRevision, long clientSequence,
                                 long startedAtMs, long durationMs, TokenState lastDisplayed) {
        private Interpolation withLastDisplayed(TokenState displayed) {
            return new Interpolation(start, target, originPlayerId, accepted,
                    authorityRevision, entityRevision, clientSequence,
                    startedAtMs, durationMs, displayed);
        }
    }
}
