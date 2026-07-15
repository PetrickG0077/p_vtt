package com.petrick.vtt.feature.tabletop.vision;

import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.selection.SelectionManager;
import com.petrick.vtt.feature.tabletop.VttScene;

import java.util.ArrayList;
import java.util.List;

/** Resolves persistent vision source first and editor selection as a debug fallback. */
public final class SceneVisionSourceResolver {
    public CanvasObject resolve(
            VttScene tabletopScene, CanvasScene canvasScene, SelectionManager selectionManager
    ) {
        List<CanvasObject> sources = resolveAll(tabletopScene, canvasScene, selectionManager);
        return sources.isEmpty() ? null : sources.getFirst();
    }

    public List<CanvasObject> resolveAll(
            VttScene tabletopScene, CanvasScene canvasScene, SelectionManager selectionManager
    ) {
        return resolveAll(tabletopScene, canvasScene, selectionManager, null);
    }

    public List<CanvasObject> resolveAll(
            VttScene tabletopScene, CanvasScene canvasScene,
            SelectionManager selectionManager, String ownerId
    ) {
        if (canvasScene == null) return List.of();
        if (ownerId != null && !ownerId.isBlank()) {
            List<CanvasObject> ownedSources = new ArrayList<>();
            if (tabletopScene != null) {
                for (var sceneObject : tabletopScene.getObjects()) {
                    if (sceneObject == null || !ownerId.equals(sceneObject.getOwnerId())) continue;
                    CanvasObject owned = canvasScene.findObjectById(sceneObject.getId());
                    if (isValid(owned)) ownedSources.add(owned);
                }
            }
            return List.copyOf(ownedSources);
        }
        List<CanvasObject> persistentSources = new ArrayList<>();
        if (tabletopScene != null) {
            for (String sourceId : tabletopScene.getVisionSourceObjectIds()) {
                CanvasObject persistent = canvasScene.findObjectById(sourceId);
                if (isValid(persistent)) persistentSources.add(persistent);
            }
        }
        if (!persistentSources.isEmpty()) return List.copyOf(persistentSources);
        if (selectionManager == null || selectionManager.getSelectedObjectIds().size() != 1) return List.of();
        CanvasObject selected = canvasScene.findObjectById(
                selectionManager.getSelectedObjectIds().iterator().next());
        return isValid(selected) ? List.of(selected) : List.of();
    }

    private boolean isValid(CanvasObject object) {
        return object != null && object.visible() && object.hasSourceTokenDefinition();
    }
}
