package com.petrick.vtt.network.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.petrick.vtt.VTT;
import com.petrick.vtt.core.session.VTTSession;
import com.petrick.vtt.feature.tabletop.VttDoor;
import com.petrick.vtt.feature.tabletop.VttFogOfWar;
import com.petrick.vtt.network.payload.VttEnvironmentStateRequestPayload;
import com.petrick.vtt.network.payload.VttEnvironmentStateUpdatePayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Type;
import java.util.List;

/** Synchronizes master-authored door and fog edits without replacing tokens or assets. */
public final class VttClientEnvironmentStateSync {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Type DOOR_LIST_TYPE = new TypeToken<List<VttDoor>>() {}.getType();
    private static long snapshotVersion = -1L;
    private static String lastDoorsJson;
    private static String lastFogJson;

    private VttClientEnvironmentStateSync() {}

    public static void tick(VTTSession session) {
        if (session == null || !session.hasNetworkSnapshot() || !session.isLocalMaster()) return;
        String doorsJson = GSON.toJson(session.getActiveScene().getDoors());
        String fogJson = GSON.toJson(session.getActiveScene().getFogOfWar());
        if (snapshotVersion != session.getNetworkSnapshotVersion()) {
            snapshotVersion = session.getNetworkSnapshotVersion();
            lastDoorsJson = doorsJson;
            lastFogJson = fogJson;
            return;
        }
        if (doorsJson.equals(lastDoorsJson) && fogJson.equals(lastFogJson)) return;
        lastDoorsJson = doorsJson;
        lastFogJson = fogJson;
        PacketDistributor.sendToServer(new VttEnvironmentStateRequestPayload(doorsJson, fogJson));
    }

    public static void acceptConfirmed(VTTSession session, VttEnvironmentStateUpdatePayload update) {
        if (session == null || update == null || !session.hasNetworkSnapshot()) return;
        try {
            List<VttDoor> doors = GSON.fromJson(update.doorsJson(), DOOR_LIST_TYPE);
            VttFogOfWar fog = GSON.fromJson(update.fogJson(), VttFogOfWar.class);
            session.getActiveScene().clearDoors();
            if (doors != null) doors.forEach(session.getActiveScene()::addDoor);
            session.getActiveScene().setFogOfWar(fog);
            lastDoorsJson = GSON.toJson(session.getActiveScene().getDoors());
            lastFogJson = GSON.toJson(session.getActiveScene().getFogOfWar());
        } catch (RuntimeException exception) {
            VTT.LOGGER.error("Failed to apply synchronized VTT door/fog state", exception);
        }
    }

    public static void reset() {
        snapshotVersion = -1L;
        lastDoorsJson = null;
        lastFogJson = null;
    }
}
