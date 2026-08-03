package com.petrick.vtt.feature.token;

import com.petrick.vtt.feature.tabletop.VttLight;
import com.petrick.vtt.feature.tabletop.VttAttachmentAnchor;

import java.util.List;

/** Reusable attachment template stored inside a token state definition preset. */
public record TokenStateAttachmentPreset(
        String definitionId,
        String displayName,
        boolean visible,
        boolean flippedHorizontally,
        int tintColorRgb,
        boolean followPosition,
        boolean followRotation,
        boolean followScale,
        boolean flipOffset,
        VttAttachmentAnchor anchor,
        double offsetX,
        double offsetY,
        double rotationOffsetDegrees,
        double scaleMultiplierX,
        double scaleMultiplierY,
        List<VttLight> lights
) {
    public TokenStateAttachmentPreset {
        if (definitionId == null || definitionId.isBlank()) {
            throw new IllegalArgumentException("Attachment preset definition id cannot be blank");
        }
        displayName = displayName == null || displayName.isBlank()
                ? "Attachment" : displayName;
        tintColorRgb &= 0x00FFFFFF;
        anchor = anchor == null ? VttAttachmentAnchor.CUSTOM : anchor;
        lights = lights == null ? List.of() : List.copyOf(lights);
    }
}
