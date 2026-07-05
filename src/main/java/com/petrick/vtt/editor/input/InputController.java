package com.petrick.vtt.editor.input;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.render.RenderState;
import com.petrick.vtt.feature.camera.Camera2D;

/**
 * Controla os inputs principais do VTT.
 *
 * A Screen apenas captura eventos brutos do Minecraft
 * e repassa para este controller.
 */
public final class InputController {

    private final Camera2D camera;

    private boolean panning;

    private Vec2d lastMousePosition;

    public InputController(Camera2D camera) {
        this.camera = camera;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 2) {
            this.panning = true;
            this.lastMousePosition = new Vec2d(mouseX, mouseY);
            return true;
        }

        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 2) {
            this.panning = false;
            this.lastMousePosition = null;
            return true;
        }

        return false;
    }

    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (panning && lastMousePosition != null) {
            Vec2d currentMousePosition = new Vec2d(mouseX, mouseY);
            Vec2d delta = currentMousePosition.subtract(lastMousePosition);

            camera.moveByScreenDelta(delta);

            lastMousePosition = currentMousePosition;
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY,
            RenderState renderState
    ) {
        double zoomFactor = scrollY > 0 ? 1.1 : 0.9;

        camera.zoomAtScreenPoint(
                zoomFactor,
                new Vec2d(mouseX, mouseY),
                renderState.getViewportBounds()
        );

        return true;
    }
}