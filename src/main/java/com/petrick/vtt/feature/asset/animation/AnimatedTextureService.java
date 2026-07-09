package com.petrick.vtt.feature.asset.animation;

import com.petrick.vtt.feature.asset.LibraryTextureAssetRef;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/**
 * Serviço responsável por carregar e cachear texturas animadas.
 *
 * O renderer não deve saber como encontrar arquivos no disco
 * nem como carregar GIFs. Ele só pede o frame atual para este serviço.
 */
public final class AnimatedTextureService {

    private final AnimatedTextureRegistry registry;

    private final AnimatedTextureLoader loader;

    public AnimatedTextureService() {
        this.registry = new AnimatedTextureRegistry();
        this.loader = new AnimatedTextureLoader();
    }

    public AnimatedTextureFrame getCurrentFrame(
            LibraryTextureAssetRef libraryTexture,
            long timeMs
    ) {
        if (libraryTexture == null) {
            return null;
        }

        AnimatedTexture animatedTexture = getOrLoadAnimatedTexture(libraryTexture);

        if (animatedTexture == null) {
            return null;
        }

        return animatedTexture.frameAtTime(timeMs);
    }

    public int loadedTextureCount() {
        return registry.size();
    }

    public void clear() {
        registry.clear();
    }

    private AnimatedTexture getOrLoadAnimatedTexture(
            LibraryTextureAssetRef libraryTexture
    ) {
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

        AnimatedTexture loadedTexture = loader.loadGif(
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
}