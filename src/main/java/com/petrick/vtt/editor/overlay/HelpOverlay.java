package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

/**
 * Painel simples de ajuda com os principais atalhos do editor.
 */
public final class HelpOverlay {

    private static final int PANEL_WIDTH = 205;

    private static final int PADDING = 8;

    private static final int LINE_HEIGHT = 8;

    private static final int PANEL_X = 10;

    private static final int PANEL_Y = 10;

    private static final float TEXT_SCALE = 0.75F;

    private static final int PANEL_BACKGROUND = 0xAA000000;

    private static final int PANEL_BORDER = 0xFF66CCFF;

    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private static final int TEXT_COLOR = 0xFFDDDDDD;

    private static final int MUTED_TEXT_COLOR = 0xFFAAAAAA;

    public void render(VRenderContext context, Font font) {
        String[] lines = {
                "Help",
                "F1: Toggle help",
                "F2: Scenes",
                "Right-click scene: Rename/Delete",
                "Ctrl+M: Master/Player",
                "",
                "Tools:",
                "S: Select",
                "H: Hand",
                "M: Measure (Esc/RMB cancel)",
                "Measure: snap grid/walls/doors",
                "W: Wall box (drag)",
                "D: Door box (drag)",
                "Select: click a door to edit it",
                "O/L: Open / Lock door",
                "Ctrl+O: Toggle local token owner",
                "F: Flip token / Fog if none selected",
                "X/Y: Reveal fog / toggle",
                "Ctrl+P: Player view preview",
                "Ctrl+V: Add / remove vision source",
                "[: Outer vision +64   ]: Outer vision -64",
                ",: Inner vision +16   .: Inner vision -16",
                " /: Reset vision radii",
                ";: Toggle selected token vision",
                "Middle Mouse: Pan",
                "Alt + drag token: Ignore collision (Master)",
                "Wheel: Zoom",
                "",
                "Selection:",
                "Click: Select",
                "Ctrl+Click: Multi",
                "Shift+Drag: Box",
                "Drag selected: Move",
                "",
                "Transform:",
                "Handles: Resize",
                "Shift+Resize: Prop.",
                "Rotation handle: Rotate",
                "C: Edit selected token collision box",
                "Q/E: Rotate",
                "+/-: Scale",
                "R: Reset",
                "Ctrl+Z: Undo token action",
                "Ctrl+Y: Redo token action",
                "",
                "Objects:",
                "Delete: Remove",
                "Ctrl+D: Duplicate",
                "V: Visibility",
                "N: Rename",
                "1/2/3: State",
                "",
                "Layers:",
                "PageUp/Down: Layer",
                "Home/End: Front/Back",
                "",
                "Panels:",
                "F3: Debug",
                "F4: Inspector",
                "F5: Assets",
                "Ctrl+F5: Asset filter",
                "B: Choose background",
                "Shift+B: Remove background",
                "F6: Tokens",
                "F7: Outliner",
                "F9: Hide panels",
                "F10: Show panels",
                "F12: Rescan assets"
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

            drawScaledString(
                    context,
                    font,
                    line,
                    x,
                    y,
                    color
            );

            y += LINE_HEIGHT;
        }
    }

    private void drawScaledString(
            VRenderContext context,
            Font font,
            String text,
            int x,
            int y,
            int color
    ) {
        context.graphics().pose().pushPose();

        context.graphics().pose().scale(
                TEXT_SCALE,
                TEXT_SCALE,
                1.0F
        );

        context.graphics().drawString(
                font,
                text,
                Math.round(x / TEXT_SCALE),
                Math.round(y / TEXT_SCALE),
                color,
                false
        );

        context.graphics().pose().popPose();
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
