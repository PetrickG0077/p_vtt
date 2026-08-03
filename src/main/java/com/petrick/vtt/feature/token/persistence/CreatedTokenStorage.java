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
import com.petrick.vtt.editor.token.TokenStateDraft;
import com.petrick.vtt.feature.asset.library.AssetLibraryFileType;
import com.petrick.vtt.feature.canvas.visual.AnimatedTextureVisual;
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
        loadCreatedTokensFromFolder(getTokensFolder(), tokenDefinitionRegistry, assetRegistry);
    }

    public static void loadCreatedTokensFromFolder(
            Path folder,
            TokenDefinitionRegistry tokenDefinitionRegistry,
            AssetRegistry assetRegistry
    ) {
        if (tokenDefinitionRegistry == null) {
            return;
        }

        if (assetRegistry == null) {
            return;
        }

        if (folder == null || !Files.exists(folder)) {
            return;
        }

        try (Stream<Path> files = Files.walk(folder)) {
            files
                    .filter(Files::isRegularFile)
                    .filter(CreatedTokenStorage::isJsonFile)
                    .forEach(file -> loadCreatedTokenFile(
                            file,
                            folder,
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
        return createEditDraftFromFolder(definition, getTokensFolder());
    }

    public static TokenCreationDraft createEditDraftFromFolder(TokenDefinition definition, Path folder) {
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

        if (folder == null) return null;
        Path file = findTokenFile(folder, definition);

        if (file == null || !Files.exists(file)) {
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
            draft.setDefaultSize(data.defaultWidth, data.defaultHeight);

            draft.replaceStates(
                    createStateDraftsFromSaveData(data),
                    data.activeStateId
            );
            draft.setStatePresets(data.statePresets);

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

    private static Path findTokenFile(Path folder, TokenDefinition definition) {
        Path expected = folder.resolve(createFileName(definition.displayName()));
        if (Files.isRegularFile(expected)) return expected;
        if (!Files.isDirectory(folder)) return null;
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path candidate : files.filter(Files::isRegularFile).filter(CreatedTokenStorage::isJsonFile).toList()) {
                try (Reader reader = Files.newBufferedReader(candidate)) {
                    CreatedTokenSaveData data = GSON.fromJson(reader, CreatedTokenSaveData.class);
                    if (data != null && definition.id().equals(data.tokenDefinitionId)) return candidate;
                } catch (RuntimeException ignored) {
                }
            }
        } catch (IOException exception) {
            VTT.LOGGER.warn("Could not search VTT token definition folder: {}", folder, exception);
        }
        return null;
    }

    public static String serializeCreatedToken(TokenCreationDraft draft, TokenDefinition definition) {
        return GSON.toJson(createSaveData(draft, definition));
    }

    public static String serializeEditedToken(TokenCreationDraft draft) {
        return GSON.toJson(createSaveDataForEditedDraft(draft));
    }

    public static String serializeTokenStatePreset(
            TokenDefinition definition, Path folder,
            String stateId, com.petrick.vtt.feature.token.TokenStatePreset preset
    ) {
        CreatedTokenSaveData data = readTokenData(definition, folder);
        if (data == null || stateId == null || !definition.states().containsKey(stateId)
                || preset == null) return null;
        if (data.statePresets == null) data.statePresets = new LinkedHashMap<>();
        data.statePresets.put(stateId, preset);
        return GSON.toJson(data);
    }

    public static TokenDefinition saveTokenStatePreset(
            TokenDefinition definition, String stateId,
            com.petrick.vtt.feature.token.TokenStatePreset preset,
            TokenDefinitionRegistry registry, AssetRegistry assetRegistry
    ) {
        if (definition == null || registry == null || assetRegistry == null) return null;
        Path file = findTokenFile(getTokensFolder(), definition);
        CreatedTokenSaveData data = readTokenData(definition, getTokensFolder());
        if (file == null || data == null || stateId == null
                || !definition.states().containsKey(stateId) || preset == null) return null;
        if (data.statePresets == null) data.statePresets = new LinkedHashMap<>();
        data.statePresets.put(stateId, preset);
        try {
            saveCreatedTokenData(data, file.getParent());
            TokenDefinition updated = createTokenDefinitionFromSaveData(data, assetRegistry);
            String folder = registry.folderOf(definition.id());
            registry.removeById(definition.id());
            registry.register(updated, folder);
            return updated;
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to save token state preset: {} / {}",
                    definition.id(), stateId, exception);
            return null;
        }
    }

    private static CreatedTokenSaveData readTokenData(
            TokenDefinition definition, Path folder
    ) {
        if (definition == null || folder == null) return null;
        Path file = findTokenFile(folder, definition);
        if (file == null) return null;
        try (Reader reader = Files.newBufferedReader(file)) {
            CreatedTokenSaveData data = GSON.fromJson(reader, CreatedTokenSaveData.class);
            return isValid(data) ? data : null;
        } catch (IOException | RuntimeException exception) {
            VTT.LOGGER.error("Failed to read token preset source: {}", definition.id(), exception);
            return null;
        }
    }

    public static TokenDefinition updateCreatedTokenInMemory(
            TokenCreationDraft draft, TokenDefinitionRegistry registry, AssetRegistry assetRegistry
    ) {
        if (draft == null || !draft.isEditing() || registry == null || assetRegistry == null
                || !draft.hasAnyStateImage() || !draft.allStatesHaveImages()) return null;
        TokenDefinition updated = createTokenDefinitionFromSaveData(
                createSaveDataForEditedDraft(draft), assetRegistry
        );
        registry.removeById(updated.id());
        registry.register(updated);
        return updated;
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

        if (!draft.hasAnyStateImage()) {
            return null;
        }

        if (!draft.allStatesHaveImages()) {
            return null;
        }

        CreatedTokenSaveData data = createSaveDataForEditedDraft(draft);

        try {
            Path existingFile = findTokenFile(getTokensFolder(), oldDefinition);
            Path targetFolder = existingFile == null
                    ? getTokensFolder() : existingFile.getParent();
            deleteOldFileIfRenamed(draft, data, targetFolder);

            TokenDefinition updatedDefinition = createTokenDefinitionFromSaveData(
                    data,
                    assetRegistry
            );

            tokenDefinitionRegistry.removeById(oldDefinition.id());
            tokenDefinitionRegistry.register(
                    updatedDefinition, relativeFolder(getTokensFolder(), targetFolder));

            saveCreatedTokenData(data, targetFolder);

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

        Path file = findTokenFile(getTokensFolder(), definition);

        if (file != null && Files.exists(file)) {
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

        Path sourceFile = findTokenFile(getTokensFolder(), sourceDefinition);

        if (sourceFile == null || !Files.exists(sourceFile)) {
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

            CreatedTokenSaveData duplicatedData = GSON.fromJson(
                    GSON.toJson(sourceData), CreatedTokenSaveData.class);
            duplicatedData.tokenDefinitionId = newTokenDefinitionId;
            duplicatedData.displayName = newDisplayName;

            TokenDefinition duplicatedDefinition = createTokenDefinitionFromSaveData(
                    duplicatedData,
                    assetRegistry
            );

            Path targetFolder = sourceFile.getParent();
            tokenDefinitionRegistry.register(
                    duplicatedDefinition, relativeFolder(getTokensFolder(), targetFolder));

            saveCreatedTokenData(duplicatedData, targetFolder);

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

        Path file = findTokenFile(getTokensFolder(), definition);
        if (file == null) {
            VTT.LOGGER.warn("Created VTT token file did not exist: {}", definition.id());
            return;
        }

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
            Path rootFolder,
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

            tokenDefinitionRegistry.register(
                    definition, relativeFolder(rootFolder, file.getParent()));

            VTT.LOGGER.info("Loaded created VTT token: {}", file);
        } catch (Exception exception) {
            VTT.LOGGER.error("Failed to load created VTT token file: {}", file, exception);
        }
    }

    private static TokenDefinition createTokenDefinitionFromSaveData(
            CreatedTokenSaveData data,
            AssetRegistry assetRegistry
    ) {
        Map<String, CanvasObjectState> states = new LinkedHashMap<>();

        if (data.states != null && !data.states.isEmpty()) {
            for (CreatedTokenSaveData.StateSaveData stateData : data.states) {
                if (!isValidState(stateData)) {
                    continue;
                }

                String assetId = data.tokenDefinitionId + "/states/" + stateData.id + "/image";

                ResourceLocation texture = ResourceLocation.parse(stateData.imageTextureId);

                LibraryTextureAssetRef assetRef = new LibraryTextureAssetRef(
                        assetId,
                        texture,
                        stateData.imageWidth,
                        stateData.imageHeight,
                        stateData.imageId
                );

                assetRegistry.register(assetRef);

                states.put(
                        stateData.id,
                        new CanvasObjectState(
                                stateData.id,
                                stateData.displayName,
                                createVisualForState(assetRef, parseFileType(stateData.imageFileType))
                        )
                );
            }
        }

        /*
         * Compatibilidade com JSON antigo, antes de existir data.states.
         */
        if (states.isEmpty()) {
            String legacyStateId = data.activeStateId == null || data.activeStateId.isBlank()
                    ? "1"
                    : data.activeStateId;

            String assetId = data.tokenDefinitionId + "/image";

            ResourceLocation texture = ResourceLocation.parse(data.selectedImageTextureId);

            LibraryTextureAssetRef assetRef = new LibraryTextureAssetRef(
                    assetId,
                    texture,
                    data.selectedImageWidth,
                    data.selectedImageHeight,
                    data.selectedImageId
            );

            assetRegistry.register(assetRef);

            states.put(
                    legacyStateId,
                    new CanvasObjectState(
                            legacyStateId,
                            "Normal",
                            createVisualForState(
                                    assetRef,
                                    parseFileType(data.selectedImageFileType)
                            )
                    )
            );
        }

        String defaultStateId = data.activeStateId == null || data.activeStateId.isBlank()
                ? states.keySet().iterator().next()
                : data.activeStateId;

        if (!states.containsKey(defaultStateId)) {
            defaultStateId = states.keySet().iterator().next();
        }

        return new TokenDefinition(
                data.tokenDefinitionId,
                data.displayName,
                new Vec2d(data.defaultWidth, data.defaultHeight),
                states,
                defaultStateId,
                data.player,
                data.statePresets
        );
    }

    private static com.petrick.vtt.feature.canvas.visual.CanvasVisual createVisualForState(
            LibraryTextureAssetRef assetRef,
            AssetLibraryFileType fileType
    ) {
        if (fileType == AssetLibraryFileType.ANIMATED_IMAGE) {
            return new AnimatedTextureVisual(assetRef);
        }

        return new TextureVisual(assetRef);
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

        if (!Double.isFinite(data.defaultWidth) || !Double.isFinite(data.defaultHeight)
                || data.defaultWidth < TokenDefinition.MIN_DEFAULT_SIZE
                || data.defaultHeight < TokenDefinition.MIN_DEFAULT_SIZE
                || data.defaultWidth > TokenDefinition.MAX_DEFAULT_SIZE
                || data.defaultHeight > TokenDefinition.MAX_DEFAULT_SIZE) {
            return false;
        }

        if (data.states != null && !data.states.isEmpty()) {
            for (CreatedTokenSaveData.StateSaveData state : data.states) {
                if (!isValidState(state)) {
                    return false;
                }
            }

            return true;
        }

        /*
         * Compatibilidade com JSON antigo.
         */
        if (data.selectedImageId == null || data.selectedImageId.isBlank()) {
            return false;
        }

        if (data.selectedImageTextureId == null || data.selectedImageTextureId.isBlank()) {
            return false;
        }

        if (data.selectedImageWidth <= 0 || data.selectedImageHeight <= 0) {
            return false;
        }

        return true;
    }

    private static boolean isValidState(CreatedTokenSaveData.StateSaveData state) {
        if (state == null) {
            return false;
        }

        if (state.id == null || state.id.isBlank()) {
            return false;
        }

        if (state.displayName == null || state.displayName.isBlank()) {
            return false;
        }

        if (state.imageId == null || state.imageId.isBlank()) {
            return false;
        }

        if (state.imageTextureId == null || state.imageTextureId.isBlank()) {
            return false;
        }

        if (state.imageWidth <= 0 || state.imageHeight <= 0) {
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

        fillSaveDataFromDraft(data, draft);

        return data;
    }

    private static CreatedTokenSaveData createSaveDataForEditedDraft(TokenCreationDraft draft) {
        CreatedTokenSaveData data = new CreatedTokenSaveData();

        data.tokenDefinitionId = draft.getEditingTokenDefinitionId();
        data.displayName = draft.getResolvedDisplayName();

        fillSaveDataFromDraft(data, draft);

        return data;
    }

    private static void fillSaveDataFromDraft(
            CreatedTokenSaveData data,
            TokenCreationDraft draft
    ) {
        if (!draft.hasValidDefaultSize()) {
            throw new IllegalArgumentException("Token draft has an invalid default size");
        }
        data.player = draft.getPlayer();
        data.notes = draft.getNotes();

        String defaultStateId = draft.getDefaultStateIdForSave();
        TokenStateDraft defaultState = draft.getDefaultStateForSave();

        data.activeStateId = defaultStateId;

        data.selectedImageId = defaultState.getImageId();
        data.selectedImageDisplayName = defaultState.getImageDisplayName();
        data.selectedImageTextureId = defaultState.getImageTexture().toString();
        data.selectedImageFileType = defaultState.getImageFileType().name();
        data.selectedImageWidth = defaultState.getImageWidth();
        data.selectedImageHeight = defaultState.getImageHeight();

        data.defaultWidth = draft.getDefaultWidth();
        data.defaultHeight = draft.getDefaultHeight();

        data.states.clear();
        data.statePresets = new LinkedHashMap<>();

        for (TokenStateDraft stateDraft : draft.getStates()) {
            if (!stateDraft.hasImage()) {
                continue;
            }

            CreatedTokenSaveData.StateSaveData stateData =
                    new CreatedTokenSaveData.StateSaveData();

            stateData.id = stateDraft.getId();
            stateData.displayName = stateDraft.getDisplayName();

            stateData.imageId = stateDraft.getImageId();
            stateData.imageDisplayName = stateDraft.getImageDisplayName();
            stateData.imageTextureId = stateDraft.getImageTexture().toString();
            stateData.imageFileType = stateDraft.getImageFileType().name();
            stateData.imageWidth = stateDraft.getImageWidth();
            stateData.imageHeight = stateDraft.getImageHeight();

            data.states.add(stateData);
            com.petrick.vtt.feature.token.TokenStatePreset preset =
                    draft.getStatePresets().get(stateDraft.getId());
            if (preset != null) data.statePresets.put(stateDraft.getId(), preset);
        }
    }

    private static java.util.List<TokenStateDraft> createStateDraftsFromSaveData(
            CreatedTokenSaveData data
    ) {
        java.util.List<TokenStateDraft> drafts = new java.util.ArrayList<>();

        if (data.states != null && !data.states.isEmpty()) {
            for (CreatedTokenSaveData.StateSaveData stateData : data.states) {
                if (!isValidState(stateData)) {
                    continue;
                }

                TokenStateDraft stateDraft = new TokenStateDraft(
                        stateData.id,
                        stateData.displayName
                );

                stateDraft.selectImage(
                        stateData.imageId,
                        stateData.imageDisplayName,
                        ResourceLocation.parse(stateData.imageTextureId),
                        stateData.imageWidth,
                        stateData.imageHeight,
                        parseFileType(stateData.imageFileType)
                );

                drafts.add(stateDraft);
            }

            return drafts;
        }

        /*
         * Compatibilidade com JSON antigo.
         */
        String legacyStateId = data.activeStateId == null || data.activeStateId.isBlank()
                ? "1"
                : data.activeStateId;

        TokenStateDraft legacyState = new TokenStateDraft(
                legacyStateId,
                "Normal"
        );

        legacyState.selectImage(
                data.selectedImageId,
                data.selectedImageDisplayName,
                ResourceLocation.parse(data.selectedImageTextureId),
                data.selectedImageWidth,
                data.selectedImageHeight
        );

        drafts.add(legacyState);

        return drafts;
    }

    private static AssetLibraryFileType parseFileType(String value) {
        if (value == null || value.isBlank()) {
            return AssetLibraryFileType.IMAGE;
        }

        try {
            return AssetLibraryFileType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return AssetLibraryFileType.IMAGE;
        }
    }

    private static void deleteOldFileIfRenamed(
            TokenCreationDraft draft,
            CreatedTokenSaveData data,
            Path folder
    ) throws IOException {
        String originalDisplayName = draft.getOriginalDisplayName();

        if (originalDisplayName == null || originalDisplayName.isBlank()) {
            return;
        }

        String newDisplayName = data.displayName;

        if (originalDisplayName.equals(newDisplayName)) {
            return;
        }

        Path targetFolder = folder == null ? getTokensFolder() : folder;
        Path oldFile = targetFolder.resolve(createFileName(originalDisplayName));
        Path newFile = targetFolder.resolve(createFileName(newDisplayName));

        if (!oldFile.equals(newFile)) {
            Files.deleteIfExists(oldFile);
        }
    }

    private static void saveCreatedTokenData(CreatedTokenSaveData data) throws IOException {
        saveCreatedTokenData(data, getTokensFolder());
    }

    private static void saveCreatedTokenData(
            CreatedTokenSaveData data, Path folder
    ) throws IOException {
        if (folder == null) folder = getTokensFolder();
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

    private static String relativeFolder(Path root, Path folder) {
        if (root == null || folder == null) return "";
        try {
            String relative = root.toAbsolutePath().normalize()
                    .relativize(folder.toAbsolutePath().normalize()).toString();
            return ".".equals(relative) ? "" : relative.replace('\\', '/');
        } catch (IllegalArgumentException ignored) {
            return "";
        }
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
