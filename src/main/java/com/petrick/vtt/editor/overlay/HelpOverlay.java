package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

/**
 * Painel simples de ajuda com os principais atalhos do editor.
 *
 * Este overlay é temporário/debug, mas já ajuda bastante enquanto
 * o editor ainda não tem botões reais de UI.
 */
public final class HelpOverlay {

    private static final int PANEL_WIDTH = 250;

    private static final int PADDING = 8;

    private static final int LINE_HEIGHT = 10;

    private static final int PANEL_X = 10;

    private static final int PANEL_Y = 10;

    private static final int PANEL_BACKGROUND = 0xAA000000;

    private static final int PANEL_BORDER = 0xFF66CCFF;

    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private static final int TEXT_COLOR = 0xFFDDDDDD;

    private static final int MUTED_TEXT_COLOR = 0xFFAAAAAA;

    public void render(VRenderContext context, Font font) {
        String[] lines = {
                "Help",
                "F1: Toggle help",
                "",
                "Tools:",
                "S: Select tool",
                "H: Hand tool",
                "Middle Mouse: Pan",
                "Mouse Wheel: Zoom",
                "",
                "Selection:",
                "Click: Select",
                "Ctrl + Click: Multi-select",
                "Shift + Drag: Box select",
                "Drag selected: Move",
                "",
                "Transform:",
                "Handles: Resize",
                "Shift + Resize: Proportional",
                "Rotation handle: Rotate",
                "Q / E: Rotate -/+",
                "+ / -: Scale",
                "R: Reset scale/rotation",
                "",
                "Objects:",
                "Delete: Remove",
                "Ctrl + D: Duplicate",
                "V: Toggle visibility",
                "N: Rename",
                "1 / 2 / 3: Change state",
                "",
                "Layers:",
                "PageUp/PageDown: Layer +/-",
                "Home/End: Front/Back",
                "",
                "Panels:",
                "F3: Debug",
                "F4: Inspector",
                "F5: Asset Catalog",
                "F6: Token Catalog",
                "F7: Outliner"
        };

        int width = PANEL_WIDTH;
        int height = PADDING * 2 + lines.length * LINE_HEIGHT;

        renderPanelBackground(context, PANEL_X, PANEL_Y, width, height);

        int x = PANEL_X + PADDING;
        int y = PANEL_Y + PADDING;

        for (String line : lines) {
            if (line.isBlank()) {
                y += LINE_HEIGHT;
                continue;
            }

            int color = line.endsWith(":")
                    ? MUTED_TEXT_COLOR
                    : TEXT_COLOR;

            if (line.equals("Help")) {
                color = TITLE_COLOR;
            }

            context.graphics().drawString(
                    font,
                    line,
                    x,
                    y,
                    color,
                    false
            );

            y += LINE_HEIGHT;
        }
    }

    private void renderPanelBackground(
            VRenderContext context,
            int x,
            int y,
            int width,
            int height
    ) {
        context.graphics().fill(
                x,
                y,
                x + width,
                y + height,
                PANEL_BACKGROUND
        );

        context.graphics().hLine(x, x + width, y, PANEL_BORDER);
        context.graphics().hLine(x, x + width, y + height, PANEL_BORDER);
        context.graphics().vLine(x, y, y + height, PANEL_BORDER);
        context.graphics().vLine(x + width, y, y + height, PANEL_BORDER);
    }
}