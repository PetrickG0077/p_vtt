package com.petrick.vtt.network.payload;

import com.petrick.vtt.feature.tabletop.VttLight;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import java.util.List;

/** Complete atomic scene mutation for one placed composite attachment. */
public record VttCompositeAttachmentPlacementData(
        List<VttSceneObject> objects, List<VttLight> lights
) {
    public VttCompositeAttachmentPlacementData {
        objects = objects == null ? List.of() : List.copyOf(objects);
        lights = lights == null ? List.of() : List.copyOf(lights);
    }
}
