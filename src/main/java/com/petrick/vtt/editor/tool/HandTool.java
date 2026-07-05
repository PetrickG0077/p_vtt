package com.petrick.vtt.editor.tool;

import com.petrick.vtt.core.math.Vec2d;

/**
 * Ferramenta de mão.
 *
 * Quando ativa, permite mover a câmera com o botão esquerdo.
 */
public final class HandTool implements Tool {

    public static final String ID = "hand";

    private static final int LEFT_MOUSE_BUTTON = 0;

    private boolean panning;

    private Vec2d lastMousePosition;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean mouseClicked(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            int modifiers
    ) {
        if (button == LEFT_MOUSE_BUTTON) {
            this.panning = true;
            this.lastMousePosition = new Vec2d(mouseX, mouseY);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            int modifiers
    ) {
        if (button == LEFT_MOUSE_BUTTON) {
            this.panning = false;
            this.lastMousePosition = null;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY,
            int modifiers
    ) {
        if (button == LEFT_MOUSE_BUTTON && panning && lastMousePosition != null) {
            Vec2d currentMousePosition = new Vec2d(mouseX, mouseY);
            Vec2d delta = currentMousePosition.subtract(lastMousePosition);

            context.camera().moveByScreenDelta(delta);

            lastMousePosition = currentMousePosition;
            return true;
        }

        return false;
    }
}