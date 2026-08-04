package com.petrick.vtt.feature.asset.library;

/**
 * Pastas padrão criadas automaticamente pelo VTT.
 *
 * Importante:
 * estas pastas NÃO limitam a organização do usuário.
 * O usuário pode criar qualquer subpasta dentro de config/vtt_assets/.
 */
public enum AssetLibraryDefaultFolder {

    TOKENS("tokens"),
    MAPS("maps"),
    PORTRAITS("portraits"),
    DOCUMENTS("documents"),
    ITEMS("items"),
    MUSICS("musics"),
    SHOWS("shows"),
    MISC("misc");

    private final String folderName;

    AssetLibraryDefaultFolder(String folderName) {
        this.folderName = folderName;
    }

    public String folderName() {
        return folderName;
    }
}
