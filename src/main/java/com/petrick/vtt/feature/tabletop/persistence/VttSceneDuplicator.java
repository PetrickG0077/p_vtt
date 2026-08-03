package com.petrick.vtt.feature.tabletop.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.feature.tabletop.VttDoor;
import com.petrick.vtt.feature.tabletop.VttFogArea;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.feature.tabletop.VttWall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Creates a deep scene copy while replacing every scene-local entity ID. */
public final class VttSceneDuplicator {
    private static final Gson GSON = new GsonBuilder().create();

    private VttSceneDuplicator() {
    }

    public static VttScene duplicate(
            VttScene source,
            String targetSceneId,
            String targetDisplayName
    ) {
        if (source == null || targetSceneId == null || targetSceneId.isBlank()) {
            return null;
        }
        VttScene copy = GSON.fromJson(GSON.toJson(source), VttScene.class);
        if (copy == null) return null;
        copy.setId(targetSceneId);
        copy.setDisplayName(targetDisplayName);

        Map<String, String> objectIds = new HashMap<>();
        for (VttSceneObject object : copy.getObjects()) {
            if (object == null) continue;
            String previousId = object.getId();
            String nextId = nextId(object.isAttachment() ? "attachment" : "token");
            object.setId(nextId);
            if (previousId != null) objectIds.put(previousId, nextId);
        }
        for (VttSceneObject object : copy.getObjects()) {
            if (object == null || object.getAttachmentBinding() == null) continue;
            String remappedTarget = objectIds.get(
                    object.getAttachmentBinding().getTargetObjectId());
            if (remappedTarget == null) object.setAttachmentBinding(null);
            else object.getAttachmentBinding().setTargetObjectId(remappedTarget);
        }
        copy.getLights().forEach(light -> {
            if (light == null || !light.isAttached()) return;
            String remapped = objectIds.get(light.getAttachedToObjectId());
            light.setAttachedToObjectId(remapped);
        });
        List<String> visionSources = new ArrayList<>(copy.getVisionSourceObjectIds());
        copy.clearVisionSourceObjectIds();
        for (String sourceId : visionSources) {
            String remapped = objectIds.get(sourceId);
            if (remapped != null) copy.addVisionSourceObjectId(remapped);
        }

        copy.getMaps().forEach(map -> {
            if (map != null) map.setId(nextId("map"));
        });

        Map<String, String> wallIds = new HashMap<>();
        for (VttWall wall : copy.getWalls()) {
            if (wall == null) continue;
            String previousId = wall.getId();
            String nextId = nextId("wall");
            wall.setId(nextId);
            if (previousId != null) wallIds.put(previousId, nextId);
        }
        for (VttDoor door : copy.getDoors()) {
            if (door == null) continue;
            String previousWallId = door.getWallId();
            door.setId(nextId("door"));
            door.setWallId(previousWallId == null ? null : wallIds.get(previousWallId));
        }
        regenerateFogIds(copy.getFogOfWar().getHiddenAreas());
        regenerateFogIds(copy.getFogOfWar().getRevealedAreas());
        return copy;
    }

    private static void regenerateFogIds(List<VttFogArea> areas) {
        for (VttFogArea area : areas) {
            if (area != null) area.setId(nextId("fog"));
        }
    }

    private static String nextId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12);
    }
}
