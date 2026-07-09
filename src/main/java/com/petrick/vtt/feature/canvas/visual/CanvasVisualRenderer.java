package com.petrick.vtt.feature.canvas.visual;

import com.petrick.vtt.feature.asset.AssetRef;
import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.feature.asset.LibraryTextureAssetRef;
import com.petrick.vtt.feature.asset.animation.AnimatedTexture;
import com.petrick.vtt.feature.asset.animation.AnimatedTextureFrame;
import com.petrick.vtt.feature.asset.animation.AnimatedTextureLoader;
import com.petrick.vtt.feature.asset.animation.AnimatedTextureRegistry;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

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

    private final AnimatedTextureLoader animatedTextureLoader = new AnimatedTextureLoader();

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
            renderTextureVisual(context, textureVisual.assetRef(), left, top, right, bottom);
            return;
        }

        if (visual instanceof AnimatedTextureVisual animatedTextureVisual) {
            renderAnimatedTextureVisual(context, animatedTextureVisual, left, top, right, bottom);
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

        renderMissingTexture(context, left, top, right, bottom);
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

        AnimatedTexture animatedTexture = getOrLoadAnimatedTexture(libraryTexture);

        if (animatedTexture == null) {
            renderLibraryTexture(context, libraryTexture, left, top, right, bottom);
            return;
        }

        long nowMs = System.currentTimeMillis();
        AnimatedTextureFrame frame = animatedTexture.frameAtTime(nowMs);

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

    private AnimatedTexture getOrLoadAnimatedTexture(
            LibraryTextureAssetRef libraryTexture
    ) {
        AnimatedTextureRegistry registry = AnimatedTextureRegistry.getInstance();

        String animatedTextureId = libraryTexture.id();

        AnimatedTexture existingTexture = registry.findById(animatedTextureId)
                .orElse(null);

        if (existingTexture != null) {
            return existingTexture;
        }

        Path file = Minecraft.getInstance()
                .gameDirectory
                .toPath()
                .resolve("config")
                .resolve("vtt_assets")
                .resolve("assets")
                .resolve(normalizeLibraryRelativePath(libraryTexture.sourceRelativePath()));

        AnimatedTexture loadedTexture = animatedTextureLoader.loadGif(
                animatedTextureId,
                file
        );

        if (loadedTexture == null) {
            return null;
        }

        registry.register(loadedTexture);

        return loadedTexture;
    }

    private String normalizeLibraryRelativePath(String sourceRelativePath) {
        if (sourceRelativePath == null || sourceRelativePath.isBlank()) {
            return "";
        }

        String normalized = sourceRelativePath.replace('\\', '/');

        if (normalized.startsWith("library:")) {
            normalized = normalized.substring("library:".length());
        }

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        return normalized;
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
            net.minecraft.resources.ResourceLocation texture,
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

    private int textureHeight() {
        /*
         * Esse método existe só para evitar confusão no overload do blit?
         * Não. Se o seu Java acusar erro aqui, substitua a chamada por textureHeight direto.
         */
        return 0;
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