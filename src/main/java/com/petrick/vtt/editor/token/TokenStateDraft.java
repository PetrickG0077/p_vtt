package com.petrick.vtt.editor.token;

import com.petrick.vtt.feature.asset.library.AssetLibraryFileType;
import net.minecraft.resources.ResourceLocation;

/**
 * Estado temporário dentro do editor de tokens.
 */
public final class TokenStateDraft {

    private final String id;

    private String displayName;

    private String imageId;

    private String imageDisplayName;

    private ResourceLocation imageTexture;

    private int imageWidth;

    private int imageHeight;

    private AssetLibraryFileType imageFileType = AssetLibraryFileType.IMAGE;

    public TokenStateDraft(String id, String displayName) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("State id cannot be null or blank");
        }

        this.id = id;
        this.displayName = displayName == null || displayName.isBlank()
                ? "State " + id
                : displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            this.displayName = "State " + id;
            return;
        }

        this.displayName = displayName;
    }

    public boolean hasImage() {
        return imageTexture != null;
    }

    public boolean isAnimatedImage() {
        return imageFileType == AssetLibraryFileType.ANIMATED_IMAGE;
    }

    public AssetLibraryFileType getImageFileType() {
        return imageFileType;
    }

    public String getImageId() {
        return imageId;
    }

    public String getImageDisplayName() {
        return imageDisplayName;
    }

    public ResourceLocation getImageTexture() {
        return imageTexture;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public void selectImage(
            String imageId,
            String imageDisplayName,
            ResourceLocation imageTexture,
            int imageWidth,
            int imageHeight
    ) {
        selectImage(
                imageId,
                imageDisplayName,
                imageTexture,
                imageWidth,
                imageHeight,
                AssetLibraryFileType.IMAGE
        );
    }

    public void selectImage(
            String imageId,
            String imageDisplayName,
            ResourceLocation imageTexture,
            int imageWidth,
            int imageHeight,
            AssetLibraryFileType imageFileType
    ) {
        if (imageId == null || imageId.isBlank()) {
            clearImage();
            return;
        }

        if (imageTexture == null || imageWidth <= 0 || imageHeight <= 0) {
            clearImage();
            return;
        }

        this.imageId = imageId;
        this.imageDisplayName = imageDisplayName == null || imageDisplayName.isBlank()
                ? imageId
                : imageDisplayName;
        this.imageTexture = imageTexture;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.imageFileType = imageFileType == null
                ? AssetLibraryFileType.IMAGE
                : imageFileType;
    }

    public void clearImage() {
        this.imageId = null;
        this.imageDisplayName = null;
        this.imageTexture = null;
        this.imageWidth = 0;
        this.imageHeight = 0;
        this.imageFileType = AssetLibraryFileType.IMAGE;
    }
}