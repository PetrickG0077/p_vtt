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

    public void render(
            VRenderContext context,
            Font font,
            Camera2D camera,
            String activeToolId,
            int assetCount,
            int tokenDefinitionCount,
            int libraryFileCount,
            long animatedImageCount
    ) {
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

        graphics.drawString(
                font,
                "Tool: " + activeToolId,
                10,
                70,
                0xFFAAAAAA
        );

        context.graphics().drawString(
                font,
                "Assets: " + assetCount,
                15,
                80,
                0xFFAAAAAA,
                false
        );

        context.graphics().drawString(
                font,
                "Token Definitions: " + tokenDefinitionCount,
                15,
                90,
                0xFFAAAAAA,
                false
        );

        context.graphics().drawString(
                font,
                "Library Files: " + libraryFileCount,
                15,
                100,
                0xFFAAAAAA,
                false
        );

        context.graphics().drawString(
                font,
                "Animated Images: " + animatedImageCount,
                15,
                110,
                0xFFAAAAAA,
                false
        );
    }

    private static String formatVec(Vec2d vec) {
        return String.format(Locale.ROOT, "(%.2f, %.2f)", vec.x(), vec.y());
    }
}