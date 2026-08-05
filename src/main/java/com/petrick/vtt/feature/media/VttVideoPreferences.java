package com.petrick.vtt.feature.media;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.VTT;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-local volume preferences for fullscreen video presentations. */
public final class VttVideoPreferences {
    public static final int DEFAULT_CACHE_MEMORY_MB = 384;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private VttVideoPreferences() {}

    public static State load() {
        Path file = file();
        if (!Files.isRegularFile(file)) return new State(1.0F, false,
                DEFAULT_CACHE_MEMORY_MB);
        try (Reader reader = Files.newBufferedReader(file)) {
            Data data = GSON.fromJson(reader, Data.class);
            return data == null ? new State(1.0F, false, DEFAULT_CACHE_MEMORY_MB)
                    : new State(clamp(data.volume), data.muted,
                    clampCacheMemory(data.cacheMemoryMb));
        } catch (Exception exception) {
            VTT.LOGGER.warn("Could not load VTT video preferences", exception);
            return new State(1.0F, false, DEFAULT_CACHE_MEMORY_MB);
        }
    }

    public static void save(float volume, boolean muted, int cacheMemoryMb) {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(new Data(clamp(volume), muted,
                        clampCacheMemory(cacheMemoryMb)), writer);
            }
        } catch (Exception exception) {
            VTT.LOGGER.warn("Could not save VTT video preferences", exception);
        }
    }

    private static Path file() {
        return FMLPaths.GAMEDIR.get().resolve(
                "config/vtt_assets/created/video_preferences.json");
    }

    private static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 1.0F;
    }

    public static int clampCacheMemory(int value) {
        return value <= 0 ? DEFAULT_CACHE_MEMORY_MB : Math.max(64, Math.min(1024, value));
    }

    public record State(float volume, boolean muted, int cacheMemoryMb) {}
    private record Data(float volume, boolean muted, int cacheMemoryMb) {}
}
