package com.petrick.vtt.feature.tabletop.vision;

import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.selection.SelectionManager;
import com.petrick.vtt.feature.tabletop.VttScene;

/** Resolves persistent vision source first and editor selection as a debug fallback. */
public final class SceneVisionSourceResolver {
    public CanvasObject resolve(
            VttScene tabletopScene, CanvasScene canvasScene, SelectionManager selectionManager
    ) {
        if (canvasScene == null) return null;
        if (tabletopScene != null && tabletopScene.getVisionSourceObjectId() != null) {
            CanvasObject persistent = canvasScene.findObjectById(tabletopScene.getVisionSourceObjectId());
            if (isValid(persistent)) return persistent;
        }
        if (selectionManager == null || selectionManager.getSelectedObjectIds().size() != 1) return null;
        CanvasObject selected = canvasScene.findObjectById(
                selectionManager.getSelectedObjectIds().iterator().next());
        return isValid(selected) ? selected : null;
    }

    private boolean isValid(CanvasObject object) {
        return object != null && object.visible() && object.hasSourceTokenDefinition();
    }
}
