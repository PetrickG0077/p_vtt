package com.petrick.vtt.feature.attachment;

import com.petrick.vtt.feature.tabletop.VttAttachmentBinding;
import com.petrick.vtt.feature.tabletop.VttLight;
import java.util.List;

/** One reusable child node inside a composite attachment definition. */
public record AttachmentCompositeNode(
        String templateNodeId,
        String definitionId,
        String displayName,
        VttAttachmentBinding binding,
        List<VttLight> lights
) {
    public static final String ROOT_ID = "$root";

    public AttachmentCompositeNode {
        if (templateNodeId == null || templateNodeId.isBlank()
                || definitionId == null || definitionId.isBlank()) {
            throw new IllegalArgumentException("Composite attachment node requires IDs");
        }
        displayName = displayName == null || displayName.isBlank()
                ? "Attachment" : displayName;
        lights = lights == null ? List.of() : List.copyOf(lights);
    }
}
