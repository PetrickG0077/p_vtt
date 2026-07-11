package com.petrick.vtt.feature.tabletop.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttTabletop;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

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

    public VttTabletop loadTabletop(String tabletopId) {
        Path file = paths.tabletopFile(tabletopId);

        if (!Files.exists(file)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            VttTabletop tabletop = GSON.fromJson(reader, VttTabletop.class);

            if (tabletop == null) {
                return null;
            }

            return tabletop;
        } catch (IOException | RuntimeException exception) {
            VTT.LOGGER.error(
                    "Failed to load VTT tabletop JSON: {}",
                    file,
                    exception
            );

            return null;
        }
    }

    public VttScene loadScene(String tabletopId, String sceneId) {
        Path file = paths.sceneFile(tabletopId, sceneId);

        if (!Files.exists(file)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            VttScene scene = GSON.fromJson(reader, VttScene.class);

            if (scene == null) {
                return null;
            }

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

            return scene;
        } catch (IOException | RuntimeException exception) {
            VTT.LOGGER.error(
                    "Failed to load VTT scene JSON: {}",
                    file,
                    exception
            );

            return null;
        }
    }

    public void saveTabletop(VttTabletop tabletop) {
        if (tabletop == null) {
            return;
        }

        paths.ensureTabletopFoldersExist(tabletop.getId());

        Path file = paths.tabletopFile(tabletop.getId());

        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(tabletop, writer);

            VTT.LOGGER.info(
                    "Saved VTT tabletop JSON: {}",
                    file
            );
        } catch (IOException | RuntimeException exception) {
            VTT.LOGGER.error(
                    "Failed to save VTT tabletop JSON: {}",
                    file,
                    exception
            );
        }
    }

    public void saveScene(String tabletopId, VttScene scene) {
        if (tabletopId == null || tabletopId.isBlank()) {
            return;
        }

        if (scene == null) {
            return;
        }

        paths.ensureTabletopFoldersExist(tabletopId);

        Path file = paths.sceneFile(tabletopId, scene.getId());

        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(scene, writer);

            VTT.LOGGER.info(
                    "Saved VTT scene JSON: {}",
                    file
            );
        } catch (IOException | RuntimeException exception) {
            VTT.LOGGER.error(
                    "Failed to save VTT scene JSON: {}",
                    file,
                    exception
            );
        }
    }
}
