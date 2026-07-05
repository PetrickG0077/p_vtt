package com.petrick.vtt.editor.tool;

import com.petrick.vtt.core.math.Vec2d;

/**
 * Ferramenta de mão.
 *
 * Responsável por mover a câmera pelo canvas.
 */
public final class HandTool implements Tool {

    public static final String ID = "hand";

    private boolean panning;

    private Vec2d lastMousePosition;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean mouseClicked(ToolContext context, double mouseX, double mouseY, int button) {
        if (button == 2) {
            this.panning = true;
            this.lastMousePosition = new Vec2d(mouseX, mouseY);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(ToolContext context, double mouseX, double mouseY, int button) {
        if (button == 2) {
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
            double dragY
    ) {
        if (panning && lastMousePosition != null) {
            Vec2d currentMousePosition = new Vec2d(mouseX, mouseY);
            Vec2d delta = currentMousePosition.subtract(lastMousePosition);

            context.camera().moveByScreenDelta(delta);

            lastMousePosition = currentMousePosition;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(
            ToolContext context,
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        double zoomFactor = scrollY > 0 ? 1.1 : 0.9;

        context.camera().zoomAtScreenPoint(
                zoomFactor,
                new Vec2d(mouseX, mouseY),
                context.renderState().getViewportBounds()
        );

        return true;
    }
}