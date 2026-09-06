package com.petrick.vtt.feature.asset.thumbnail;

import com.mojang.blaze3d.platform.NativeImage;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.asset.library.AssetLibraryEntry;
import com.petrick.vtt.feature.asset.library.AssetLibraryFileType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Iterator;

/**
 * Carrega miniaturas de arquivos de imagem externos como DynamicTexture.
 *
 * Por enquanto:
 * - IMAGE carrega a imagem real;
 * - ANIMATED_IMAGE carrega o primeiro frame do GIF como thumbnail;
 * - não redimensiona a imagem;
 * - rejeita arquivos/imagens grandes demais.
 */
public final class AssetThumbnailLoader {

    private static final long MAX_FILE_SIZE_BYTES = 64L * 1024L * 1024L;

    private static final int MAX_IMAGE_WIDTH = 5120;

    private static final int MAX_IMAGE_HEIGHT = 5120;

    private static final long MAX_NATIVE_IMAGE_BYTES = 64L * 1024L * 1024L;

    private static final int ANIMATED_FALLBACK_PLACEHOLDER_SIZE = 64;

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
            loadAnimatedImageThumbnail(entry);
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

    private void loadAnimatedImageThumbnail(AssetLibraryEntry entry) {
        try {
            NativeImage image = readFirstGifFrameAsNativeImage(entry);

            if (!isImageSizeAllowed(image)) {
                VTT.LOGGER.warn(
                        "Skipped animated VTT asset thumbnail because image is too large: {} ({}x{})",
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
                    "vtt_asset_thumbnail/animated/" + sanitizeTextureName(entry.id())
            );

            VTT.LOGGER.info(
                    "Loaded VTT animated asset thumbnail first frame: {} ({}x{})",
                    entry.id(),
                    image.getWidth(),
                    image.getHeight()
            );
        } catch (IOException exception) {
            VTT.LOGGER.error(
                    "Failed to load first frame for animated VTT asset thumbnail: {}",
                    entry.absolutePath(),
                    exception
            );

            loadAnimatedImageFallbackPlaceholder(entry);
        } catch (RuntimeException exception) {
            VTT.LOGGER.error(
                    "Unexpected error while loading animated VTT asset thumbnail: {}",
                    entry.absolutePath(),
                    exception
            );

            loadAnimatedImageFallbackPlaceholder(entry);
        }
    }

    private NativeImage readFirstGifFrameAsNativeImage(AssetLibraryEntry entry) throws IOException {
        ImageIO.setUseCache(false);
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

                BufferedImage firstFrame = reader.read(0);

                if (firstFrame == null) {
                    throw new IOException("GIF first frame is null: " + entry.absolutePath());
                }

                return convertToNativeImage(firstFrame);
            } finally {
                reader.dispose();
            }
        }
    }

    private NativeImage convertToNativeImage(BufferedImage bufferedImage) {
        NativeImage nativeImage = new NativeImage(
                bufferedImage.getWidth(),
                bufferedImage.getHeight(),
                false
        );

        for (int y = 0; y < bufferedImage.getHeight(); y++) {
            for (int x = 0; x < bufferedImage.getWidth(); x++) {
                int argb = bufferedImage.getRGB(x, y);

                int alpha = (argb >> 24) & 0xFF;
                int red = (argb >> 16) & 0xFF;
                int green = (argb >> 8) & 0xFF;
                int blue = argb & 0xFF;

                int abgr = alpha << 24
                        | blue << 16
                        | green << 8
                        | red;

                nativeImage.setPixelRGBA(x, y, abgr);
            }
        }

        return nativeImage;
    }

    private void loadAnimatedImageFallbackPlaceholder(AssetLibraryEntry entry) {
        try {
            NativeImage image = createAnimatedImagePlaceholder(
                    ANIMATED_FALLBACK_PLACEHOLDER_SIZE,
                    ANIMATED_FALLBACK_PLACEHOLDER_SIZE
            );

            registerThumbnail(
                    entry.id(),
                    image,
                    "vtt_asset_thumbnail/animated/fallback/" + sanitizeTextureName(entry.id())
            );

            VTT.LOGGER.info(
                    "Loaded fallback VTT animated asset placeholder thumbnail: {}",
                    entry.id()
            );
        } catch (RuntimeException exception) {
            VTT.LOGGER.error(
                    "Failed to create fallback animated VTT asset placeholder thumbnail: {}",
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
                boolean checker = ((x / 16) + (y / 16)) % 2 == 0;

                int color = checker
                        ? 0xFFFFAA33
                        : 0xFF552266;

                image.setPixelRGBA(x, y, color);
            }
        }

        return image;
    }

    private void registerThumbnail(
            String thumbnailId,
            NativeImage image,
            String textureName
    ) {
        DynamicTexture dynamicTexture = new DynamicTexture(image);

        try {
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

        } catch (RuntimeException exception) {
            dynamicTexture.close();

            VTT.LOGGER.error(
                    "Failed to register VTT asset thumbnail texture: {}",
                    textureName,
                    exception
            );

            throw exception;
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
        if (image.getWidth() > MAX_IMAGE_WIDTH
                || image.getHeight() > MAX_IMAGE_HEIGHT) {
            return false;
        }

        long estimatedBytes = (long) image.getWidth()
                * image.getHeight()
                * 4L;

        return estimatedBytes <= MAX_NATIVE_IMAGE_BYTES;
    }

    private String sanitizeTextureName(String value) {
        return value
                .toLowerCase()
                .replace('\\', '/')
                .replaceAll("[^a-z0-9/._-]", "_");
    }
}
