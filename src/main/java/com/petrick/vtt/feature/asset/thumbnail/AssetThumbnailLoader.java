package com.petrick.vtt.feature.asset.thumbnail;

import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.asset.library.AssetLibraryEntry;
import com.petrick.vtt.feature.asset.library.AssetLibraryFileType;
import com.mojang.blaze3d.platform.NativeImage;
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
 * - não faz resize real da imagem;
 * - usa a imagem inteira como textura dinâmica.
 */
public final class AssetThumbnailLoader {

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

        try (InputStream inputStream = Files.newInputStream(entry.absolutePath())) {
            NativeImage image = NativeImage.read(inputStream);

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

            VTT.LOGGER.info("Loaded VTT asset thumbnail: {}", thumbnailId);
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to load VTT asset thumbnail: {}", entry.absolutePath(), exception);
        }
    }

    private String sanitizeTextureName(String value) {
        return value
                .toLowerCase()
                .replace('\\', '/')
                .replaceAll("[^a-z0-9/._-]", "_");
    }
}