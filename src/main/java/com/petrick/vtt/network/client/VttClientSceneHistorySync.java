package com.petrick.vtt.network.client;

import com.petrick.vtt.core.session.VTTSession;

/** Suppresses granular prediction packets while an atomic history request is pending. */
public final class VttClientSceneHistorySync {
    private static boolean pending;
    private static boolean releaseAfterSnapshot;
    private static long startingSnapshotVersion = -1L;

    private VttClientSceneHistorySync() {}

    public static boolean isPending() {
        return pending;
    }

    public static void begin(long snapshotVersion) {
        pending = true;
        releaseAfterSnapshot = false;
        startingSnapshotVersion = snapshotVersion;
    }

    public static void releaseAfterNextSnapshot() {
        if (pending) releaseAfterSnapshot = true;
    }

    public static void tick(VTTSession session) {
        if (!pending || !releaseAfterSnapshot || session == null) return;
        if (session.getNetworkSnapshotVersion() > startingSnapshotVersion) finish();
    }

    public static void finish() {
        pending = false;
        releaseAfterSnapshot = false;
        startingSnapshotVersion = -1L;
    }

    public static void reset() {
        finish();
    }
}
