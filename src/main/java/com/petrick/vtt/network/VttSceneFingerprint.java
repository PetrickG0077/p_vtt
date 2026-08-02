package com.petrick.vtt.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneObject;

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
            VttScene canonical = canonicalCopy(scene);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    GSON.toJson(canonical).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * Materializes legacy/default-backed fields before hashing. Gson otherwise distinguishes
     * between a missing nullable field and the equivalent explicit default, even though the
     * editor and server treat both representations as the same scene state.
     */
    private static VttScene canonicalCopy(VttScene source) {
        VttScene scene = GSON.fromJson(GSON.toJson(source), VttScene.class);
        scene.getBackgroundTransform();
        scene.getInitialCameraView();
        scene.getVisionSourceObjectIds();
        scene.getMaps();
        scene.getWalls();
        scene.getDoors();
        scene.getFogOfWar().getRevealedAreas();
        scene.getFogOfWar().getHiddenAreas();
        scene.getGrid();
        scene.getLighting();
        scene.getLights();

        for (VttSceneObject object : scene.getObjects()) {
            if (object == null) continue;
            object.setTransform(object.getTransform());
            object.setSize(object.getSize());
            object.setState(object.getState());
            object.setVisionOuterRadius(object.getVisionOuterRadius());
            object.setVisionInnerRadius(object.getVisionInnerRadius());
            object.setVisionEnabled(object.isVisionEnabled());
            object.setVisionOwnLightEnabled(object.isVisionOwnLightEnabled());
            object.setOwnerId(object.getOwnerId());
            object.getState().setTintColorRgb(object.getState().getTintColorRgb());
        }
        return scene;
    }
}
