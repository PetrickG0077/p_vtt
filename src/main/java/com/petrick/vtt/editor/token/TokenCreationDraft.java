package com.petrick.vtt.editor.token;

import net.minecraft.resources.ResourceLocation;

/**
 * Dados temporários enquanto o usuário está criando um token.
 */
public final class TokenCreationDraft {

    private String name = "";

    private String player = "";

    private String notes = "";

    private String errorMessage = "";

    private String selectedImageId;

    private String selectedImageDisplayName;

    private ResourceLocation selectedImageTexture;

    private int selectedImageWidth;

    private int selectedImageHeight;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = sanitize(name);
        clearError();
    }

    public String getPlayer() {
        return player;
    }

    public void setPlayer(String player) {
        this.player = sanitize(player);
        clearError();
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = sanitize(notes);
        clearError();
    }

    public boolean hasName() {
        return name != null && !name.isBlank();
    }

    public String getResolvedDisplayName() {
        if (hasName()) {
            return name.trim();
        }

        if (selectedImageDisplayName != null && !selectedImageDisplayName.isBlank()) {
            return selectedImageDisplayName;
        }

        return "New Token";
    }

    public boolean hasSelectedImage() {
        return selectedImageTexture != null;
    }

    public String getSelectedImageId() {
        return selectedImageId;
    }

    public String getSelectedImageDisplayName() {
        return selectedImageDisplayName;
    }

    public ResourceLocation getSelectedImageTexture() {
        return selectedImageTexture;
    }

    public int getSelectedImageWidth() {
        return selectedImageWidth;
    }

    public int getSelectedImageHeight() {
        return selectedImageHeight;
    }

    public void selectImage(
            String imageId,
            String displayName,
            ResourceLocation texture,
            int width,
            int height
    ) {
        if (imageId == null || imageId.isBlank()) {
            clearSelectedImage();
            return;
        }

        if (texture == null || width <= 0 || height <= 0) {
            clearSelectedImage();
            return;
        }

        this.selectedImageId = imageId;
        this.selectedImageDisplayName = displayName == null || displayName.isBlank()
                ? imageId
                : displayName;
        this.selectedImageTexture = texture;
        this.selectedImageWidth = width;
        this.selectedImageHeight = height;

        clearError();
    }

    public void clearSelectedImage() {
        this.selectedImageId = null;
        this.selectedImageDisplayName = null;
        this.selectedImageTexture = null;
        this.selectedImageWidth = 0;
        this.selectedImageHeight = 0;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isBlank();
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = sanitize(errorMessage);
    }

    public void clearError() {
        this.errorMessage = "";
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }

        return value;
    }
}