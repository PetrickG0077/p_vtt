package com.petrick.vtt.feature.tabletop.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttTabletop;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.Predicate;

/**
 * Serviço responsável por salvar e carregar Tabletop/Scenes em JSON.
 *
 * Nesta primeira versão:
 * - salva tabletop.json;
 * - salva scenes/<sceneId>.json;
 * - cria uma tabletop/cena default se não existir nada ainda.
 */
public final class TabletopStorage {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private final TabletopStoragePaths paths;

    public TabletopStorage(TabletopStoragePaths paths) {
        if (paths == null) {
            throw new IllegalArgumentException("TabletopStoragePaths cannot be null");
        }

        this.paths = paths;
    }

    public VttTabletop loadOrCreateDefaultTabletop() {
        paths.ensureTabletopFoldersExist("default");

        Path tabletopFile = paths.tabletopFile("default");

        if (Files.exists(tabletopFile)) {
            VttTabletop loadedTabletop = loadTabletop("default");

            if (loadedTabletop != null) {
                return loadedTabletop;
            }
        }

        VttTabletop tabletop = new VttTabletop("default", "Default Tabletop");
        tabletop.addSceneId("default_scene");
        tabletop.setActiveSceneId("default_scene");

        saveTabletop(tabletop);

        VttScene defaultScene = new VttScene("default_scene", "Default Scene");
        saveScene(tabletop.getId(), defaultScene);

        return tabletop;
    }

    public VttScene loadOrCreateActiveScene(VttTabletop tabletop) {
        if (tabletop == null) {
            VTT.LOGGER.warn("Cannot load active scene because tabletop is null.");
            return new VttScene("default_scene", "Default Scene");
        }

        String sceneId = tabletop.getActiveSceneId();

        if (sceneId == null || sceneId.isBlank()) {
            sceneId = "default_scene";
            tabletop.setActiveSceneId(sceneId);
            saveTabletop(tabletop);
        }

        VttScene loadedScene = loadScene(tabletop.getId(), sceneId);

        if (loadedScene != null) {
            return loadedScene;
        }

        VttScene createdScene = new VttScene(sceneId, sceneId);
        saveScene(tabletop.getId(), createdScene);

        return createdScene;
    }

    public synchronized VttTabletop loadTabletop(String tabletopId) {
        Path file = paths.tabletopFile(tabletopId);
        return loadWithRecovery(file, VttTabletop.class,
                this::validTabletop, "tabletop");
    }

    public synchronized VttScene loadScene(String tabletopId, String sceneId) {
        Path file = paths.sceneFile(tabletopId, sceneId);
        VttScene scene = loadWithRecovery(file, VttScene.class, this::validScene, "scene");
        if (scene != null) {
            scene.getWalls().forEach(wall -> {
                if (wall != null) wall.normalizeLegacyGeometry();
            });
            scene.getDoors().forEach(door -> {
                if (door != null) {
                    door.getTransform();
                    door.getSize();
                }
            });
            scene.getFogOfWar().getRevealedAreas().forEach(area -> {
                if (area != null) {
                    area.getTransform();
                    area.getSize();
                }
            });
            scene.getFogOfWar().getHiddenAreas().forEach(area -> {
                if (area != null) {
                    area.getTransform();
                    area.getSize();
                }
            });

        }
        return scene;
    }

    public synchronized boolean saveTabletop(VttTabletop tabletop) {
        if (tabletop == null) {
            return false;
        }

        paths.ensureTabletopFoldersExist(tabletop.getId());

        Path file = paths.tabletopFile(tabletop.getId());

        return writeAtomically(file, tabletop, VttTabletop.class,
                this::validTabletop, "tabletop");
    }

    public synchronized boolean saveScene(String tabletopId, VttScene scene) {
        if (tabletopId == null || tabletopId.isBlank()) {
            return false;
        }

        if (scene == null) {
            return false;
        }

        paths.ensureTabletopFoldersExist(tabletopId);

        Path file = paths.sceneFile(tabletopId, scene.getId());

        return writeAtomically(file, scene, VttScene.class, this::validScene, "scene");
    }

    public synchronized boolean deleteScene(String tabletopId, String sceneId) {
        if (tabletopId == null || tabletopId.isBlank() || sceneId == null || sceneId.isBlank()) {
            return false;
        }
        Path file = paths.sceneFile(tabletopId, sceneId);
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(temporaryFile(file));
            Files.deleteIfExists(backupFile(file));
            Files.deleteIfExists(backupTemporaryFile(file));
            VTT.LOGGER.info("Deleted VTT scene JSON: {}", file);
            return true;
        } catch (IOException | RuntimeException exception) {
            VTT.LOGGER.error("Failed to delete VTT scene JSON: {}", file, exception);
            return false;
        }
    }

    private boolean validTabletop(VttTabletop tabletop) {
        if (tabletop == null || tabletop.getId() == null || tabletop.getId().isBlank()
                || tabletop.getSceneIds() == null || tabletop.getActiveSceneId() == null
                || tabletop.getActiveSceneId().isBlank()) return false;
        return tabletop.getSceneIds().contains(tabletop.getActiveSceneId());
    }

    private boolean validScene(VttScene scene) {
        try {
            if (scene == null || scene.getId() == null || scene.getId().isBlank()
                    || scene.getObjects() == null) return false;
            scene.getVisionSourceObjectIds();
            scene.getFogOfWar().getHiddenAreas();
            scene.getFogOfWar().getRevealedAreas();
            for (var object : scene.getObjects()) {
                if (object == null || object.getId() == null || object.getId().isBlank()
                        || object.getTransform() == null || object.getSize() == null
                        || object.getState() == null
                        || !finite(object.getTransform().getX(), object.getTransform().getY(),
                        object.getTransform().getScaleX(), object.getTransform().getScaleY(),
                        object.getTransform().getRotationDegrees(), object.getSize().getWidth(),
                        object.getSize().getHeight(), object.getVisionOuterRadius(),
                        object.getVisionInnerRadius())) return false;
            }
            for (var wall : scene.getWalls()) {
                if (wall == null || wall.getId() == null || wall.getId().isBlank()
                        || wall.getTransform() == null || wall.getSize() == null
                        || !finite(wall.getTransform().getX(), wall.getTransform().getY(),
                        wall.getTransform().getScaleX(), wall.getTransform().getScaleY(),
                        wall.getTransform().getRotationDegrees(), wall.getSize().getWidth(),
                        wall.getSize().getHeight())) return false;
            }
            for (var door : scene.getDoors()) {
                if (door == null || door.getId() == null || door.getId().isBlank()
                        || door.getTransform() == null || door.getSize() == null
                        || !finite(door.getTransform().getX(), door.getTransform().getY(),
                        door.getTransform().getScaleX(), door.getTransform().getScaleY(),
                        door.getTransform().getRotationDegrees(), door.getSize().getWidth(),
                        door.getSize().getHeight())) return false;
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private <T> boolean writeAtomically(
            Path file, T value, Class<T> type, Predicate<T> validator, String label
    ) {
        Path temporary = temporaryFile(file);
        Path backupTemporary = backupTemporaryFile(file);
        try {
            Files.createDirectories(file.getParent());
            Files.deleteIfExists(temporary);
            Files.deleteIfExists(backupTemporary);
            String json = GSON.toJson(value);
            if (parseValidated(json, type, validator) == null) {
                throw new IOException("Serialized " + label + " failed validation");
            }

            Files.writeString(temporary, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            forceFile(temporary);
            if (readValidated(temporary, type, validator) == null) {
                throw new IOException("Temporary " + label + " failed validation");
            }

            if (Files.exists(file) && readValidated(file, type, validator) != null) {
                replaceBackup(file, backupFile(file), backupTemporary, type, validator);
            }
            moveReplacing(temporary, file);
            VTT.LOGGER.info("Saved VTT {} JSON atomically: {}", label, file);
            return true;
        } catch (IOException | RuntimeException exception) {
            VTT.LOGGER.error("Failed to save VTT {} JSON atomically: {}", label, file, exception);
            deleteQuietly(temporary);
            deleteQuietly(backupTemporary);
            return false;
        }
    }

    private <T> T loadWithRecovery(
            Path file, Class<T> type, Predicate<T> validator, String label
    ) {
        Path temporary = temporaryFile(file);
        Path backup = backupFile(file);
        Path backupTemporary = backupTemporaryFile(file);
        deleteQuietly(backupTemporary);

        T primary = readValidated(file, type, validator);
        if (primary != null) {
            deleteQuietly(temporary);
            return primary;
        }
        if (Files.exists(file)) VTT.LOGGER.warn("Invalid VTT {} JSON detected: {}", label, file);

        T pending = readValidated(temporary, type, validator);
        if (pending != null && promoteRecoveryFile(temporary, file, label, "temporary")) {
            return pending;
        }
        deleteQuietly(temporary);

        T recovered = readValidated(backup, type, validator);
        if (recovered != null && restoreBackup(
                backup, file, temporary, type, validator, label)) {
            return recovered;
        }
        if (Files.exists(backup)) VTT.LOGGER.error("Invalid VTT {} backup JSON: {}", label, backup);
        return null;
    }

    private <T> void replaceBackup(
            Path source, Path backup, Path backupTemporary,
            Class<T> type, Predicate<T> validator
    ) throws IOException {
        Files.copy(source, backupTemporary, StandardCopyOption.REPLACE_EXISTING);
        forceFile(backupTemporary);
        if (readValidated(backupTemporary, type, validator) == null) {
            throw new IOException("Copied VTT backup failed validation");
        }
        moveReplacing(backupTemporary, backup);
    }

    private <T> boolean restoreBackup(
            Path backup, Path file, Path temporary,
            Class<T> type, Predicate<T> validator, String label
    ) {
        try {
            Files.copy(backup, temporary, StandardCopyOption.REPLACE_EXISTING);
            forceFile(temporary);
            if (readValidated(temporary, type, validator) == null) {
                throw new IOException("Restored VTT backup failed validation");
            }
            moveReplacing(temporary, file);
            VTT.LOGGER.warn("Recovered VTT {} JSON from backup: {}", label, backup);
            return true;
        } catch (IOException | RuntimeException exception) {
            VTT.LOGGER.error("Failed to restore VTT {} backup: {}", label, backup, exception);
            deleteQuietly(temporary);
            return false;
        }
    }

    private boolean promoteRecoveryFile(Path source, Path file, String label, String sourceName) {
        try {
            moveReplacing(source, file);
            VTT.LOGGER.warn("Recovered VTT {} JSON from valid {} file: {}",
                    label, sourceName, source);
            return true;
        } catch (IOException | RuntimeException exception) {
            VTT.LOGGER.error("Failed to recover VTT {} JSON from {}", label, source, exception);
            return false;
        }
    }

    private <T> T readValidated(Path file, Class<T> type, Predicate<T> validator) {
        if (file == null || !Files.isRegularFile(file)) return null;
        try {
            return parseValidated(Files.readString(file, StandardCharsets.UTF_8), type, validator);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private <T> T parseValidated(String json, Class<T> type, Predicate<T> validator) {
        if (json == null || json.isBlank()) return null;
        T value = GSON.fromJson(JsonParser.parseString(json), type);
        return value != null && validator.test(value) ? value : null;
    }

    private void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (UnsupportedOperationException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path temporaryFile(Path file) {
        return file.resolveSibling(file.getFileName() + ".tmp");
    }

    private Path backupFile(Path file) {
        return file.resolveSibling(file.getFileName() + ".bak");
    }

    private Path backupTemporaryFile(Path file) {
        return file.resolveSibling(file.getFileName() + ".bak.tmp");
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException | RuntimeException exception) {
            VTT.LOGGER.debug("Could not remove stale VTT temporary file: {}", file, exception);
        }
    }
}
