package com.petrick.vtt.feature.attachment;

import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.tabletop.VttLight;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneObject;

import java.util.HashSet;
import java.util.Set;

/** Resolves inherited visibility without mutating the attachment or light's own enabled state. */
public final class AttachmentVisibilityResolver {
    private AttachmentVisibilityResolver() {}

    public static boolean isObjectEffectivelyVisible(
            VttScene tabletopScene, CanvasScene canvasScene, CanvasObject object
    ) {
        if (object == null || !object.visible()) return false;
        if (tabletopScene == null || canvasScene == null) return true;
        String currentId = object.id();
        Set<String> visited = new HashSet<>();
        while (currentId != null && visited.add(currentId)) {
            CanvasObject current = canvasScene.findObjectById(currentId);
            if (current == null || !current.visible()) return false;
            String lookupId = currentId;
            VttSceneObject metadata = tabletopScene.getObjects().stream()
                    .filter(candidate -> candidate != null && lookupId.equals(candidate.getId()))
                    .findFirst().orElse(null);
            if (metadata == null || !metadata.isAttachment()
                    || metadata.getAttachmentBinding() == null
                    || !metadata.getAttachmentBinding().isBound()) return true;
            currentId = metadata.getAttachmentBinding().getTargetObjectId();
        }
        // A cycle is invalid data; hiding it is safer than leaking a hidden parent.
        return false;
    }

    public static boolean isLightEffectivelyVisible(
            VttScene tabletopScene, CanvasScene canvasScene, VttLight light
    ) {
        if (light == null || !light.isEnabled()) return false;
        if (!light.isAttached()) return true;
        CanvasObject attachment = canvasScene == null ? null
                : canvasScene.findObjectById(light.getAttachedToObjectId());
        return isObjectEffectivelyVisible(tabletopScene, canvasScene, attachment);
    }
}
