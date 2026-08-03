package com.petrick.vtt.feature.attachment;

/** Reusable attachment asset. Its image is optional by design. */
public record AttachmentDefinition(
        String id,
        String displayName,
        String assetId,
        double defaultWidth,
        double defaultHeight
) {
    public static final double DEFAULT_SIZE = 32.0;

    public AttachmentDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Attachment definition id cannot be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Attachment definition name cannot be blank");
        }
        assetId = assetId == null || assetId.isBlank() ? null : assetId.trim();
        if (!Double.isFinite(defaultWidth) || !Double.isFinite(defaultHeight)
                || defaultWidth <= 0.0 || defaultHeight <= 0.0
                || defaultWidth > 16_000.0 || defaultHeight > 16_000.0) {
            throw new IllegalArgumentException("Invalid attachment default size");
        }
    }

    public boolean hasImage() {
        return assetId != null;
    }
}
