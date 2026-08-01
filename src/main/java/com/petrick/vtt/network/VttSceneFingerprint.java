package com.petrick.vtt.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.feature.tabletop.VttScene;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable content fingerprint used to reject stale whole-scene editor operations. */
public final class VttSceneFingerprint {
    private static final Gson GSON = new GsonBuilder().create();

    private VttSceneFingerprint() {}

    public static String of(VttScene scene) {
        if (scene == null) return "";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    GSON.toJson(scene).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
