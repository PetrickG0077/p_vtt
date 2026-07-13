package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttTabletop;
import com.petrick.vtt.feature.tabletop.persistence.TabletopStorage;
import com.petrick.vtt.feature.tabletop.persistence.TabletopStoragePaths;
import com.petrick.vtt.network.payload.VttSceneSnapshotPayload;
import net.neoforged.fml.loading.FMLPaths;

public final class VttServerTabletopState {

    private static final Gson GSON = new GsonBuilder().create();
    private static VttServerTabletopState instance;

    private final VttTabletop tabletop;
    private final VttScene activeScene;

    private VttServerTabletopState() {
        TabletopStoragePaths paths = new TabletopStoragePaths(FMLPaths.GAMEDIR.get());
        paths.ensureBaseFoldersExist();
        paths.ensureTabletopFoldersExist("default");

        TabletopStorage storage = new TabletopStorage(paths);
        this.tabletop = storage.loadOrCreateDefaultTabletop();
        this.activeScene = storage.loadOrCreateActiveScene(tabletop);
    }

    public static synchronized VttServerTabletopState get() {
        if (instance == null) {
            instance = new VttServerTabletopState();
        }
        return instance;
    }

    public VttSceneSnapshotPayload createSnapshotPayload() {
        return new VttSceneSnapshotPayload(GSON.toJson(tabletop), GSON.toJson(activeScene));
    }

    public VttScene activeScene() {
        return activeScene;
    }
}
