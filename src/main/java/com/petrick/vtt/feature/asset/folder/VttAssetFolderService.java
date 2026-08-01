package com.petrick.vtt.feature.asset.folder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** Safe physical folder operations for scene, map and token definition catalogs. */
public final class VttAssetFolderService {
    private static final int MAX_DEPTH = 16;
    private static final int MAX_NAME_LENGTH = 64;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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
            tabletop.getSceneIds().forEach(sceneId ->
                    tabletop.setSceneFolder(sceneId, ""));
            itemFolders.getOrDefault(Section.SCENES, Map.of())
                    .forEach(tabletop::setSceneFolder);
        }
        if (maps != null) {
            maps.clearFolders();
            itemFolders.getOrDefault(Section.MAPS, Map.of())
                    .forEach(maps::setFolder);
        }
        if (tokens != null) {
            tokens.clearFolders();
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

    /** Duplicates a complete folder tree while assigning new public IDs to JSON assets. */
    public synchronized FolderDuplicateResult duplicateFolder(
            Section section, String folder
    ) {
        Path source = resolveFolder(section, folder, false);
        if (source == null) return null;
        Path target = uniqueCopyFolder(section, source);
        if (target == null) return null;

        Set<String> reservedIds = new HashSet<>(
                itemFolders.getOrDefault(section, Map.of()).keySet());
        List<DuplicatedScene> scenes = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(source, MAX_DEPTH)) {
            for (Path path : paths.sorted().toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative).normalize();
                if (!isInsideRoot(section, destination)) throw new IOException(
                        "Duplicate destination escaped asset root");
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                    continue;
                }
                Files.createDirectories(destination.getParent());
                if (path.getFileName().toString().toLowerCase().endsWith(".json")) {
                    JsonObject json;
                    try (Reader reader = Files.newBufferedReader(path)) {
                        json = JsonParser.parseReader(reader).getAsJsonObject();
                    }
                    String idField = switch (section) {
                        case SCENES -> "id";
                        case MAPS -> "mapDefinitionId";
                        case TOKENS -> "tokenDefinitionId";
                    };
                    if (json.has(idField) && json.get(idField).isJsonPrimitive()) {
                        String newId = uniqueCopyId(
                                json.get(idField).getAsString(), reservedIds);
                        json.addProperty(idField, newId);
                        String displayName = json.has("displayName")
                                && json.get("displayName").isJsonPrimitive()
                                ? json.get("displayName").getAsString() : newId;
                        String copiedDisplayName = displayName + " Copy";
                        json.addProperty("displayName", copiedDisplayName);
                        if (section == Section.SCENES) {
                            String sceneFileName = newId.trim().toLowerCase()
                                    .replace('\\', '/')
                                    .replaceAll("[^a-z0-9/_-]", "_") + ".json";
                            destination = destination.getParent()
                                    .resolve(sceneFileName).normalize();
                            Files.createDirectories(destination.getParent());
                            scenes.add(new DuplicatedScene(
                                    newId,
                                    copiedDisplayName,
                                    relative(section, destination.getParent())));
                        }
                    }
                    Files.writeString(destination, GSON.toJson(json));
                } else {
                    Files.copy(path, destination);
                }
            }
            refresh();
            return new FolderDuplicateResult(relative(section, target), scenes);
        } catch (RuntimeException | IOException exception) {
            deleteCreatedTree(target);
            refresh();
            VTT.LOGGER.error("Failed to duplicate VTT asset folder: {}", source, exception);
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

    /** Moves a same-section selection as one validated filesystem operation. */
    public synchronized boolean moveSelection(
            Section section,
            String encodedSources,
            String targetFolder
    ) {
        Path parent = resolveFolder(section, targetFolder, true);
        if (parent == null || encodedSources == null || encodedSources.isBlank()) {
            return false;
        }

        List<Path> sources = new ArrayList<>();
        Set<Path> uniqueSources = new HashSet<>();
        for (String encoded : encodedSources.split("\\R")) {
            if (encoded.length() < 3 || encoded.charAt(1) != ':') return false;
            Path source = switch (encoded.charAt(0)) {
                case 'F' -> resolveFolder(section, encoded.substring(2), false);
                case 'I' -> findItemFile(section, encoded.substring(2));
                default -> null;
            };
            if (source == null || !uniqueSources.add(source)) return false;
            if (Files.isDirectory(source)
                    && (parent.equals(source) || parent.startsWith(source))) return false;
            if (source.getParent().equals(parent)) return false;
            sources.add(source);
        }
        if (sources.isEmpty() || sources.size() > 256) return false;

        Set<Path> destinations = new HashSet<>();
        for (Path source : sources) {
            Path destination = parent.resolve(source.getFileName()).normalize();
            if (!isInsideRoot(section, destination)
                    || Files.exists(destination)
                    || !destinations.add(destination)) return false;
        }

        List<Path> movedSources = new ArrayList<>();
        try {
            for (Path source : sources) {
                move(source, parent.resolve(source.getFileName()).normalize());
                movedSources.add(source);
            }
            refresh();
            return true;
        } catch (IOException exception) {
            Collections.reverse(movedSources);
            for (Path original : movedSources) {
                Path current = parent.resolve(original.getFileName()).normalize();
                try {
                    if (Files.exists(current) && !Files.exists(original)) {
                        move(current, original);
                    }
                } catch (IOException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            refresh();
            VTT.LOGGER.error("Failed to move VTT asset selection", exception);
            return false;
        }
    }

    /**
     * Deletes selected JSON items and flattens selected folders as one rollback-capable
     * filesystem operation. Folder contents are moved to the folder's parent.
     */
    public synchronized boolean deleteSelection(Section section, String encodedSources) {
        List<SelectionEntry> entries = decodeSelection(encodedSources);
        if (section == null || entries.size() < 2) return false;
        Path root = root(section);
        List<Path> itemSources = new ArrayList<>();
        List<Path> folderSources = new ArrayList<>();
        Set<Path> unique = new HashSet<>();
        for (SelectionEntry entry : entries) {
            Path source = entry.folder()
                    ? resolveFolder(section, entry.source(), false)
                    : findItemFile(section, entry.source());
            if (source == null || !unique.add(source)) return false;
            if (entry.folder()) folderSources.add(source);
            else itemSources.add(source);
        }

        Set<Path> selectedItems = Set.copyOf(itemSources);
        Set<Path> destinations = new HashSet<>();
        try {
            for (Path folder : folderSources) {
                try (Stream<Path> children = Files.list(folder)) {
                    for (Path child : children.toList()) {
                        Path destination = folder.getParent()
                                .resolve(child.getFileName()).normalize();
                        if (!isInsideRoot(section, destination)
                                || !destinations.add(destination)
                                || Files.exists(destination)
                                && !selectedItems.contains(destination)) return false;
                    }
                }
            }
        } catch (IOException exception) {
            return false;
        }

        Path staging = root.resolve(".delete_staging_" + UUID.randomUUID()).normalize();
        List<StagedItem> stagedItems = new ArrayList<>();
        List<FlattenedFolder> flattenedFolders = new ArrayList<>();
        try {
            Files.createDirectory(staging);
            for (int index = 0; index < itemSources.size(); index++) {
                Path source = itemSources.get(index);
                Path staged = staging.resolve(index + "_" + source.getFileName()).normalize();
                move(source, staged);
                stagedItems.add(new StagedItem(source, staged));
            }
            for (Path folder : folderSources) {
                List<MovedChild> movedChildren = new ArrayList<>();
                try {
                    try (Stream<Path> children = Files.list(folder)) {
                        for (Path child : children.toList()) {
                            Path destination = folder.getParent()
                                    .resolve(child.getFileName()).normalize();
                            move(child, destination);
                            movedChildren.add(new MovedChild(child, destination));
                        }
                    }
                    Files.delete(folder);
                    flattenedFolders.add(new FlattenedFolder(folder, movedChildren));
                } catch (IOException exception) {
                    Collections.reverse(movedChildren);
                    for (MovedChild child : movedChildren) {
                        if (Files.exists(child.destination())) {
                            move(child.destination(), child.original());
                        }
                    }
                    throw exception;
                }
            }
            deleteCreatedTree(staging);
            refresh();
            return true;
        } catch (IOException exception) {
            Collections.reverse(flattenedFolders);
            for (FlattenedFolder flattened : flattenedFolders) {
                try {
                    Files.createDirectories(flattened.folder());
                    List<MovedChild> children = new ArrayList<>(flattened.children());
                    Collections.reverse(children);
                    for (MovedChild child : children) {
                        if (Files.exists(child.destination())) {
                            move(child.destination(), child.original());
                        }
                    }
                } catch (IOException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            Collections.reverse(stagedItems);
            for (StagedItem staged : stagedItems) {
                try {
                    if (Files.exists(staged.staged())) move(staged.staged(), staged.original());
                } catch (IOException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            deleteCreatedTree(staging);
            refresh();
            VTT.LOGGER.error("Failed to delete VTT asset selection", exception);
            return false;
        }
    }

    public static List<SelectionEntry> decodeSelection(String encodedSources) {
        if (encodedSources == null || encodedSources.isBlank()) return List.of();
        List<SelectionEntry> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (String encoded : encodedSources.split("\\R")) {
            if (encoded.length() < 3 || encoded.charAt(1) != ':') return List.of();
            boolean folder = encoded.charAt(0) == 'F';
            if (!folder && encoded.charAt(0) != 'I') return List.of();
            String source = encoded.substring(2);
            if (source.isBlank() || !unique.add(encoded)) return List.of();
            result.add(new SelectionEntry(folder, source));
        }
        return result.size() > 256 ? List.of() : List.copyOf(result);
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

    public synchronized FolderInspection inspectFolder(Section section, String folder) {
        Path target = resolveFolder(section, folder, false);
        if (target == null) return FolderInspection.missing(folder);

        int folderCount = 0;
        int itemCount = 0;
        try (Stream<Path> descendants = Files.walk(target, MAX_DEPTH)) {
            for (Path path : descendants.filter(path -> !path.equals(target)).toList()) {
                if (Files.isDirectory(path)) {
                    folderCount++;
                } else {
                    itemCount++;
                }
            }
        } catch (IOException exception) {
            VTT.LOGGER.warn("Could not inspect VTT asset folder: {}", target, exception);
            return FolderInspection.missing(folder);
        }

        List<String> conflicts = new ArrayList<>();
        Path parent = target.getParent();
        try (Stream<Path> children = Files.list(target)) {
            for (Path child : children.toList()) {
                Path destination = parent.resolve(child.getFileName()).normalize();
                if (Files.exists(destination)) {
                    conflicts.add(child.getFileName().toString());
                }
            }
        } catch (IOException exception) {
            VTT.LOGGER.warn("Could not inspect VTT asset folder children: {}", target, exception);
            return FolderInspection.missing(folder);
        }
        conflicts.sort(String.CASE_INSENSITIVE_ORDER);
        return new FolderInspection(
                true,
                relative(section, target),
                relative(section, parent),
                folderCount,
                itemCount,
                conflicts);
    }

    /**
     * Moves every direct child to the source folder's parent and removes the now-empty
     * folder. Existing destinations are rejected before any move begins. If an I/O
     * failure happens mid-operation, already moved children are moved back when possible.
     */
    public synchronized boolean moveContentsToParentAndDelete(
            Section section, String folder
    ) {
        Path source = resolveFolder(section, folder, false);
        if (source == null) return false;
        FolderInspection inspection = inspectFolder(section, folder);
        if (!inspection.exists() || inspection.hasConflicts()) return false;
        if (inspection.empty()) return deleteEmptyFolder(section, folder);

        Path parent = source.getParent();
        List<Path> children;
        try (Stream<Path> stream = Files.list(source)) {
            children = stream.sorted((left, right) ->
                    left.getFileName().toString().compareToIgnoreCase(
                            right.getFileName().toString())).toList();
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to list VTT asset folder: {}", source, exception);
            return false;
        }

        for (Path child : children) {
            Path destination = parent.resolve(child.getFileName()).normalize();
            if (!isInsideRoot(section, destination) || Files.exists(destination)) {
                return false;
            }
        }

        List<Path> moved = new ArrayList<>();
        try {
            for (Path child : children) {
                move(child, parent.resolve(child.getFileName()).normalize());
                moved.add(child);
            }
            Files.delete(source);
            refresh();
            return true;
        } catch (IOException exception) {
            Collections.reverse(moved);
            for (Path original : moved) {
                Path current = parent.resolve(original.getFileName()).normalize();
                try {
                    if (Files.exists(current) && !Files.exists(original)) {
                        move(current, original);
                    }
                } catch (IOException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            refresh();
            VTT.LOGGER.error(
                    "Failed to move contents and delete VTT asset folder: {}",
                    source, exception);
            return false;
        }
    }

    private Path uniqueCopyFolder(Section section, Path source) {
        Path parent = source.getParent();
        String originalName = source.getFileName().toString();
        for (int copyIndex = 1; copyIndex <= 999; copyIndex++) {
            String suffix = copyIndex == 1 ? " Copy" : " Copy " + copyIndex;
            String candidateName = sanitizeFolderName(originalName + suffix);
            if (candidateName == null) return null;
            Path candidate = parent.resolve(candidateName).normalize();
            if (isInsideRoot(section, candidate) && !Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String uniqueCopyId(String originalId, Set<String> reservedIds) {
        String base = originalId == null || originalId.isBlank()
                ? "asset_copy" : originalId + "_copy";
        String candidate = base;
        int copyIndex = 2;
        while (!reservedIds.add(candidate)) {
            candidate = base + "_" + copyIndex++;
        }
        return candidate;
    }

    private void deleteCreatedTree(Path target) {
        if (target == null || !Files.exists(target)) return;
        try (Stream<Path> paths = Files.walk(target, MAX_DEPTH)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException rollbackException) {
            VTT.LOGGER.error(
                    "Failed to clean incomplete duplicated asset folder: {}",
                    target, rollbackException);
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

    public record FolderInspection(
            boolean exists,
            String folder,
            String parentFolder,
            int folderCount,
            int itemCount,
            List<String> conflicts
    ) {
        public FolderInspection {
            folder = folder == null ? "" : folder;
            parentFolder = parentFolder == null ? "" : parentFolder;
            conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        }

        public static FolderInspection missing(String folder) {
            return new FolderInspection(false, folder, "", 0, 0, List.of());
        }

        public boolean empty() {
            return folderCount == 0 && itemCount == 0;
        }

        public boolean hasConflicts() {
            return !conflicts.isEmpty();
        }
    }

    public record FolderDuplicateResult(
            String folder,
            List<DuplicatedScene> scenes
    ) {
        public FolderDuplicateResult {
            folder = folder == null ? "" : folder;
            scenes = scenes == null ? List.of() : List.copyOf(scenes);
        }
    }

    public record DuplicatedScene(String id, String displayName, String folder) {}
    public record SelectionEntry(boolean folder, String source) {}
    private record StagedItem(Path original, Path staged) {}
    private record MovedChild(Path original, Path destination) {}
    private record FlattenedFolder(Path folder, List<MovedChild> children) {
        private FlattenedFolder {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }
}
