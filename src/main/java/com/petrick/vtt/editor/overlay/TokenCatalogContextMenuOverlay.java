package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.editor.catalog.TokenCatalogContextMenu;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

/**
 * Desenha o popup de botão direito do Token Catalog.
 */
public final class TokenCatalogContextMenuOverlay {

    private static final int WIDTH = 120;

    private static final int OPTION_HEIGHT = 18;

    private static final int PADDING = 4;

    private static final int BACKGROUND_COLOR = 0xEE101010;

    private static final int BORDER_COLOR = 0xFF555555;

    private static final int HOVER_COLOR = 0xFF3A3A22;

    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private static final int DANGER_TEXT_COLOR = 0xFFFF5555;

    public enum Action {
        NONE,
        EDIT,
        VIEW_IN_EXPLORER,
        DUPLICATE,
        DELETE
    }

    public void render(
            VRenderContext context,
            Font font,
            TokenCatalogContextMenu menu
    ) {
        if (menu == null || !menu.isOpen()) {
            return;
        }

        int x = menu.getX();
        int y = menu.getY();

        int height = getHeight();

        context.graphics().fill(
                x,
                y,
                x + WIDTH,
                y + height,
                BACKGROUND_COLOR
        );

        context.graphics().hLine(x, x + WIDTH, y, BORDER_COLOR);
        context.graphics().hLine(x, x + WIDTH, y + height, BORDER_COLOR);
        context.graphics().vLine(x, y, y + height, BORDER_COLOR);
        context.graphics().vLine(x + WIDTH, y, y + height, BORDER_COLOR);

        renderOption(context, font, "Edit", x, y, 0, false);
        renderOption(context, font, "View in explorer", x, y, 1, false);
        renderOption(context, font, "Duplicate", x, y, 2, false);
        renderOption(context, font, "Delete", x, y, 3, true);
    }

    private void renderOption(
            VRenderContext context,
            Font font,
            String label,
            int menuX,
            int menuY,
            int index,
            boolean danger
    ) {
        int optionX = menuX + PADDING;
        int optionY = menuY + PADDING + index * OPTION_HEIGHT;

        if (isMouseOverOption(context, menuX, menuY, index)) {
            context.graphics().fill(
                    menuX + 1,
                    optionY - 1,
                    menuX + WIDTH - 1,
                    optionY + OPTION_HEIGHT - 1,
                    HOVER_COLOR
            );
        }

        context.graphics().drawString(
                font,
                label,
                optionX,
                optionY + 4,
                danger ? DANGER_TEXT_COLOR : TEXT_COLOR,
                false
        );
    }

    public Action getActionAt(
            TokenCatalogContextMenu menu,
            double mouseX,
            double mouseY
    ) {
        if (menu == null || !menu.isOpen()) {
            return Action.NONE;
        }

        if (!containsPoint(menu, mouseX, mouseY)) {
            return Action.NONE;
        }

        int relativeY = (int) mouseY - menu.getY() - PADDING;

        if (relativeY < 0) {
            return Action.NONE;
        }

        int index = relativeY / OPTION_HEIGHT;

        return switch (index) {
            case 0 -> Action.EDIT;
            case 1 -> Action.VIEW_IN_EXPLORER;
            case 2 -> Action.DUPLICATE;
            case 3 -> Action.DELETE;
            default -> Action.NONE;
        };
    }

    public boolean containsPoint(
            TokenCatalogContextMenu menu,
            double mouseX,
            double mouseY
    ) {
        if (menu == null || !menu.isOpen()) {
            return false;
        }

        int x = menu.getX();
        int y = menu.getY();

        return mouseX >= x
                && mouseX <= x + WIDTH
                && mouseY >= y
                && mouseY <= y + getHeight();
    }

    private boolean isMouseOverOption(
            VRenderContext context,
            int menuX,
            int menuY,
            int index
    ) {
        int optionY = menuY + PADDING + index * OPTION_HEIGHT;

        return context.mouseX() >= menuX
                && context.mouseX() <= menuX + WIDTH
                && context.mouseY() >= optionY
                && context.mouseY() <= optionY + OPTION_HEIGHT;
    }

    private int getHeight() {
        return PADDING * 2 + OPTION_HEIGHT * 4;
    }
}