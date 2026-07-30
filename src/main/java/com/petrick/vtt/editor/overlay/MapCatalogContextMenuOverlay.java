package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.editor.catalog.MapCatalogContextMenu;
import com.petrick.vtt.editor.hud.EditorHudTheme;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

/** Right-click actions for reusable map definitions. */
public final class MapCatalogContextMenuOverlay {
    private static final int WIDTH = 110;
    private static final int OPTION_HEIGHT = 18;
    private static final int PADDING = 4;

    public enum Action { NONE, EDIT, DUPLICATE, DELETE }

    public void render(VRenderContext context, Font font, MapCatalogContextMenu menu) {
        if (menu == null || !menu.isOpen()) return;
        int x = menu.getX();
        int y = menu.getY();
        int height = PADDING * 2 + OPTION_HEIGHT * 3;
        context.graphics().fill(x, y, x + WIDTH, y + height, 0xEE101010);
        border(context, x, y, WIDTH, height, EditorHudTheme.outline());
        option(context, font, x, y, 0, "Edit", false);
        option(context, font, x, y, 1, "Duplicate", false);
        option(context, font, x, y, 2, "Delete", true);
    }

    public Action getActionAt(
            MapCatalogContextMenu menu, double mouseX, double mouseY
    ) {
        if (!containsPoint(menu, mouseX, mouseY)) return Action.NONE;
        int relativeY = (int) mouseY - menu.getY() - PADDING;
        if (relativeY < 0) return Action.NONE;
        int index = relativeY / OPTION_HEIGHT;
        return switch (index) {
            case 0 -> Action.EDIT;
            case 1 -> Action.DUPLICATE;
            case 2 -> Action.DELETE;
            default -> Action.NONE;
        };
    }

    public boolean containsPoint(
            MapCatalogContextMenu menu, double mouseX, double mouseY
    ) {
        if (menu == null || !menu.isOpen()) return false;
        return mouseX >= menu.getX() && mouseX <= menu.getX() + WIDTH
                && mouseY >= menu.getY()
                && mouseY <= menu.getY() + PADDING * 2 + OPTION_HEIGHT * 3;
    }

    private void option(
            VRenderContext context, Font font, int x, int y,
            int index, String label, boolean danger
    ) {
        int optionY = y + PADDING + index * OPTION_HEIGHT;
        if (context.mouseX() >= x && context.mouseX() <= x + WIDTH
                && context.mouseY() >= optionY
                && context.mouseY() <= optionY + OPTION_HEIGHT) {
            context.graphics().fill(
                    x + 1, optionY, x + WIDTH - 1, optionY + OPTION_HEIGHT, 0xFF3A3A22);
        }
        context.graphics().drawString(
                font, label, x + PADDING, optionY + 5,
                danger ? 0xFFFF5555 : 0xFFFFFFFF, false);
    }

    private void border(
            VRenderContext context, int x, int y, int width, int height, int color
    ) {
        context.graphics().hLine(x, x + width, y, color);
        context.graphics().hLine(x, x + width, y + height, color);
        context.graphics().vLine(x, y, y + height, color);
        context.graphics().vLine(x + width, y, y + height, color);
    }
}
