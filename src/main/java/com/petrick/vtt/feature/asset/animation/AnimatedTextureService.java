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
    private boolean useServerCache;
    private Path serverCacheRoot;

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

    public void setUseServerCache(boolean useServerCache) {
        this.useServerCache = useServerCache;
        if (!useServerCache) this.serverCacheRoot = null;
        clear();
    }

    public void setServerCacheRoot(Path serverCacheRoot) {
        this.serverCacheRoot = serverCacheRoot == null
                ? null : serverCacheRoot.toAbsolutePath().normalize();
        this.useServerCache = this.serverCacheRoot != null;
        clear();
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

        Path gameDirectory = Minecraft.getInstance()
                .gameDirectory
                .toPath();
        String relativePath = normalizeLibraryRelativePath(libraryTexture.sourceRelativePath());
        Path syncedRoot = serverCacheRoot == null
                ? gameDirectory.resolve("config/vtt_assets/cache/server")
                : serverCacheRoot;
        Path syncedFile = syncedRoot.resolve("assets")
                .resolve(relativePath);
        Path localFile = gameDirectory
                .resolve("config")
                .resolve("vtt_assets")
                .resolve("assets")
                .resolve(relativePath);
        Path file = useServerCache && java.nio.file.Files.isRegularFile(syncedFile) ? syncedFile : localFile;

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
