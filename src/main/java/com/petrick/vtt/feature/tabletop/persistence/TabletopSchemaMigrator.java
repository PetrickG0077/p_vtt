package com.petrick.vtt.feature.tabletop.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/** Central migration chain for persisted tabletop and scene JSON documents. */
final class TabletopSchemaMigrator {
    static final int LEGACY_SCHEMA_VERSION = 0;
    static final int CURRENT_TABLETOP_SCHEMA_VERSION = 1;
    static final int CURRENT_SCENE_SCHEMA_VERSION = 2;
    static final int CURRENT_PLAYER_PREFERENCES_SCHEMA_VERSION = 1;

    private TabletopSchemaMigrator() {}

    static MigrationResult migrate(String json, DocumentType type) {
        if (json == null || json.isBlank()) throw new JsonParseException("Empty VTT JSON");
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) throw new JsonParseException("VTT JSON root must be an object");
        JsonObject document = parsed.getAsJsonObject().deepCopy();
        int sourceVersion = readVersion(document);
        int currentVersion = currentVersion(type);
        if (sourceVersion > currentVersion) {
            throw new UnsupportedSchemaVersionException(type, sourceVersion, currentVersion);
        }

        int version = sourceVersion;
        while (version < currentVersion) {
            document = switch (type) {
                case TABLETOP -> migrateTabletop(document, version);
                case SCENE -> migrateScene(document, version);
                case PLAYER_PREFERENCES -> migratePlayerPreferences(document, version);
            };
            version++;
        }
        document.addProperty("schemaVersion", currentVersion);
        return new MigrationResult(document, sourceVersion, currentVersion,
                sourceVersion != currentVersion);
    }

    private static JsonObject migrateTabletop(JsonObject document, int sourceVersion) {
        if (sourceVersion != LEGACY_SCHEMA_VERSION) {
            throw new JsonParseException("No tabletop migration from schema " + sourceVersion);
        }
        JsonObject displayNames = document.has("sceneDisplayNames")
                && document.get("sceneDisplayNames").isJsonObject()
                ? document.getAsJsonObject("sceneDisplayNames") : new JsonObject();
        JsonElement sceneIds = document.get("sceneIds");
        if (sceneIds != null && sceneIds.isJsonArray()) {
            for (JsonElement sceneId : sceneIds.getAsJsonArray()) {
                if (sceneId != null && sceneId.isJsonPrimitive()
                        && sceneId.getAsJsonPrimitive().isString()) {
                    String id = sceneId.getAsString();
                    if (!id.isBlank() && !displayNames.has(id)) {
                        displayNames.addProperty(id, id);
                    }
                }
            }
        }
        document.add("sceneDisplayNames", displayNames);
        return document;
    }

    private static JsonObject migrateScene(JsonObject document, int sourceVersion) {
        if (sourceVersion == 1) {
            ensureArray(document, "maps");
            return document;
        }
        if (sourceVersion != LEGACY_SCHEMA_VERSION) {
            throw new JsonParseException("No scene migration from schema " + sourceVersion);
        }
        JsonArray sourceIds = document.has("visionSourceObjectIds")
                && document.get("visionSourceObjectIds").isJsonArray()
                ? document.getAsJsonArray("visionSourceObjectIds") : new JsonArray();
        JsonElement legacySource = document.get("visionSourceObjectId");
        if (legacySource != null && legacySource.isJsonPrimitive()
                && legacySource.getAsJsonPrimitive().isString()
                && !legacySource.getAsString().isBlank()
                && !containsString(sourceIds, legacySource.getAsString())) {
            sourceIds.add(legacySource.getAsString());
        }
        document.add("visionSourceObjectIds", sourceIds);
        document.remove("visionSourceObjectId");
        ensureArray(document, "objects");
        ensureArray(document, "maps");
        ensureArray(document, "walls");
        ensureArray(document, "doors");
        ensureFog(document);
        return document;
    }

    private static JsonObject migratePlayerPreferences(
            JsonObject document, int sourceVersion
    ) {
        if (sourceVersion != LEGACY_SCHEMA_VERSION) {
            throw new JsonParseException(
                    "No player preferences migration from schema " + sourceVersion);
        }
        if (!document.has("players") || !document.get("players").isJsonObject()) {
            document.add("players", new JsonObject());
        }
        return document;
    }

    private static void ensureArray(JsonObject document, String name) {
        if (!document.has(name) || !document.get(name).isJsonArray()) {
            document.add(name, new JsonArray());
        }
    }

    private static boolean containsString(JsonArray values, String expected) {
        for (JsonElement value : values) {
            if (value != null && value.isJsonPrimitive()
                    && value.getAsJsonPrimitive().isString()
                    && expected.equals(value.getAsString())) return true;
        }
        return false;
    }

    private static void ensureFog(JsonObject document) {
        JsonObject fog;
        JsonElement existing = document.get("fogOfWar");
        if (existing == null || !existing.isJsonObject()) {
            fog = new JsonObject();
            document.add("fogOfWar", fog);
        } else {
            fog = existing.getAsJsonObject();
        }
        ensureArray(fog, "revealedAreas");
        ensureArray(fog, "hiddenAreas");
    }

    private static int readVersion(JsonObject document) {
        JsonElement value = document.get("schemaVersion");
        if (value == null) return LEGACY_SCHEMA_VERSION;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("Invalid VTT schemaVersion");
        }
        try {
            double numeric = value.getAsDouble();
            int version = value.getAsInt();
            if (!Double.isFinite(numeric) || numeric != version || version < LEGACY_SCHEMA_VERSION) {
                throw new JsonParseException("Invalid VTT schemaVersion: " + value);
            }
            return version;
        } catch (NumberFormatException exception) {
            throw new JsonParseException("Invalid VTT schemaVersion", exception);
        }
    }

    private static int currentVersion(DocumentType type) {
        return switch (type) {
            case TABLETOP -> CURRENT_TABLETOP_SCHEMA_VERSION;
            case SCENE -> CURRENT_SCENE_SCHEMA_VERSION;
            case PLAYER_PREFERENCES -> CURRENT_PLAYER_PREFERENCES_SCHEMA_VERSION;
        };
    }

    enum DocumentType {
        TABLETOP,
        SCENE,
        PLAYER_PREFERENCES
    }

    record MigrationResult(
            JsonObject document, int sourceVersion, int targetVersion, boolean migrated
    ) {}

    static final class UnsupportedSchemaVersionException extends RuntimeException {
        private final DocumentType documentType;
        private final int foundVersion;
        private final int supportedVersion;

        private UnsupportedSchemaVersionException(
                DocumentType documentType, int foundVersion, int supportedVersion
        ) {
            super("Unsupported VTT " + documentType.name().toLowerCase()
                    + " schema " + foundVersion + "; maximum supported is " + supportedVersion);
            this.documentType = documentType;
            this.foundVersion = foundVersion;
            this.supportedVersion = supportedVersion;
        }

        DocumentType documentType() {
            return documentType;
        }

        int foundVersion() {
            return foundVersion;
        }

        int supportedVersion() {
            return supportedVersion;
        }
    }
}
