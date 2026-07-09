package com.petrick.vtt.feature.token.persistence;

/**
 * Dados salvos em JSON para tokens criados pelo usuário.
 *
 * Isso não salva a textura em si.
 * Salva a referência da imagem escolhida, para depois recarregar.
 */
public final class CreatedTokenSaveData {

    public String tokenDefinitionId;

    public String displayName;

    public String player;

    public String notes;

    public String selectedImageId;

    public String selectedImageDisplayName;

    public int selectedImageWidth;

    public int selectedImageHeight;

    public double defaultWidth;

    public double defaultHeight;

    public String activeStateId;

    public CreatedTokenSaveData() {}
}