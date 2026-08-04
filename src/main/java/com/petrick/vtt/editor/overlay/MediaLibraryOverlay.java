package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.editor.hud.EditorHudTheme;
import com.petrick.vtt.feature.asset.library.AssetLibraryEntry;
import com.petrick.vtt.feature.asset.library.AssetLibraryFileType;
import com.petrick.vtt.feature.asset.library.AssetLibraryScanResult;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

import java.util.Comparator;
import java.util.List;

/** First media-control surface. Playback controls are added by the following steps. */
public final class MediaLibraryOverlay {
    private static final int WIDTH = 520;
    private static final int HEIGHT = 310;
    private static final int PANEL = 0xF018181E;
    private static final int HEADER = 0xF044444A;
    private static final int ROW = 0xC824242A;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int MUTED = 0xFF99999F;
    private Tab tab = Tab.MUSICS;

    public void render(VRenderContext context, Font font, AssetLibraryScanResult scan) {
        Bounds bounds = bounds(context.screenWidth(), context.screenHeight());
        context.graphics().fill(bounds.x, bounds.y, bounds.right(), bounds.bottom(), PANEL);
        context.graphics().fill(bounds.x, bounds.y, bounds.right(), bounds.y + 36, HEADER);
        border(context, bounds.x, bounds.y, bounds.width, bounds.height,
                EditorHudTheme.outline());
        context.graphics().drawString(font, "Show / Musics", bounds.x + 10,
                bounds.y + 13, TEXT, false);
        tab(context, font, bounds.x + 150, bounds.y + 7, 92, "Musics",
                tab == Tab.MUSICS);
        tab(context, font, bounds.x + 246, bounds.y + 7, 92, "Shows",
                tab == Tab.SHOWS);

        List<AssetLibraryEntry> entries = entries(scan);
        int rowY = bounds.y + 45;
        if (entries.isEmpty()) {
            context.graphics().drawString(font,
                    tab == Tab.MUSICS ? "Put audio files in assets/musics"
                            : "Put images or videos in assets/shows",
                    bounds.x + 12, rowY + 4, MUTED, false);
        }
        for (int index = 0; index < Math.min(entries.size(), 10); index++) {
            AssetLibraryEntry entry = entries.get(index);
            context.graphics().fill(bounds.x + 8, rowY, bounds.right() - 8,
                    rowY + 21, ROW);
            context.graphics().drawString(font, entry.relativePath(), bounds.x + 15,
                    rowY + 7, TEXT, false);
            context.graphics().drawString(font, "[ ready ]", bounds.right() - 64,
                    rowY + 7, MUTED, false);
            rowY += 23;
        }
        context.graphics().fill(bounds.x + 8, bounds.bottom() - 47,
                bounds.right() - 8, bounds.bottom() - 8, HEADER);
        context.graphics().drawString(font,
                tab == Tab.MUSICS ? "Player: stopped  |  Master volume: 100%"
                        : "Presentation: inactive",
                bounds.x + 17, bounds.bottom() - 31, TEXT, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY,
                                int screenWidth, int screenHeight) {
        Bounds bounds = bounds(screenWidth, screenHeight);
        if (!bounds.contains(mouseX, mouseY)) return false;
        if (inside(mouseX, mouseY, bounds.x + 150, bounds.y + 7, 92, 22)) {
            tab = Tab.MUSICS;
        } else if (inside(mouseX, mouseY, bounds.x + 246, bounds.y + 7, 92, 22)) {
            tab = Tab.SHOWS;
        }
        return true;
    }

    private List<AssetLibraryEntry> entries(AssetLibraryScanResult scan) {
        if (scan == null) return List.of();
        return scan.entries().stream().filter(entry -> {
            String path = entry.relativePath().toLowerCase();
            if (tab == Tab.MUSICS) return path.startsWith("musics/")
                    && entry.fileType() == AssetLibraryFileType.AUDIO;
            return path.startsWith("shows/") && (entry.fileType() == AssetLibraryFileType.IMAGE
                    || entry.fileType() == AssetLibraryFileType.ANIMATED_IMAGE
                    || entry.fileType() == AssetLibraryFileType.VIDEO);
        }).sorted(Comparator.comparing(AssetLibraryEntry::relativePath)).toList();
    }

    private void tab(VRenderContext context, Font font, int x, int y, int width,
                     String label, boolean selected) {
        context.graphics().fill(x, y, x + width, y + 22,
                selected ? EditorHudTheme.selection() : 0xD8202026);
        border(context, x, y, width, 22, EditorHudTheme.outline());
        context.graphics().drawCenteredString(font, label, x + width / 2, y + 7, TEXT);
    }

    private Bounds bounds(int screenWidth, int screenHeight) {
        int width = Math.min(WIDTH, Math.max(300, screenWidth - 80));
        int height = Math.min(HEIGHT, Math.max(210, screenHeight - 100));
        return new Bounds((screenWidth - width) / 2, (screenHeight - height) / 2,
                width, height);
    }

    private void border(VRenderContext context, int x, int y, int width, int height, int color) {
        context.graphics().hLine(x, x + width, y, color);
        context.graphics().hLine(x, x + width, y + height, color);
        context.graphics().vLine(x, y, y + height, color);
        context.graphics().vLine(x + width, y, y + height, color);
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private enum Tab { MUSICS, SHOWS }
    private record Bounds(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        boolean contains(double mx, double my) {
            return mx >= x && mx <= right() && my >= y && my <= bottom();
        }
    }
}
