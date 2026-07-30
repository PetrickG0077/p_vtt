package com.petrick.vtt.editor.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.VTT;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Global, persistent colors shared by the editor HUD.
 *
 * The JSON format intentionally uses readable ARGB hexadecimal strings so it
 * can be edited manually until the visual color controls are implemented.
 */
public final class EditorHudTheme {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_OUTLINE = 0xFFE8E8E8;
    private static final int DEFAULT_FOLDER_BACKGROUND = 0xAA4B236E;
    private static final int DEFAULT_SELECTION = 0xE0245266;

    private static int outline = DEFAULT_OUTLINE;
    private static int folderBackground = DEFAULT_FOLDER_BACKGROUND;
    private static int selection = DEFAULT_SELECTION;
    private static Path file;

    private EditorHudTheme() {
    }

    public static synchronized void initialize(Path gameDirectory) {
        if (gameDirectory == null) return;
        Path target = gameDirectory.toAbsolutePath().normalize()
                .resolve("config/vtt_assets/created/editor_hud_theme.json");
        if (target.equals(file)) return;
        file = target;
        load();
    }

    public static int outline() {
        return outline;
    }

    public static int folderBackground() {
        return folderBackground;
    }

    public static int selection() {
        return selection;
    }

    public static int opaqueSelection() {
        return 0xFF000000 | (selection & 0x00FFFFFF);
    }

    public static int selectionWithAlpha(int alpha) {
        return withAlpha(selection, alpha);
    }

    public static int folderBackgroundWithAlpha(int alpha) {
        return withAlpha(folderBackground, alpha);
    }

    public static int folderHover() {
        return brighten(folderBackground, 0.16);
    }

    public static synchronized void setOutline(int argb) {
        outline = argb;
        save();
    }

    public static synchronized void setFolderBackground(int argb) {
        folderBackground = argb;
        save();
    }

    public static synchronized void setSelection(int argb) {
        selection = argb;
        save();
    }

    public static synchronized void configure(
            int outlineColor,
            int folderBackgroundColor,
            int selectionColor
    ) {
        outline = outlineColor;
        folderBackground = folderBackgroundColor;
        selection = selectionColor;
        save();
    }

    private static void load() {
        outline = DEFAULT_OUTLINE;
        folderBackground = DEFAULT_FOLDER_BACKGROUND;
        selection = DEFAULT_SELECTION;
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    ThemeData data = GSON.fromJson(reader, ThemeData.class);
                    if (data != null) {
                        outline = parseColor(data.outline, DEFAULT_OUTLINE);
                        folderBackground = parseColor(
                                data.folderBackground, DEFAULT_FOLDER_BACKGROUND);
                        selection = parseColor(data.selection, DEFAULT_SELECTION);
                    }
                }
            }
            save();
        } catch (IOException | RuntimeException exception) {
            VTT.LOGGER.warn("Could not load editor HUD theme from {}", file, exception);
        }
    }

    private static void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(new ThemeData(
                        formatColor(outline),
                        formatColor(folderBackground),
                        formatColor(selection)), writer);
            }
        } catch (IOException exception) {
            VTT.LOGGER.warn("Could not save editor HUD theme to {}", file, exception);
        }
    }

    private static int parseColor(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim()
                .replace("#", "")
                .replace("0x", "")
                .replace("0X", "");
        if (normalized.length() == 6) normalized = "FF" + normalized;
        if (normalized.length() != 8) return fallback;
        try {
            return (int) Long.parseUnsignedLong(normalized, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String formatColor(int color) {
        return String.format("#%08X", color);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24)
                | (color & 0x00FFFFFF);
    }

    private static int brighten(int color, double amount) {
        int alpha = color >>> 24;
        int red = brightenChannel(color >>> 16 & 0xFF, amount);
        int green = brightenChannel(color >>> 8 & 0xFF, amount);
        int blue = brightenChannel(color & 0xFF, amount);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int brightenChannel(int value, double amount) {
        return Math.min(255, (int) Math.round(value + (255 - value) * amount));
    }

    private record ThemeData(
            String outline,
            String folderBackground,
            String selection
    ) {
    }
}
