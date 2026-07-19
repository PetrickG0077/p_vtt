package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.platform.render.VRenderContext;

/** Shared compact vertical scrollbar used by editor lists. */
public final class EditorScrollbar {
    private static final int TRACK_COLOR = 0x66333333;

    private EditorScrollbar() {}

    public static void render(VRenderContext context, int x, int y, int width, int height,
                              int itemCount, int visibleCount, int offset, int thumbColor) {
        context.graphics().fill(x, y, x + width, y + height, TRACK_COLOR);
        int maxOffset = maxOffset(itemCount, visibleCount);
        if (maxOffset <= 0) return;
        int thumbHeight = thumbHeight(height, itemCount, visibleCount);
        int thumbY = y + (int) Math.round((height - thumbHeight) *
                (clampOffset(offset, itemCount, visibleCount) / (double) maxOffset));
        context.graphics().fill(x, thumbY, x + width, thumbY + thumbHeight, thumbColor);
    }

    public static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public static int offsetForMouse(double mouseY, int y, int height,
                                     int itemCount, int visibleCount) {
        int maxOffset = maxOffset(itemCount, visibleCount);
        if (maxOffset <= 0) return 0;
        int thumbHeight = thumbHeight(height, itemCount, visibleCount);
        double travel = Math.max(1.0, height - thumbHeight);
        double progress = (mouseY - y - thumbHeight / 2.0) / travel;
        return Math.max(0, Math.min(maxOffset, (int) Math.round(progress * maxOffset)));
    }

    public static int clampOffset(int offset, int itemCount, int visibleCount) {
        return Math.max(0, Math.min(offset, maxOffset(itemCount, visibleCount)));
    }

    public static int maxOffset(int itemCount, int visibleCount) {
        return Math.max(0, itemCount - visibleCount);
    }

    private static int thumbHeight(int height, int itemCount, int visibleCount) {
        if (itemCount <= 0) return height;
        return Math.max(8, Math.min(height,
                (int) Math.round(height * (visibleCount / (double) itemCount))));
    }
}
