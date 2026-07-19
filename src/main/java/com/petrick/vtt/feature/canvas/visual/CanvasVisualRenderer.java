package com.petrick.vtt.feature.canvas.visual;

import com.mojang.blaze3d.systems.RenderSystem;
import com.petrick.vtt.feature.asset.AssetRef;
import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.feature.asset.LibraryTextureAssetRef;
import com.petrick.vtt.feature.asset.animation.AnimatedTextureFrame;
import com.petrick.vtt.feature.asset.animation.AnimatedTextureService;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.resources.ResourceLocation;

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
 * - textura estática
 * - textura animada
 */
public final class CanvasVisualRenderer {

    private static final int MISSING_TEXTURE_COLOR = 0xFFFF00FF;

    private final AnimatedTextureService animatedTextureService;

    public CanvasVisualRenderer(AnimatedTextureService animatedTextureService) {
        if (animatedTextureService == null) {
            throw new IllegalArgumentException("AnimatedTextureService cannot be null");
        }

        this.animatedTextureService = animatedTextureService;
    }

    public void render(
            VRenderContext context,
            CanvasVisual visual,
            int left,
            int top,
            int right,
            int bottom
    ) {
        render(context, visual, left, top, right, bottom, 1.0F);
    }

    public void render(
            VRenderContext context,
            CanvasVisual visual,
            int left,
            int top,
            int right,
            int bottom,
            float opacity
    ) {
        float safeOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
        if (visual instanceof ColorVisual colorVisual) {
            renderColorVisual(context, colorVisual, left, top, right, bottom, safeOpacity);
            return;
        }

        boolean translucent = safeOpacity < 1.0F;
        if (translucent) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, safeOpacity);
        }
        try {
            if (visual instanceof TextureVisual textureVisual) {
                renderTextureVisual(context, textureVisual.assetRef(), left, top, right, bottom);
                return;
            }

            if (visual instanceof AnimatedTextureVisual animatedTextureVisual) {
                renderAnimatedTextureVisual(context, animatedTextureVisual, left, top, right, bottom);
                return;
            }

            renderMissingTexture(context, left, top, right, bottom, safeOpacity);
        } finally {
            if (translucent) RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void renderColorVisual(
            VRenderContext context,
            ColorVisual visual,
            int left,
            int top,
            int right,
            int bottom,
            float opacity
    ) {
        context.graphics().fill(
                left,
                top,
                right,
                bottom,
                withOpacity(visual.color(), opacity)
        );
    }

    private void renderTextureVisual(
            VRenderContext context,
            AssetRef assetRef,
            int left,
            int top,
            int right,
            int bottom
    ) {
        if (assetRef instanceof BuiltInTextureAssetRef builtInTexture) {
            renderBuiltInTexture(context, builtInTexture, left, top, right, bottom);
            return;
        }

        if (assetRef instanceof LibraryTextureAssetRef libraryTexture) {
            renderLibraryTexture(context, libraryTexture, left, top, right, bottom);
            return;
        }

        renderMissingTexture(context, left, top, right, bottom, 1.0F);
    }

    private void renderAnimatedTextureVisual(
            VRenderContext context,
            AnimatedTextureVisual visual,
            int left,
            int top,
            int right,
            int bottom
    ) {
        if (!(visual.assetRef() instanceof LibraryTextureAssetRef libraryTexture)) {
            renderTextureVisual(context, visual.assetRef(), left, top, right, bottom);
            return;
        }

        AnimatedTextureFrame frame = animatedTextureService.getCurrentFrame(
                libraryTexture,
                System.currentTimeMillis()
        );

        if (frame == null) {
            renderLibraryTexture(context, libraryTexture, left, top, right, bottom);
            return;
        }

        renderTexture(
                context,
                frame.texture(),
                frame.width(),
                frame.height(),
                left,
                top,
                right,
                bottom
        );
    }

    private void renderBuiltInTexture(
            VRenderContext context,
            BuiltInTextureAssetRef asset,
            int left,
            int top,
            int right,
            int bottom
    ) {
        renderTexture(
                context,
                asset.texture(),
                asset.textureWidth(),
                asset.textureHeight(),
                left,
                top,
                right,
                bottom
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
        renderTexture(
                context,
                asset.texture(),
                asset.textureWidth(),
                asset.textureHeight(),
                left,
                top,
                right,
                bottom
        );
    }

    private void renderTexture(
            VRenderContext context,
            ResourceLocation texture,
            int textureWidth,
            int textureHeight,
            int left,
            int top,
            int right,
            int bottom
    ) {
        int drawWidth = right - left;
        int drawHeight = bottom - top;

        context.graphics().blit(
                texture,
                left,
                top,
                drawWidth,
                drawHeight,
                0.0F,
                0.0F,
                textureWidth,
                textureHeight,
                textureWidth,
                textureHeight
        );
    }

    private void renderMissingTexture(
            VRenderContext context,
            int left,
            int top,
            int right,
            int bottom,
            float opacity
    ) {
        context.graphics().fill(
                left,
                top,
                right,
                bottom,
                withOpacity(MISSING_TEXTURE_COLOR, opacity)
        );
    }

    private int withOpacity(int color, float opacity) {
        int alpha = color >>> 24;
        int adjustedAlpha = Math.round(alpha * Math.max(0.0F, Math.min(1.0F, opacity)));
        return (adjustedAlpha << 24) | (color & 0x00FFFFFF);
    }
}
