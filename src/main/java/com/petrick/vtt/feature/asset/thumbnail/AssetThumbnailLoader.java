package com.petrick.vtt.feature.asset.thumbnail;

import com.mojang.blaze3d.platform.NativeImage;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.asset.library.AssetLibraryEntry;
import com.petrick.vtt.feature.asset.library.AssetLibraryFileType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Carrega miniaturas de arquivos de imagem externos como DynamicTexture.
 *
 * Por enquanto:
 * - carrega apenas IMAGE;
 * - não carrega ANIMATED_IMAGE;
 * - não redimensiona a imagem;
 * - rejeita arquivos/imagens grandes demais.
 */
public final class AssetThumbnailLoader {

    private static final long MAX_FILE_SIZE_BYTES = 8L * 1024L * 1024L;

    private static final int MAX_IMAGE_WIDTH = 2048;

    private static final int MAX_IMAGE_HEIGHT = 2048;

    private final AssetThumbnailRegistry registry;

    public AssetThumbnailLoader(AssetThumbnailRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("AssetThumbnailRegistry cannot be null");
        }

        this.registry = registry;
    }

    public void loadThumbnailIfNeeded(AssetLibraryEntry entry) {
        if (entry == null) {
            return;
        }

        if (entry.fileType() != AssetLibraryFileType.IMAGE) {
            return;
        }

        String thumbnailId = entry.id();

        if (registry.contains(thumbnailId)) {
            return;
        }

        if (!isFileSizeAllowed(entry)) {
            VTT.LOGGER.warn(
                    "Skipped VTT asset thumbnail because file is too large: {}",
                    entry.absolutePath()
            );
            return;
        }

        try (InputStream inputStream = Files.newInputStream(entry.absolutePath())) {
            NativeImage image = NativeImage.read(inputStream);

            if (!isImageSizeAllowed(image)) {
                VTT.LOGGER.warn(
                        "Skipped VTT asset thumbnail because image is too large: {} ({}x{})",
                        entry.absolutePath(),
                        image.getWidth(),
                        image.getHeight()
                );

                image.close();
                return;
            }

            DynamicTexture dynamicTexture = new DynamicTexture(image);

            ResourceLocation textureLocation = Minecraft.getInstance()
                    .getTextureManager()
                    .register(
                            "vtt_asset_thumbnail/" + sanitizeTextureName(thumbnailId),
                            dynamicTexture
                    );

            registry.register(new AssetThumbnail(
                    thumbnailId,
                    textureLocation,
                    image.getWidth(),
                    image.getHeight()
            ));

            VTT.LOGGER.info(
                    "Loaded VTT asset thumbnail: {} ({}x{})",
                    thumbnailId,
                    image.getWidth(),
                    image.getHeight()
            );
        } catch (IOException exception) {
            VTT.LOGGER.error(
                    "Failed to load VTT asset thumbnail: {}",
                    entry.absolutePath(),
                    exception
            );
        } catch (RuntimeException exception) {
            VTT.LOGGER.error(
                    "Unexpected error while loading VTT asset thumbnail: {}",
                    entry.absolutePath(),
                    exception
            );
        }
    }

    private boolean isFileSizeAllowed(AssetLibraryEntry entry) {
        try {
            long fileSize = Files.size(entry.absolutePath());

            return fileSize <= MAX_FILE_SIZE_BYTES;
        } catch (IOException exception) {
            VTT.LOGGER.warn(
                    "Could not read VTT asset file size: {}",
                    entry.absolutePath(),
                    exception
            );

            return false;
        }
    }

    private boolean isImageSizeAllowed(NativeImage image) {
        return image.getWidth() <= MAX_IMAGE_WIDTH
                && image.getHeight() <= MAX_IMAGE_HEIGHT;
    }

    private String sanitizeTextureName(String value) {
        return value
                .toLowerCase()
                .replace('\\', '/')
                .replaceAll("[^a-z0-9/._-]", "_");
    }
}