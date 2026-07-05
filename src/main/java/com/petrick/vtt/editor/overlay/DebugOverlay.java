package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.camera.Camera2D;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

/**
 * Overlay temporário de debug do VTT.
 *
 * Esta classe existe apenas para facilitar o desenvolvimento.
 * Futuramente poderemos ativar/desativar isso por tecla ou configuração.
 */
public final class DebugOverlay {

    public void render(VRenderContext context, Font font, Camera2D camera) {
        GuiGraphics graphics = context.graphics();

        graphics.drawString(
                font,
                "Mouse Screen: " + formatVec(context.mouseScreenPosition()),
                10,
                10,
                0xFFFFFFFF
        );

        graphics.drawString(
                font,
                "Mouse World: " + formatVec(context.mouseWorldPosition()),
                10,
                22,
                0xFFFFFFFF
        );

        graphics.drawString(
                font,
                "Camera: " + formatVec(camera.getPosition()),
                10,
                34,
                0xFFFFFFFF
        );

        graphics.drawString(
                font,
                "Zoom: " + String.format(Locale.ROOT, "%.2f", camera.getZoom()),
                10,
                46,
                0xFFFFFFFF
        );

        graphics.drawString(
                font,
                "Press ESC to close",
                10,
                58,
                0xFFAAAAAA
        );
    }

    private static String formatVec(Vec2d vec) {
        return String.format(Locale.ROOT, "(%.2f, %.2f)", vec.x(), vec.y());
    }
}