package com.petrick.vtt.network.client;

/** Transient editor notice state, independent from persistent tabletop data. */
public final class VttClientEditorNotice {
    private static final long DISPLAY_DURATION_MS = 4_000L;
    private static String message;
    private static long expiresAt;

    private VttClientEditorNotice() {}

    public static synchronized void show(String value) {
        if (value == null || value.isBlank()) return;
        message = value;
        expiresAt = System.currentTimeMillis() + DISPLAY_DURATION_MS;
    }

    public static synchronized String current() {
        if (message != null && System.currentTimeMillis() <= expiresAt) return message;
        reset();
        return null;
    }

    public static synchronized void reset() {
        message = null;
        expiresAt = 0L;
    }
}
