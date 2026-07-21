package com.petrick.vtt.feature.tabletop;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

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

    private int schemaVersion = 1;

    private String id;

    private String displayName;

    private String activeSceneId;

    private final List<String> sceneIds = new ArrayList<>();

    /** Lightweight scene metadata used by the scene list without loading every scene JSON. */
    private Map<String, String> sceneDisplayNames = new LinkedHashMap<>();

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
        sceneDisplayNames().putIfAbsent(sceneId, sceneId);

        if (activeSceneId == null || activeSceneId.isBlank()) {
            activeSceneId = sceneId;
        }
    }

    public void removeSceneId(String sceneId) {
        if (sceneId == null || sceneId.isBlank()) {
            return;
        }

        sceneIds.remove(sceneId);
        sceneDisplayNames().remove(sceneId);

        if (sceneId.equals(activeSceneId)) {
            activeSceneId = sceneIds.isEmpty()
                    ? null
                    : sceneIds.get(0);
        }
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getSceneDisplayName(String sceneId) {
        if (sceneId == null) return "";
        return sceneDisplayNames().getOrDefault(sceneId, sceneId);
    }

    public void setSceneDisplayName(String sceneId, String displayName) {
        if (sceneId == null || sceneId.isBlank()) return;
        String normalized = displayName == null || displayName.isBlank()
                ? sceneId : displayName.trim();
        sceneDisplayNames().put(sceneId, normalized);
    }

    private Map<String, String> sceneDisplayNames() {
        if (sceneDisplayNames == null) sceneDisplayNames = new LinkedHashMap<>();
        return sceneDisplayNames;
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
