package com.petrick.vtt.platform.client;

import com.petrick.vtt.network.client.VttClientAssetCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Compact HUD feedback for server asset verification and downloads. */
public final class VttAssetSyncHudOverlay {
    private static final int WIDTH = 210;
    private static final int HEIGHT = 25;
    private static final int MARGIN = 6;

    private VttAssetSyncHudOverlay() {}

    public static void render(GuiGraphics graphics) {
        VttClientAssetCache.SyncProgress progress = VttClientAssetCache.progressSnapshot();
        if (graphics == null || progress == null || !progress.visible()) return;

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int x = MARGIN;
        int y = Math.max(MARGIN,
                minecraft.getWindow().getGuiScaledHeight() - HEIGHT - MARGIN);
        int accent = accentColor(progress.status());

        graphics.fill(x + 1, y + 1, x + WIDTH + 1, y + HEIGHT + 1, 0x80000000);
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xDC10141C);
        graphics.fill(x, y, x + WIDTH, y + 1, accent);

        String label = truncate(font, label(progress), WIDTH - 10);
        graphics.drawString(font, label, x + 5, y + 5, 0xFFF2F4F8, true);

        int barLeft = x + 5;
        int barTop = y + HEIGHT - 7;
        int barWidth = WIDTH - 10;
        graphics.fill(barLeft, barTop, barLeft + barWidth, barTop + 3, 0xFF252B35);
        int filledWidth = (int) Math.round(barWidth * progress.fraction());
        if (filledWidth > 0) {
            graphics.fill(barLeft, barTop, barLeft + filledWidth, barTop + 3, accent);
        }
    }

    private static String label(VttClientAssetCache.SyncProgress progress) {
        return switch (progress.status()) {
            case VERIFYING -> "VTT · Verificando cache...";
            case DOWNLOADING -> downloadLabel(progress);
            case COMPLETE -> "VTT · Assets sincronizados";
            case FAILED -> "VTT · " + fallback(progress.currentFile(), "Falha na sincronização");
            case IDLE -> "";
        };
    }

    private static String downloadLabel(VttClientAssetCache.SyncProgress progress) {
        if (progress.totalFiles() <= 0) return "VTT · Cache atualizado";
        String current = fileName(progress.currentFile());
        String count = progress.completedFiles() + "/" + progress.totalFiles();
        return current.isBlank()
                ? "VTT · Baixando " + count
                : "VTT · " + count + " · " + current;
    }

    private static int accentColor(VttClientAssetCache.SyncStatus status) {
        return switch (status) {
            case VERIFYING -> 0xFFFFC857;
            case DOWNLOADING -> 0xFF36C5F0;
            case COMPLETE -> 0xFF55D68B;
            case FAILED -> 0xFFFF5C6C;
            case IDLE -> 0x00000000;
        };
    }

    private static String fileName(String path) {
        return "";
    }

    private static String fallback(String value, String fallback) {
        return fallback;
    }

    private static String truncate(Font font, String text, int maximumWidth) {
        if (font.width(text) <= maximumWidth) return text;
        String suffix = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + suffix) > maximumWidth) end--;
        return text.substring(0, end) + suffix;
    }
}
