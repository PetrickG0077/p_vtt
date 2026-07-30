package com.petrick.vtt.feature.asset.folder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.map.MapDefinitionRegistry;
import com.petrick.vtt.feature.tabletop.VttTabletop;
import com.petrick.vtt.feature.token.TokenDefinitionRegistry;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Safe physical folder operations for scene, map and token definition catalogs. */
public final class VttAssetFolderService {
    private static final int MAX_DEPTH = 16;
    private static final int MAX_NAME_LENGTH = 64;

    private final Path gameDirectory;
    private final String tabletopId;
    private final EnumMap<Section, List<String>> folders =
            new EnumMap<>(Section.class);
    private final EnumMap<Section, Map<String, String>> itemFolders =
            new EnumMap<>(Section.class);

    public VttAssetFolderService(Path gameDirectory, String tabletopId) {
        if (gameDirectory == null) {
            throw new IllegalArgumentException("Game directory cannot be null");
        }
        this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
        this.tabletopId = sanitizeSegment(tabletopId, "default");
        for (Section section : Section.values()) {
            folders.put(section, List.of());
            itemFolders.put(section, Map.of());
        }
        refresh();
    }

    public synchronized void refresh() {
        for (Section section : Section.values()) {
            Path root = root(section);
            try {
                Files.createDirectories(root);
                folders.put(section, scanFolders(root));
                itemFolders.put(section, scanItems(section, root));
            } catch (IOException exception) {
                VTT.LOGGER.error("Failed to scan VTT {} folders", section, exception);
                folders.put(section, List.of());
                itemFolders.put(section, Map.of());
            }
        }
    }

    public synchronized void applyMetadata(
            VttTabletop tabletop,
            MapDefinitionRegistry maps,
            TokenDefinitionRegistry tokens
    ) {
        if (tabletop != null) {
            for (Section section : Section.values()) {
                tabletop.setCatalogFolders(section.name(), folders(section));
            }
            itemFolders.getOrDefault(Section.SCENES, Map.of())
                    .forEach(tabletop::setSceneFolder);
        }
        if (maps != null) {
            itemFolders.getOrDefault(Section.MAPS, Map.of())
                    .forEach(maps::setFolder);
        }
        if (tokens != null) {
            itemFolders.getOrDefault(Section.TOKENS, Map.of())
                    .forEach(tokens::setFolder);
        }
    }

    public synchronized List<String> folders(Section section) {
        return List.copyOf(folders.getOrDefault(section, List.of()));
    }

    public synchronized String itemFolder(Section section, String itemId) {
        if (section == null || itemId == null) return "";
        return itemFolders.getOrDefault(section, Map.of()).getOrDefault(itemId, "");
    }

    public synchronized boolean createFolder(
            Section section, String parentFolder, String requestedName
    ) {
        Path parent = resolveFolder(section, parentFolder, true);
        String name = sanitizeFolderName(requestedName);
        if (parent == null || name == null) return false;
        Path target = parent.resolve(name).normalize();
        if (!isInsideRoot(section, target) || Files.exists(target)) return false;
        try {
            Files.createDirectory(target);
            refresh();
            return true;
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to create VTT asset folder: {}", target, exception);
            return false;
        }
    }

    public synchronized String renameFolder(
            Section section, String folder, String requestedName
    ) {
        Path source = resolveFolder(section, folder, false);
        String name = sanitizeFolderName(requestedName);
        if (source == null || name == null) return null;
        Path target = source.getParent().resolve(name).normalize();
        if (!isInsideRoot(section, target) || Files.exists(target)) return null;
        try {
            move(source, target);
            refresh();
            return relative(section, target);
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to rename VTT asset folder: {}", source, exception);
            return null;
        }
    }

    public synchronized String moveFolder(
            Section section, String folder, String targetParentFolder
    ) {
        Path source = resolveFolder(section, folder, false);
        Path parent = resolveFolder(section, targetParentFolder, true);
        if (source == null || parent == null || parent.equals(source)
                || parent.startsWith(source)) return null;
        Path target = parent.resolve(source.getFileName()).normalize();
        if (!isInsideRoot(section, target) || Files.exists(target)) return null;
        try {
            move(source, target);
            refresh();
            return relative(section, target);
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to move VTT asset folder: {}", source, exception);
            return null;
        }
    }

    public synchronized boolean moveItem(
            Section section, String itemId, String targetFolder
    ) {
        Path source = findItemFile(section, itemId);
        Path parent = resolveFolder(section, targetFolder, true);
        if (source == null || parent == null || source.getParent().equals(parent)) return false;
        Path target = parent.resolve(source.getFileName()).normalize();
        if (!isInsideRoot(section, target) || Files.exists(target)) return false;
        try {
            move(source, target);
            refresh();
            return true;
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to move VTT asset item: {}", source, exception);
            return false;
        }
    }

    public synchronized boolean deleteEmptyFolder(Section section, String folder) {
        Path target = resolveFolder(section, folder, false);
        if (target == null) return false;
        try (Stream<Path> children = Files.list(target)) {
            if (children.findAny().isPresent()) return false;
        } catch (IOException exception) {
            return false;
        }
        try {
            Files.delete(target);
            refresh();
            return true;
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to delete VTT asset folder: {}", target, exception);
            return false;
        }
    }

    private List<String> scanFolders(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root, MAX_DEPTH)) {
            return paths.filter(Files::isDirectory)
                    .filter(path -> !path.equals(root))
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
    }

    private Map<String, String> scanItems(Section section, Path root) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(root, MAX_DEPTH)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase().endsWith(".json")).toList()) {
                String id = readId(section, file);
                if (id != null && !id.isBlank()) {
                    result.put(id, relative(section, file.getParent()));
                }
            }
        }
        return Map.copyOf(result);
    }

    private String readId(Section section, Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            String field = switch (section) {
                case SCENES -> "id";
                case MAPS -> "mapDefinitionId";
                case TOKENS -> "tokenDefinitionId";
            };
            return json.has(field) && json.get(field).isJsonPrimitive()
                    ? json.get(field).getAsString() : null;
        } catch (RuntimeException | IOException ignored) {
            return null;
        }
    }

    private Path findItemFile(Section section, String itemId) {
        if (section == null || itemId == null || itemId.isBlank()) return null;
        Path root = root(section);
        try (Stream<Path> paths = Files.walk(root, MAX_DEPTH)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase().endsWith(".json")).toList()) {
                if (itemId.equals(readId(section, file))) return file;
            }
        } catch (IOException exception) {
            VTT.LOGGER.warn("Could not locate VTT asset item {}", itemId, exception);
        }
        return null;
    }

    private Path resolveFolder(Section section, String relative, boolean allowRoot) {
        if (section == null) return null;
        Path root = root(section);
        String normalized = normalizeRelative(relative);
        if (normalized == null) return null;
        Path resolved = normalized.isBlank()
                ? root : root.resolve(normalized).normalize();
        if (!isInsideRoot(section, resolved)
                || !Files.isDirectory(resolved)
                || !allowRoot && resolved.equals(root)) return null;
        return resolved;
    }

    private boolean isInsideRoot(Section section, Path path) {
        return section != null && path != null
                && path.toAbsolutePath().normalize().startsWith(root(section));
    }

    private String relative(Section section, Path path) {
        Path root = root(section);
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) return "";
        String value = root.relativize(normalized).toString().replace('\\', '/');
        return ".".equals(value) ? "" : value;
    }

    private Path root(Section section) {
        Path created = gameDirectory.resolve("config/vtt_assets/created");
        return (switch (section) {
            case SCENES -> created.resolve("tabletops").resolve(tabletopId).resolve("scenes");
            case MAPS -> created.resolve("maps");
            case TOKENS -> created.resolve("tokens");
        }).toAbsolutePath().normalize();
    }

    private String normalizeRelative(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim().replace('\\', '/').replaceAll("/+", "/")
                .replaceAll("^/+|/+$", "");
        if (normalized.isBlank()) return "";
        String[] parts = normalized.split("/");
        if (parts.length > MAX_DEPTH) return null;
        for (String part : parts) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)
                    || part.matches(".*[<>:\"|?*\\p{Cntrl}].*")) return null;
        }
        return normalized;
    }

    private String sanitizeFolderName(String value) {
        if (value == null) return null;
        String name = value.trim().replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "_");
        name = name.replaceAll("[. ]+$", "");
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) return null;
        return name.length() > MAX_NAME_LENGTH
                ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    private static String sanitizeSegment(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toLowerCase()
                .replaceAll("[^a-z0-9_-]", "_");
        return normalized.isBlank() ? fallback : normalized;
    }

    private void move(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }

    public enum Section { SCENES, MAPS, TOKENS }
}
