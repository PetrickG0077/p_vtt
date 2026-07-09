package com.petrick.vtt.feature.token.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.VTT;
import com.petrick.vtt.editor.token.TokenCreationDraft;
import com.petrick.vtt.feature.token.TokenDefinition;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Salva tokens criados pelo usuário em JSON.
 *
 * Pasta:
 * config/vtt_assets/user/tokens/
 */
public final class CreatedTokenStorage {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final String TOKENS_FOLDER = "config/vtt_assets/user/tokens";

    private CreatedTokenStorage() {}

    public static void saveCreatedToken(
            TokenCreationDraft draft,
            TokenDefinition definition
    ) {
        if (draft == null) {
            return;
        }

        if (definition == null) {
            return;
        }

        CreatedTokenSaveData data = createSaveData(draft, definition);

        Path folder = getTokensFolder();

        try {
            Files.createDirectories(folder);

            Path file = folder.resolve(createFileName(definition.displayName()));

            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(data, writer);
            }

            VTT.LOGGER.info("Saved created VTT token: {}", file);
        } catch (IOException exception) {
            VTT.LOGGER.error(
                    "Failed to save created VTT token: {}",
                    definition.id(),
                    exception
            );
        }
    }

    private static CreatedTokenSaveData createSaveData(
            TokenCreationDraft draft,
            TokenDefinition definition
    ) {
        CreatedTokenSaveData data = new CreatedTokenSaveData();

        data.tokenDefinitionId = definition.id();
        data.displayName = definition.displayName();

        data.player = draft.getPlayer();
        data.notes = draft.getNotes();

        data.selectedImageId = draft.getSelectedImageId();
        data.selectedImageDisplayName = draft.getSelectedImageDisplayName();

        data.selectedImageWidth = draft.getSelectedImageWidth();
        data.selectedImageHeight = draft.getSelectedImageHeight();

        data.defaultWidth = definition.defaultSize().x();
        data.defaultHeight = definition.defaultSize().y();

        data.activeStateId = definition.defaultStateId();

        return data;
    }

    private static Path getTokensFolder() {
        return Minecraft.getInstance()
                .gameDirectory
                .toPath()
                .resolve(TOKENS_FOLDER);
    }

    private static String createFileName(String displayName) {
        String safeName = sanitizeFileName(displayName);

        if (safeName.isBlank()) {
            safeName = "new_token";
        }

        return safeName + ".json";
    }

    private static String sanitizeFileName(String value) {
        if (value == null) {
            return "new_token";
        }

        return value
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9._-]", "_")
                .replaceAll("_+", "_");
    }
}