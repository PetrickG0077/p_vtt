package com.petrick.vtt.feature.token.persistence;

import java.util.ArrayList;
import java.util.List;

/**
 * Dados salvos em JSON para tokens criados pelo usuário.
 *
 * Agora suporta múltiplos states.
 */
public final class CreatedTokenSaveData {

    public String tokenDefinitionId;

    public String displayName;

    public String player;

    public String notes;

    /**
     * Campos antigos/compatibilidade.
     * Também continuam úteis como "imagem principal" do token.
     */
    public String selectedImageId;

    public String selectedImageDisplayName;

    public String selectedImageTextureId;

    public int selectedImageWidth;

    public int selectedImageHeight;

    public double defaultWidth;

    public double defaultHeight;

    public String activeStateId;

    public List<StateSaveData> states = new ArrayList<>();

    public CreatedTokenSaveData() {}

    public static final class StateSaveData {

        public String id;

        public String displayName;

        public String imageId;

        public String imageDisplayName;

        public String imageTextureId;

        public int imageWidth;

        public int imageHeight;

        public StateSaveData() {}
    }
}