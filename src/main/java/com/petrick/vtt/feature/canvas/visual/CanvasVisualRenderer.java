package com.petrick.vtt.feature.canvas.visual;

import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.feature.asset.LibraryTextureAssetRef;
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
 * - textura built-in
 * - textura carregada da biblioteca do usuário
 */
public final class CanvasVisualRenderer {

    private static final int MISSING_TEXTURE_COLOR = 0xFFFF00FF;

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
            return;
        }

        renderMissingTexture(context, left, top, right, bottom);
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
        if (visual.assetRef() instanceof BuiltInTextureAssetRef builtInTexture) {
            renderBuiltInTexture(context, builtInTexture, left, top, right, bottom);
            return;
        }

        if (visual.assetRef() instanceof LibraryTextureAssetRef libraryTexture) {
            renderLibraryTexture(context, libraryTexture, left, top, right, bottom);
            return;
        }

        renderMissingTexture(context, left, top, right, bottom);
    }

    private void renderBuiltInTexture(
            VRenderContext context,
            BuiltInTextureAssetRef asset,
            int left,
            int top,
            int right,
            int bottom
    ) {
        int drawWidth = right - left;
        int drawHeight = bottom - top;

        context.graphics().blit(
                asset.texture(),
                left,
                top,
                drawWidth,
                drawHeight,
                0.0F,
                0.0F,
                asset.textureWidth(),
                asset.textureHeight(),
                asset.textureWidth(),
                asset.textureHeight()
        );
    }

    private void renderLibraryTexture(
            VRenderContext context,
            LibraryTextureAssetRef asset,
            int left,
            int top,
            int right,
            int bottom
    ) {
        int drawWidth = right - left;
        int drawHeight = bottom - top;

        context.graphics().blit(
                asset.texture(),
                left,
                top,
                drawWidth,
                drawHeight,
                0.0F,
                0.0F,
                asset.textureWidth(),
                asset.textureHeight(),
                asset.textureWidth(),
                asset.textureHeight()
        );
    }

    private void renderMissingTexture(
            VRenderContext context,
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
                MISSING_TEXTURE_COLOR
        );
    }
}