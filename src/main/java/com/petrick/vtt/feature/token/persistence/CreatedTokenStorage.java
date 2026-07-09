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
import net.minecraft.Util;
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
 *
 * config/vtt_assets/created/tokens/
 *
 * A pasta "created" guarda dados internos do VTT.
 * Ela não deve aparecer no Asset Catalog.
 */
public final class CreatedTokenStorage {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final String TOKENS_FOLDER = "config/vtt_assets/created/tokens";

    private static final String USER_TOKEN_ID_PREFIX = "user/tokens/";

    private static final double MAX_DEFAULT_TOKEN_SIZE = 96.0;

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

        try {
            saveCreatedTokenData(data);

            VTT.LOGGER.info("Saved created VTT token: {}", definition.id());
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

    /**
     * Cria um draft preenchido para editar um token criado pelo usuário.
     */
    public static TokenCreationDraft createEditDraft(TokenDefinition definition) {
        if (definition == null) {
            return null;
        }

        if (!isUserCreatedToken(definition)) {
            VTT.LOGGER.warn(
                    "Ignoring edit request for non-user-created token: {}",
                    definition.id()
            );
            return null;
        }

        Path file = getTokensFolder().resolve(createFileName(definition.displayName()));

        if (!Files.exists(file)) {
            VTT.LOGGER.warn("Cannot edit token because JSON file does not exist: {}", file);
            return null;
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            CreatedTokenSaveData data = GSON.fromJson(reader, CreatedTokenSaveData.class);

            if (!isValid(data)) {
                VTT.LOGGER.warn("Cannot edit invalid created VTT token file: {}", file);
                return null;
            }

            TokenCreationDraft draft = new TokenCreationDraft();

            draft.beginEdit(data.tokenDefinitionId, data.displayName);

            draft.setName(data.displayName);
            draft.setPlayer(data.player);
            draft.setNotes(data.notes);

            draft.selectImage(
                    data.selectedImageId,
                    data.selectedImageDisplayName,
                    ResourceLocation.parse(data.selectedImageTextureId),
                    data.selectedImageWidth,
                    data.selectedImageHeight
            );

            return draft;
        } catch (Exception exception) {
            VTT.LOGGER.error(
                    "Failed to create edit draft for created VTT token: {}",
                    definition.id(),
                    exception
            );
            return null;
        }
    }

    /**
     * Salva alterações feitas no editor de token.
     *
     * O ID do token continua o mesmo. Isso evita quebrar objetos já colocados
     * na mesa que apontam para esse TokenDefinition.
     */
    public static TokenDefinition updateCreatedToken(
            TokenCreationDraft draft,
            TokenDefinitionRegistry tokenDefinitionRegistry,
            AssetRegistry assetRegistry
    ) {
        if (draft == null) {
            return null;
        }

        if (!draft.isEditing()) {
            return null;
        }

        if (tokenDefinitionRegistry == null || assetRegistry == null) {
            return null;
        }

        if (draft.getEditingTokenDefinitionId() == null
                || draft.getEditingTokenDefinitionId().isBlank()) {
            return null;
        }

        TokenDefinition oldDefinition = tokenDefinitionRegistry
                .findById(draft.getEditingTokenDefinitionId())
                .orElse(null);

        if (oldDefinition == null) {
            VTT.LOGGER.warn(
                    "Cannot update created token because definition does not exist: {}",
                    draft.getEditingTokenDefinitionId()
            );
            return null;
        }

        if (!isUserCreatedToken(oldDefinition)) {
            VTT.LOGGER.warn(
                    "Ignoring update request for non-user-created token: {}",
                    oldDefinition.id()
            );
            return null;
        }

        if (!draft.hasSelectedImage()) {
            return null;
        }

        CreatedTokenSaveData data = createSaveDataForEditedDraft(draft);

        try {
            deleteOldFileIfRenamed(draft, data);

            TokenDefinition updatedDefinition = createTokenDefinitionFromSaveData(
                    data,
                    assetRegistry
            );

            tokenDefinitionRegistry.removeById(oldDefinition.id());
            tokenDefinitionRegistry.register(updatedDefinition);

            saveCreatedTokenData(data);

            VTT.LOGGER.info("Updated created VTT token: {}", updatedDefinition.id());

            return updatedDefinition;
        } catch (Exception exception) {
            VTT.LOGGER.error(
                    "Failed to update created VTT token: {}",
                    draft.getEditingTokenDefinitionId(),
                    exception
            );

            return null;
        }
    }

    public static void viewCreatedTokenInExplorer(TokenDefinition definition) {
        if (definition == null) {
            return;
        }

        if (!isUserCreatedToken(definition)) {
            VTT.LOGGER.warn(
                    "Ignoring view in explorer request for non-user-created token: {}",
                    definition.id()
            );
            return;
        }

        Path file = getTokensFolder().resolve(createFileName(definition.displayName()));

        if (Files.exists(file)) {
            openInExplorer(file.getParent());
            return;
        }

        Path folder = getTokensFolder();

        if (Files.exists(folder)) {
            openInExplorer(folder);
            return;
        }

        VTT.LOGGER.warn(
                "Cannot open token in explorer because file/folder does not exist: {}",
                file
        );
    }

    public static TokenDefinition duplicateCreatedToken(
            TokenDefinition sourceDefinition,
            TokenDefinitionRegistry tokenDefinitionRegistry,
            AssetRegistry assetRegistry
    ) {
        if (sourceDefinition == null) {
            return null;
        }

        if (tokenDefinitionRegistry == null || assetRegistry == null) {
            return null;
        }

        if (!isUserCreatedToken(sourceDefinition)) {
            VTT.LOGGER.warn(
                    "Ignoring duplicate request for non-user-created token: {}",
                    sourceDefinition.id()
            );
            return null;
        }

        Path sourceFile = getTokensFolder().resolve(createFileName(sourceDefinition.displayName()));

        if (!Files.exists(sourceFile)) {
            VTT.LOGGER.warn("Cannot duplicate token because JSON file does not exist: {}", sourceFile);
            return null;
        }

        try (Reader reader = Files.newBufferedReader(sourceFile)) {
            CreatedTokenSaveData sourceData = GSON.fromJson(reader, CreatedTokenSaveData.class);

            if (!isValid(sourceData)) {
                VTT.LOGGER.warn("Cannot duplicate invalid created VTT token file: {}", sourceFile);
                return null;
            }

            String newDisplayName = createUniqueDisplayName(
                    tokenDefinitionRegistry,
                    sourceData.displayName + " Copy"
            );

            String newTokenDefinitionId = createUniqueTokenDefinitionId(
                    tokenDefinitionRegistry,
                    USER_TOKEN_ID_PREFIX + sanitizeFileName(newDisplayName)
            );

            CreatedTokenSaveData duplicatedData = new CreatedTokenSaveData();

            duplicatedData.tokenDefinitionId = newTokenDefinitionId;
            duplicatedData.displayName = newDisplayName;

            duplicatedData.player = sourceData.player;
            duplicatedData.notes = sourceData.notes;

            duplicatedData.selectedImageId = sourceData.selectedImageId;
            duplicatedData.selectedImageDisplayName = sourceData.selectedImageDisplayName;
            duplicatedData.selectedImageTextureId = sourceData.selectedImageTextureId;

            duplicatedData.selectedImageWidth = sourceData.selectedImageWidth;
            duplicatedData.selectedImageHeight = sourceData.selectedImageHeight;

            duplicatedData.defaultWidth = sourceData.defaultWidth;
            duplicatedData.defaultHeight = sourceData.defaultHeight;

            duplicatedData.activeStateId = sourceData.activeStateId;

            TokenDefinition duplicatedDefinition = createTokenDefinitionFromSaveData(
                    duplicatedData,
                    assetRegistry
            );

            tokenDefinitionRegistry.register(duplicatedDefinition);

            saveCreatedTokenData(duplicatedData);

            VTT.LOGGER.info(
                    "Duplicated created VTT token: {} -> {}",
                    sourceDefinition.id(),
                    duplicatedDefinition.id()
            );

            return duplicatedDefinition;
        } catch (Exception exception) {
            VTT.LOGGER.error(
                    "Failed to duplicate created VTT token: {}",
                    sourceDefinition.id(),
                    exception
            );

            return null;
        }
    }

    public static void deleteCreatedToken(TokenDefinition definition) {
        if (definition == null) {
            return;
        }

        if (!isUserCreatedToken(definition)) {
            VTT.LOGGER.warn(
                    "Ignoring delete request for non-user-created token: {}",
                    definition.id()
            );
            return;
        }

        Path file = getTokensFolder().resolve(createFileName(definition.displayName()));

        try {
            boolean deleted = Files.deleteIfExists(file);

            if (deleted) {
                VTT.LOGGER.info("Deleted created VTT token file: {}", file);
            } else {
                VTT.LOGGER.warn("Created VTT token file did not exist: {}", file);
            }
        } catch (IOException exception) {
            VTT.LOGGER.error(
                    "Failed to delete created VTT token file: {}",
                    definition.id(),
                    exception
            );
        }
    }

    public static boolean isUserCreatedToken(TokenDefinition definition) {
        if (definition == null) {
            return false;
        }

        return definition.id() != null
                && definition.id().startsWith(USER_TOKEN_ID_PREFIX);
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

    private static CreatedTokenSaveData createSaveDataForEditedDraft(TokenCreationDraft draft) {
        CreatedTokenSaveData data = new CreatedTokenSaveData();

        data.tokenDefinitionId = draft.getEditingTokenDefinitionId();
        data.displayName = draft.getResolvedDisplayName();

        data.player = draft.getPlayer();
        data.notes = draft.getNotes();

        data.selectedImageId = draft.getSelectedImageId();
        data.selectedImageDisplayName = draft.getSelectedImageDisplayName();

        data.selectedImageTextureId = draft.getSelectedImageTexture().toString();

        data.selectedImageWidth = draft.getSelectedImageWidth();
        data.selectedImageHeight = draft.getSelectedImageHeight();

        Vec2d defaultSize = calculateDefaultSize(
                draft.getSelectedImageWidth(),
                draft.getSelectedImageHeight()
        );

        data.defaultWidth = defaultSize.x();
        data.defaultHeight = defaultSize.y();

        data.activeStateId = "1";

        return data;
    }

    private static void deleteOldFileIfRenamed(
            TokenCreationDraft draft,
            CreatedTokenSaveData data
    ) throws IOException {
        String originalDisplayName = draft.getOriginalDisplayName();

        if (originalDisplayName == null || originalDisplayName.isBlank()) {
            return;
        }

        String newDisplayName = data.displayName;

        if (originalDisplayName.equals(newDisplayName)) {
            return;
        }

        Path oldFile = getTokensFolder().resolve(createFileName(originalDisplayName));
        Path newFile = getTokensFolder().resolve(createFileName(newDisplayName));

        if (!oldFile.equals(newFile)) {
            Files.deleteIfExists(oldFile);
        }
    }

    private static void saveCreatedTokenData(CreatedTokenSaveData data) throws IOException {
        Path folder = getTokensFolder();

        Files.createDirectories(folder);

        Path file = folder.resolve(createFileName(data.displayName));

        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(data, writer);
        }
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

    private static void openInExplorer(Path path) {
        if (path == null) {
            return;
        }

        Util.getPlatform().openFile(path.toFile());
    }

    private static String createUniqueDisplayName(
            TokenDefinitionRegistry registry,
            String baseDisplayName
    ) {
        String displayName = baseDisplayName;
        int counter = 2;

        while (hasDisplayName(registry, displayName)) {
            displayName = baseDisplayName + " " + counter;
            counter++;
        }

        return displayName;
    }

    private static boolean hasDisplayName(
            TokenDefinitionRegistry registry,
            String displayName
    ) {
        for (TokenDefinition definition : registry.getAll()) {
            if (definition.displayName().equalsIgnoreCase(displayName)) {
                return true;
            }
        }

        return false;
    }

    private static String createUniqueTokenDefinitionId(
            TokenDefinitionRegistry registry,
            String baseId
    ) {
        String id = baseId;
        int counter = 2;

        while (registry.findById(id).isPresent()) {
            id = baseId + "_" + counter;
            counter++;
        }

        return id;
    }

    private static Vec2d calculateDefaultSize(
            int textureWidth,
            int textureHeight
    ) {
        if (textureWidth <= 0 || textureHeight <= 0) {
            return new Vec2d(MAX_DEFAULT_TOKEN_SIZE, MAX_DEFAULT_TOKEN_SIZE);
        }

        double width = textureWidth;
        double height = textureHeight;

        double largestSide = Math.max(width, height);

        if (largestSide <= MAX_DEFAULT_TOKEN_SIZE) {
            return new Vec2d(width, height);
        }

        double scale = MAX_DEFAULT_TOKEN_SIZE / largestSide;

        return new Vec2d(
                width * scale,
                height * scale
        );
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
