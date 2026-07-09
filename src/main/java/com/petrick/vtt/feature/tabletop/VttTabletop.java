package com.petrick.vtt.feature.tabletop;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa uma campanha/mesa maior.
 *
 * Um Tabletop contém várias cenas/mapas.
 *
 * Exemplo:
 * - Campanha: "A primeira missão"
 * - Scenes:
 *   - cidade
 *   - casa_interior
 *   - caverna
 */
public final class VttTabletop {

    private String id;

    private String displayName;

    private String activeSceneId;

    private final List<String> sceneIds = new ArrayList<>();

    public VttTabletop() {
        this("default", "Default Tabletop");
    }

    public VttTabletop(String id, String displayName) {
        this.id = normalizeId(id);
        this.displayName = normalizeDisplayName(displayName, this.id);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = normalizeId(id);
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = normalizeDisplayName(displayName, id);
    }

    public String getActiveSceneId() {
        return activeSceneId;
    }

    public void setActiveSceneId(String activeSceneId) {
        this.activeSceneId = activeSceneId;

        if (activeSceneId != null && !activeSceneId.isBlank()) {
            addSceneId(activeSceneId);
        }
    }

    public List<String> getSceneIds() {
        return sceneIds;
    }

    public void addSceneId(String sceneId) {
        if (sceneId == null || sceneId.isBlank()) {
            return;
        }

        if (!sceneIds.contains(sceneId)) {
            sceneIds.add(sceneId);
        }

        if (activeSceneId == null || activeSceneId.isBlank()) {
            activeSceneId = sceneId;
        }
    }

    public void removeSceneId(String sceneId) {
        if (sceneId == null || sceneId.isBlank()) {
            return;
        }

        sceneIds.remove(sceneId);

        if (sceneId.equals(activeSceneId)) {
            activeSceneId = sceneIds.isEmpty()
                    ? null
                    : sceneIds.get(0);
        }
    }

    private String normalizeId(String value) {
        if (value == null || value.isBlank()) {
            return "default";
        }

        return value
                .trim()
                .toLowerCase()
                .replace('\\', '/')
                .replaceAll("[^a-z0-9/_-]", "_");
    }

    private String normalizeDisplayName(String value, String fallbackId) {
        if (value == null || value.isBlank()) {
            return fallbackId == null || fallbackId.isBlank()
                    ? "Untitled Tabletop"
                    : fallbackId;
        }

        return value.trim();
    }
}