package com.petrick.vtt.feature.canvas.visual;

import com.petrick.vtt.platform.render.VRenderContext;

/**
 * Renderizador dos diferentes tipos de visual do canvas.
 *
 * O CanvasRenderer cuida da transformação do objeto:
 * - posição
 * - escala
 * - rotação
 *
 * Esta classe cuida apenas de como o conteúdo visual é desenhado:
 * - cor sólida
 * - textura
 * - futuramente AssetRef/cache
 */
public final class CanvasVisualRenderer {

    public void render(
            VRenderContext context,
            CanvasVisual visual,
            int left,
            int top,
            int right,
            int bottom
    ) {
        if (visual instanceof ColorVisual colorVisual) {
            renderColorVisual(context, colorVisual, left, top, right, bottom);
            return;
        }

        if (visual instanceof TextureVisual textureVisual) {
            renderTextureVisual(context, textureVisual, left, top, right, bottom);
        }
    }

    private void renderColorVisual(
            VRenderContext context,
            ColorVisual visual,
            int left,
            int top,
            int right,
            int bottom
    ) {
        context.graphics().fill(
                left,
                top,
                right,
                bottom,
                visual.color()
        );
    }

    private void renderTextureVisual(
            VRenderContext context,
            TextureVisual visual,
            int left,
            int top,
            int right,
            int bottom
    ) {
        int drawWidth = right - left;
        int drawHeight = bottom - top;

        context.graphics().blit(
                visual.texture(),
                left,
                top,
                drawWidth,
                drawHeight,
                0.0F,
                0.0F,
                visual.textureWidth(),
                visual.textureHeight(),
                visual.textureWidth(),
                visual.textureHeight()
        );
    }
}