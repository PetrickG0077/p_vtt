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
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Iterator;

/**
 * Carrega miniaturas de arquivos de imagem externos como DynamicTexture.
 *
 * Por enquanto:
 * - IMAGE carrega a imagem real;
 * - ANIMATED_IMAGE carrega um placeholder temporário;
 * - não redimensiona a imagem;
 * - rejeita arquivos/imagens grandes demais.
 */
public final class AssetThumbnailLoader {

    private static final long MAX_FILE_SIZE_BYTES = 8L * 1024L * 1024L;

    private static final int MAX_IMAGE_WIDTH = 2048;

    private static final int MAX_IMAGE_HEIGHT = 2048;

    private static final int ANIMATED_PLACEHOLDER_SIZE = 64;

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

        if (entry.fileType() != AssetLibraryFileType.IMAGE
                && entry.fileType() != AssetLibraryFileType.ANIMATED_IMAGE) {
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

        if (entry.fileType() == AssetLibraryFileType.ANIMATED_IMAGE) {
            loadAnimatedImagePlaceholder(entry);
            return;
        }

        loadStaticImageThumbnail(entry);
    }

    private void loadStaticImageThumbnail(AssetLibraryEntry entry) {
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

            registerThumbnail(
                    entry.id(),
                    image,
                    "vtt_asset_thumbnail/" + sanitizeTextureName(entry.id())
            );

            VTT.LOGGER.info(
                    "Loaded VTT asset thumbnail: {} ({}x{})",
                    entry.id(),
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

    private void loadAnimatedImagePlaceholder(AssetLibraryEntry entry) {
        try {
            int[] dimensions = readAnimatedImageDimensions(entry);

            int width = dimensions[0];
            int height = dimensions[1];

            if (width > MAX_IMAGE_WIDTH || height > MAX_IMAGE_HEIGHT) {
                VTT.LOGGER.warn(
                        "Skipped animated VTT asset thumbnail because image is too large: {} ({}x{})",
                        entry.absolutePath(),
                        width,
                        height
                );
                return;
            }

            NativeImage image = createAnimatedImagePlaceholder(width, height);

            registerThumbnail(
                    entry.id(),
                    image,
                    "vtt_asset_thumbnail/animated/" + sanitizeTextureName(entry.id())
            );

            VTT.LOGGER.info(
                    "Loaded VTT animated asset placeholder thumbnail: {} ({}x{})",
                    entry.id(),
                    width,
                    height
            );
        } catch (IOException exception) {
            VTT.LOGGER.error(
                    "Failed to read animated VTT asset dimensions: {}",
                    entry.absolutePath(),
                    exception
            );
        } catch (RuntimeException exception) {
            VTT.LOGGER.error(
                    "Failed to create animated VTT asset placeholder thumbnail: {}",
                    entry.absolutePath(),
                    exception
            );
        }
    }

    private NativeImage createAnimatedImagePlaceholder(int width, int height) {
        NativeImage image = new NativeImage(
                width,
                height,
                false
        );

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean checker = ((x / 8) + (y / 8)) % 2 == 0;

                int color = checker
                        ? 0xFFFFAA33
                        : 0xFF552266;

                image.setPixelRGBA(x, y, color);
            }
        }

        return image;
    }

    private int[] readAnimatedImageDimensions(AssetLibraryEntry entry) throws IOException {
        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(
                Files.newInputStream(entry.absolutePath())
        )) {
            if (imageInputStream == null) {
                throw new IOException("Could not create image input stream for " + entry.absolutePath());
            }

            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");

            if (!readers.hasNext()) {
                throw new IOException("No GIF ImageReader available");
            }

            ImageReader reader = readers.next();

            try {
                reader.setInput(imageInputStream, false, false);

                return new int[] {
                        reader.getWidth(0),
                        reader.getHeight(0)
                };
            } finally {
                reader.dispose();
            }
        }
    }

    private void registerThumbnail(
            String thumbnailId,
            NativeImage image,
            String textureName
    ) {
        DynamicTexture dynamicTexture = new DynamicTexture(image);

        ResourceLocation textureLocation = Minecraft.getInstance()
                .getTextureManager()
                .register(
                        textureName,
                        dynamicTexture
                );

        registry.register(new AssetThumbnail(
                thumbnailId,
                textureLocation,
                image.getWidth(),
                image.getHeight()
        ));
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