package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.editor.catalog.SceneContextMenu;
import com.petrick.vtt.editor.hud.EditorHudTheme;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

/** Context actions for a scene row. */
public final class SceneContextMenuOverlay {
    private static final int WIDTH = 112;
    private static final int OPTION_HEIGHT = 18;
    private static final int PADDING = 4;

    public enum Action { NONE, DUPLICATE, RENAME, DELETE }

    public void render(VRenderContext context, Font font, SceneContextMenu menu) {
        if (menu == null || !menu.isOpen()) return;
        int x = menu.getX();
        int y = menu.getY();
        int height = PADDING * 2 + OPTION_HEIGHT * 3;
        context.graphics().fill(x, y, x + WIDTH, y + height, 0xEE101010);
        context.graphics().hLine(x, x + WIDTH, y, EditorHudTheme.outline());
        context.graphics().hLine(
                x, x + WIDTH, y + height, EditorHudTheme.outline());
        context.graphics().vLine(x, y, y + height, EditorHudTheme.outline());
        context.graphics().vLine(
                x + WIDTH, y, y + height, EditorHudTheme.outline());
        renderOption(context, font, "Duplicate", x, y, 0, 0xFFFFFFFF);
        renderOption(context, font, "Rename", x, y, 1, 0xFFFFFFFF);
        renderOption(context, font, "Delete", x, y, 2, 0xFFFF5555);
    }

    private void renderOption(VRenderContext context, Font font, String label,
                              int x, int y, int index, int color) {
        int top = y + PADDING + index * OPTION_HEIGHT;
        if (context.mouseX() >= x && context.mouseX() <= x + WIDTH
                && context.mouseY() >= top && context.mouseY() <= top + OPTION_HEIGHT) {
            context.graphics().fill(x + 1, top, x + WIDTH - 1,
                    top + OPTION_HEIGHT, 0xFF3A3A22);
        }
        context.graphics().drawString(font, label, x + PADDING, top + 5, color, false);
    }

    public Action getActionAt(SceneContextMenu menu, double mouseX, double mouseY) {
        if (!containsPoint(menu, mouseX, mouseY)) return Action.NONE;
        int relativeY = (int) mouseY - menu.getY() - PADDING;
        if (relativeY < 0) return Action.NONE;
        return switch (relativeY / OPTION_HEIGHT) {
            case 0 -> Action.DUPLICATE;
            case 1 -> Action.RENAME;
            case 2 -> Action.DELETE;
            default -> Action.NONE;
        };
    }

    public boolean containsPoint(SceneContextMenu menu, double mouseX, double mouseY) {
        if (menu == null || !menu.isOpen()) return false;
        int height = PADDING * 2 + OPTION_HEIGHT * 3;
        return mouseX >= menu.getX() && mouseX <= menu.getX() + WIDTH
                && mouseY >= menu.getY() && mouseY <= menu.getY() + height;
    }
}
