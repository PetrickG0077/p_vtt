package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.editor.hud.EditorHudTheme;
import com.petrick.vtt.feature.asset.library.AssetLibraryEntry;
import com.petrick.vtt.feature.asset.library.AssetLibraryFileType;
import com.petrick.vtt.feature.asset.library.AssetLibraryScanResult;
import com.petrick.vtt.feature.media.VttAudioPlayerService;
import com.petrick.vtt.core.session.VTTSession;
import com.petrick.vtt.network.client.VttClientMusicSync;
import com.petrick.vtt.network.client.VttClientShowState;
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
    private boolean draggingProgress;
    private boolean draggingVolume;
    private double draggedProgressRatio;

    public void render(VRenderContext context, Font font, AssetLibraryScanResult scan,
                       VttAudioPlayerService audio) {
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
            boolean active = tab == Tab.MUSICS
                    ? entry.absolutePath().equals(audio.track())
                    : entry.relativePath().equals(VttClientShowState.relativePath())
                    && VttClientShowState.isActive();
            int buttonX = bounds.right() - 78;
            context.graphics().fill(buttonX, rowY + 2, bounds.right() - 12,
                    rowY + 19, active ? EditorHudTheme.selection() : 0xE038383F);
            border(context, buttonX, rowY + 2, 66, 17, EditorHudTheme.outline());
            String action = tab == Tab.SHOWS ? active ? "Showing" : "Show"
                    : active && audio.isPlaying()
                    ? audio.isPaused() ? "Resume" : "Pause" : "Play";
            context.graphics().drawCenteredString(font, action,
                    buttonX + 33, rowY + 6, TEXT);
            rowY += 23;
        }
        context.graphics().fill(bounds.x + 8, bounds.bottom() - 47,
                bounds.right() - 8, bounds.bottom() - 8, HEADER);
        context.graphics().drawString(font,
                tab == Tab.MUSICS ? playerText(audio)
                        : VttClientShowState.isActive()
                        ? "Presentation active: " + VttClientShowState.relativePath()
                        : "Presentation: inactive",
                bounds.x + 17, bounds.bottom() - 31, TEXT, false);
        if (tab == Tab.MUSICS) {
            int trackX = bounds.x + 132;
            int trackY = bounds.bottom() - 17;
            int trackWidth = bounds.width - 270;
            button(context, font, bounds.x + 14, bounds.bottom() - 23, 48, 18,
                    "Stop", audio.isPlaying());
            button(context, font, bounds.x + 67, bounds.bottom() - 23, 58, 18,
                    audio.isPaused() ? "Resume" : "Pause", audio.isPlaying());
            context.graphics().fill(trackX, trackY, trackX + trackWidth, trackY + 4,
                    0xFF55555D);
            double progressRatio = draggingProgress ? draggedProgressRatio
                    : audio.durationSeconds() <= 0.0 ? 0.0
                    : audio.positionSeconds() / audio.durationSeconds();
            progressRatio = Math.max(0.0, Math.min(1.0, progressRatio));
            int progress = (int) Math.round(trackWidth * progressRatio);
            context.graphics().fill(trackX, trackY, trackX + Math.max(0, progress),
                    trackY + 4, EditorHudTheme.selection());
            int volumeX = bounds.right() - 112;
            int volumeWidth = 62;
            context.graphics().fill(volumeX, trackY, volumeX + volumeWidth, trackY + 4,
                    0xFF55555D);
            context.graphics().fill(volumeX, trackY,
                    volumeX + Math.round(volumeWidth * audio.masterVolume()), trackY + 4,
                    EditorHudTheme.selection());
            context.graphics().drawString(font, "Vol", volumeX - 24, trackY - 3,
                    MUTED, false);
            context.graphics().drawString(font, audio.isLoop() ? "Loop ON" : "Loop",
                    bounds.right() - 46, trackY - 4,
                    audio.isLoop() ? TEXT : MUTED, false);
            if (audio.status().startsWith("Opening")) {
                context.graphics().drawString(font, "Loading / synchronizing...",
                        bounds.x + 12, bounds.bottom() - 60, 0xFFFFCC55, false);
            }
            if (!audio.error().isBlank()) {
                context.graphics().drawString(font, trim(audio.error(), 70), bounds.x + 12,
                        bounds.bottom() - 60, 0xFFFF6666, false);
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY,
                                int screenWidth, int screenHeight,
                                AssetLibraryScanResult scan, VttAudioPlayerService audio,
                                VTTSession session) {
        Bounds bounds = bounds(screenWidth, screenHeight);
        if (!bounds.contains(mouseX, mouseY)) return false;
        if (inside(mouseX, mouseY, bounds.x + 150, bounds.y + 7, 92, 22)) {
            tab = Tab.MUSICS;
        } else if (inside(mouseX, mouseY, bounds.x + 246, bounds.y + 7, 92, 22)) {
            tab = Tab.SHOWS;
        } else if (tab == Tab.MUSICS) {
            List<AssetLibraryEntry> entries = entries(scan);
            int row = (int) Math.floor((mouseY - bounds.y - 45) / 23.0);
            if (row >= 0 && row < Math.min(entries.size(), 10)
                    && inside(mouseX, mouseY, bounds.right() - 78,
                    bounds.y + 47 + row * 23, 66, 17)) {
                AssetLibraryEntry entry = entries.get(row);
                if (entry.absolutePath().equals(audio.track()) && audio.isPlaying()) {
                    VttClientMusicSync.togglePause(session);
                } else {
                    VttClientMusicSync.play(session, entry.relativePath(), entry.absolutePath());
                }
            } else if (inside(mouseX, mouseY, bounds.x + 14,
                    bounds.bottom() - 23, 48, 18)) {
                VttClientMusicSync.stop(session);
            } else if (inside(mouseX, mouseY, bounds.x + 67,
                    bounds.bottom() - 23, 58, 18)) {
                VttClientMusicSync.togglePause(session);
            } else if (mouseY >= bounds.bottom() - 24) {
                int trackX = bounds.x + 132;
                int trackWidth = bounds.width - 270;
                int volumeX = bounds.right() - 112;
                if (mouseX >= trackX && mouseX <= trackX + trackWidth) {
                    draggingProgress = true;
                    draggedProgressRatio = (mouseX - trackX) / trackWidth;
                } else if (mouseX >= volumeX && mouseX <= volumeX + 62) {
                    draggingVolume = true;
                    VttClientMusicSync.previewMasterVolume(
                            (float) ((mouseX - volumeX) / 62.0));
                } else if (mouseX >= bounds.right() - 48) {
                    VttClientMusicSync.toggleLoop(session);
                }
            }
        } else {
            List<AssetLibraryEntry> entries = entries(scan);
            int row = (int) Math.floor((mouseY - bounds.y - 45) / 23.0);
            if (row >= 0 && row < Math.min(entries.size(), 10)
                    && inside(mouseX, mouseY, bounds.right() - 78,
                    bounds.y + 47 + row * 23, 66, 17)) {
                AssetLibraryEntry entry = entries.get(row);
                VttClientShowState.show(session, entry.relativePath());
            }
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int screenWidth,
                                int screenHeight, VTTSession session) {
        if (!draggingProgress && !draggingVolume) return false;
        Bounds bounds = bounds(screenWidth, screenHeight);
        if (draggingProgress) {
            int x = bounds.x + 132;
            int width = bounds.width - 270;
            draggedProgressRatio = Math.max(0.0,
                    Math.min(1.0, (mouseX - x) / width));
        } else {
            int x = bounds.right() - 112;
            VttClientMusicSync.previewMasterVolume(
                    (float) Math.max(0.0, Math.min(1.0, (mouseX - x) / 62.0)));
        }
        return true;
    }

    public boolean mouseReleased(VTTSession session) {
        boolean handled = draggingProgress || draggingVolume;
        if (draggingProgress) VttClientMusicSync.seek(session, draggedProgressRatio);
        if (draggingVolume) VttClientMusicSync.commitMasterVolume(session);
        draggingProgress = false;
        draggingVolume = false;
        return handled;
    }

    private String playerText(VttAudioPlayerService audio) {
        return audio.status() + "  " + time(audio.positionSeconds()) + "/"
                + time(audio.durationSeconds()) + "  Volume: "
                + Math.round(audio.masterVolume() * 100.0F) + "%";
    }

    private String time(double seconds) {
        int value = Math.max(0, (int) Math.round(seconds));
        return String.format("%d:%02d", value / 60, value % 60);
    }

    private String trim(String value, int length) {
        if (value == null || value.length() <= length) return value == null ? "" : value;
        return value.substring(0, Math.max(0, length - 3)) + "...";
    }

    private List<AssetLibraryEntry> entries(AssetLibraryScanResult scan) {
        if (scan == null) return List.of();
        return scan.entries().stream().filter(entry -> {
            String path = entry.relativePath().toLowerCase();
            if (tab == Tab.MUSICS) return path.startsWith("musics/")
                    && entry.fileType() == AssetLibraryFileType.AUDIO;
            return path.startsWith("shows/") && (entry.fileType() == AssetLibraryFileType.IMAGE
                    || entry.fileType() == AssetLibraryFileType.ANIMATED_IMAGE);
        }).sorted(Comparator.comparing(AssetLibraryEntry::relativePath)).toList();
    }

    private void tab(VRenderContext context, Font font, int x, int y, int width,
                     String label, boolean selected) {
        context.graphics().fill(x, y, x + width, y + 22,
                selected ? EditorHudTheme.selection() : 0xD8202026);
        border(context, x, y, width, 22, EditorHudTheme.outline());
        context.graphics().drawCenteredString(font, label, x + width / 2, y + 7, TEXT);
    }

    private void button(VRenderContext context, Font font, int x, int y, int width,
                        int height, String label, boolean enabled) {
        context.graphics().fill(x, y, x + width, y + height,
                enabled ? 0xE038383F : 0xA028282D);
        border(context, x, y, width, height,
                enabled ? EditorHudTheme.outline() : 0xFF66666A);
        context.graphics().drawCenteredString(font, label, x + width / 2,
                y + 5, enabled ? TEXT : MUTED);
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
