package com.petrick.vtt.feature.token.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.VTT;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.editor.token.TokenCreationDraft;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.LibraryTextureAssetRef;
import com.petrick.vtt.feature.canvas.CanvasObjectState;
import com.petrick.vtt.feature.canvas.visual.TextureVisual;
import com.petrick.vtt.feature.token.TokenDefinition;
import com.petrick.vtt.feature.token.TokenDefinitionRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Salva e carrega tokens criados pelo usuário em JSON.
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

    public static void loadCreatedTokens(
            TokenDefinitionRegistry tokenDefinitionRegistry,
            AssetRegistry assetRegistry
    ) {
        if (tokenDefinitionRegistry == null) {
            return;
        }

        if (assetRegistry == null) {
            return;
        }

        Path folder = getTokensFolder();

        if (!Files.exists(folder)) {
            return;
        }

        try (Stream<Path> files = Files.list(folder)) {
            files
                    .filter(Files::isRegularFile)
                    .filter(CreatedTokenStorage::isJsonFile)
                    .forEach(file -> loadCreatedTokenFile(
                            file,
                            tokenDefinitionRegistry,
                            assetRegistry
                    ));
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to load created VTT tokens", exception);
        }
    }

    private static void loadCreatedTokenFile(
            Path file,
            TokenDefinitionRegistry tokenDefinitionRegistry,
            AssetRegistry assetRegistry
    ) {
        try (Reader reader = Files.newBufferedReader(file)) {
            CreatedTokenSaveData data = GSON.fromJson(reader, CreatedTokenSaveData.class);

            if (!isValid(data)) {
                VTT.LOGGER.warn("Skipping invalid created VTT token file: {}", file);
                return;
            }

            TokenDefinition definition = createTokenDefinitionFromSaveData(
                    data,
                    assetRegistry
            );

            tokenDefinitionRegistry.register(definition);

            VTT.LOGGER.info("Loaded created VTT token: {}", file);
        } catch (Exception exception) {
            VTT.LOGGER.error("Failed to load created VTT token file: {}", file, exception);
        }
    }

    private static TokenDefinition createTokenDefinitionFromSaveData(
            CreatedTokenSaveData data,
            AssetRegistry assetRegistry
    ) {
        String assetId = data.tokenDefinitionId + "/image";

        ResourceLocation texture = ResourceLocation.parse(data.selectedImageTextureId);

        LibraryTextureAssetRef assetRef = new LibraryTextureAssetRef(
                assetId,
                texture,
                data.selectedImageWidth,
                data.selectedImageHeight,
                data.selectedImageDisplayName
        );

        assetRegistry.register(assetRef);

        String stateId = data.activeStateId == null || data.activeStateId.isBlank()
                ? "1"
                : data.activeStateId;

        Map<String, CanvasObjectState> states = new LinkedHashMap<>();

        states.put(
                stateId,
                new CanvasObjectState(
                        stateId,
                        "Normal",
                        new TextureVisual(assetRef)
                )
        );

        return new TokenDefinition(
                data.tokenDefinitionId,
                data.displayName,
                new Vec2d(data.defaultWidth, data.defaultHeight),
                states,
                stateId
        );
    }

    private static boolean isValid(CreatedTokenSaveData data) {
        if (data == null) {
            return false;
        }

        if (data.tokenDefinitionId == null || data.tokenDefinitionId.isBlank()) {
            return false;
        }

        if (data.displayName == null || data.displayName.isBlank()) {
            return false;
        }

        if (data.selectedImageId == null || data.selectedImageId.isBlank()) {
            return false;
        }

        if (data.selectedImageTextureId == null || data.selectedImageTextureId.isBlank()) {
            return false;
        }

        if (data.selectedImageWidth <= 0 || data.selectedImageHeight <= 0) {
            return false;
        }

        if (data.defaultWidth <= 0 || data.defaultHeight <= 0) {
            return false;
        }

        return true;
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

        data.selectedImageTextureId = draft.getSelectedImageTexture().toString();

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

    private static boolean isJsonFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return fileName.endsWith(".json");
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