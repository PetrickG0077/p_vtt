package com.petrick.vtt.feature.attachment.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.attachment.AttachmentDefinition;
import com.petrick.vtt.feature.attachment.AttachmentDefinitionRegistry;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

/** Persistence for definitions under config/vtt_assets/created/attachments. */
public final class CreatedAttachmentStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FOLDER = "config/vtt_assets/created/attachments";
    private static final String ID_PREFIX = "user/attachments/";

    private CreatedAttachmentStorage() {}

    public static Path getAttachmentsFolder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(FOLDER);
    }

    public static AttachmentDefinition createDefinition(
            String name, String assetId, double width, double height
    ) {
        String normalized = normalizeName(name);
        return new AttachmentDefinition(
                ID_PREFIX + slug(normalized) + "_" + UUID.randomUUID().toString().substring(0, 8),
                normalized, assetId, width, height);
    }

    public static AttachmentDefinition updateDefinition(
            AttachmentDefinition existing, String name, String assetId,
            double width, double height
    ) {
        if (!isUserCreatedAttachment(existing)) {
            throw new IllegalArgumentException("Only user attachments can be edited");
        }
        return new AttachmentDefinition(
                existing.id(), normalizeName(name), assetId, width, height);
    }

    public static boolean save(AttachmentDefinition definition) {
        return save(definition, getAttachmentsFolder());
    }

    public static boolean save(AttachmentDefinition definition, Path folder) {
        if (definition == null) return false;
        Path targetFolder = folder == null ? getAttachmentsFolder() : folder;
        Path target = targetFolder.resolve(fileName(definition));
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(targetFolder);
            var data = new CreatedAttachmentSaveData(
                    CreatedAttachmentSaveData.CURRENT_SCHEMA_VERSION,
                    definition.id(), definition.displayName(), definition.assetId(),
                    definition.defaultWidth(), definition.defaultHeight());
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(data, writer);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to save VTT attachment: {}", definition.id(), exception);
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
            return false;
        }
    }

    public static AttachmentDefinition duplicate(AttachmentDefinition source) {
        if (!isUserCreatedAttachment(source)) throw new IllegalArgumentException("Invalid attachment");
        AttachmentDefinition copy = createDefinition(
                source.displayName() + " Copy", source.assetId(),
                source.defaultWidth(), source.defaultHeight());
        Path sourceFile = findFile(source);
        Path folder = sourceFile == null ? getAttachmentsFolder() : sourceFile.getParent();
        if (!save(copy, folder)) throw new IllegalStateException("Could not duplicate attachment");
        return copy;
    }

    public static boolean delete(AttachmentDefinition definition) {
        if (!isUserCreatedAttachment(definition)) return false;
        try {
            Path file = findFile(definition);
            return file != null && Files.deleteIfExists(file);
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to delete VTT attachment: {}", definition.id(), exception);
            return false;
        }
    }

    public static boolean isUserCreatedAttachment(AttachmentDefinition definition) {
        return definition != null && definition.id().startsWith(ID_PREFIX);
    }

    public static String folderOf(AttachmentDefinition definition) {
        Path file = findFile(definition);
        return file == null ? "" : relativeFolder(getAttachmentsFolder(), file.getParent());
    }

    public static void loadCreatedAttachments(AttachmentDefinitionRegistry registry) {
        loadCreatedAttachmentsFromFolder(getAttachmentsFolder(), registry);
    }

    public static void loadCreatedAttachmentsFromFolder(
            Path folder, AttachmentDefinitionRegistry registry
    ) {
        if (registry == null || folder == null || !Files.isDirectory(folder)) return;
        try (Stream<Path> files = Files.walk(folder)) {
            files.filter(Files::isRegularFile)
                    .filter(CreatedAttachmentStorage::isJson)
                    .forEach(path -> load(path, folder, registry));
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to load created VTT attachments", exception);
        }
    }

    private static void load(Path file, Path root, AttachmentDefinitionRegistry registry) {
        try (Reader reader = Files.newBufferedReader(file)) {
            CreatedAttachmentSaveData data = GSON.fromJson(reader, CreatedAttachmentSaveData.class);
            if (!valid(data)) {
                VTT.LOGGER.warn("Ignored invalid VTT attachment: {}", file);
                return;
            }
            registry.register(new AttachmentDefinition(
                    data.attachmentDefinitionId(), data.displayName(), data.assetId(),
                    data.defaultWidth(), data.defaultHeight()),
                    relativeFolder(root, file.getParent()));
        } catch (RuntimeException | IOException exception) {
            VTT.LOGGER.error("Failed to load VTT attachment: {}", file, exception);
        }
    }

    private static boolean valid(CreatedAttachmentSaveData data) {
        return data != null && data.schemaVersion() == CreatedAttachmentSaveData.CURRENT_SCHEMA_VERSION
                && data.attachmentDefinitionId() != null
                && data.attachmentDefinitionId().startsWith(ID_PREFIX)
                && data.displayName() != null && !data.displayName().isBlank()
                && Double.isFinite(data.defaultWidth()) && data.defaultWidth() > 0.0
                && data.defaultWidth() <= 16_000.0
                && Double.isFinite(data.defaultHeight()) && data.defaultHeight() > 0.0
                && data.defaultHeight() <= 16_000.0;
    }

    private static Path findFile(AttachmentDefinition definition) {
        if (definition == null || !Files.isDirectory(getAttachmentsFolder())) return null;
        try (Stream<Path> files = Files.walk(getAttachmentsFolder())) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(CreatedAttachmentStorage::isJson).toList()) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    CreatedAttachmentSaveData data = GSON.fromJson(reader, CreatedAttachmentSaveData.class);
                    if (data != null && definition.id().equals(data.attachmentDefinitionId())) {
                        return file;
                    }
                } catch (RuntimeException ignored) {}
            }
        } catch (IOException exception) {
            VTT.LOGGER.warn("Could not search attachment files for {}", definition.id(), exception);
        }
        return null;
    }

    private static boolean isJson(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private static String normalizeName(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isBlank()) throw new IllegalArgumentException("Attachment name cannot be blank");
        return result.length() > 64 ? result.substring(0, 64) : result;
    }

    private static String slug(String value) {
        String result = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return result.isBlank() ? "attachment" : result;
    }

    private static String fileName(AttachmentDefinition definition) {
        return definition.id().substring(definition.id().lastIndexOf('/') + 1) + ".json";
    }

    private static String relativeFolder(Path root, Path folder) {
        try {
            String relative = root.toAbsolutePath().normalize()
                    .relativize(folder.toAbsolutePath().normalize()).toString();
            return ".".equals(relative) ? "" : relative.replace('\\', '/');
        } catch (RuntimeException ignored) {
            return "";
        }
    }
}
