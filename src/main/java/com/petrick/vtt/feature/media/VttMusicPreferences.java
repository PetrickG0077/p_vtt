package com.petrick.vtt.feature.media;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.VTT;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Small client-local preference file for the music player. */
public final class VttMusicPreferences {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private VttMusicPreferences() {}

    public static float loadMasterVolume() {
        Path file = file();
        if (!Files.isRegularFile(file)) return 1.0F;
        try (Reader reader = Files.newBufferedReader(file)) {
            Data data = GSON.fromJson(reader, Data.class);
            return data == null ? 1.0F : clamp(data.masterVolume);
        } catch (Exception exception) {
            VTT.LOGGER.warn("Could not load VTT music preferences", exception);
            return 1.0F;
        }
    }

    public static void saveMasterVolume(float volume) {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(new Data(clamp(volume)), writer);
            }
        } catch (Exception exception) {
            VTT.LOGGER.warn("Could not save VTT music preferences", exception);
        }
    }

    private static Path file() {
        return FMLPaths.GAMEDIR.get().resolve(
                "config/vtt_assets/created/music_preferences.json");
    }

    private static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 1.0F;
    }

    private record Data(float masterVolume) {}
}
