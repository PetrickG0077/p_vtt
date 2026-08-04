package com.petrick.vtt.feature.attachment;

/** A reusable visual state of an attachment definition. */
public record AttachmentStateDefinition(
        String id, String displayName, String assetId, boolean visible, int tintColorRgb
) {
    public AttachmentStateDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("State id cannot be blank");
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        assetId = assetId == null || assetId.isBlank() ? null : assetId.trim();
        tintColorRgb &= 0x00FFFFFF;
    }

    public boolean hasImage() { return assetId != null; }
}
