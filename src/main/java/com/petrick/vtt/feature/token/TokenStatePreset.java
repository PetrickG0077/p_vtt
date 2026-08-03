package com.petrick.vtt.feature.token;

import com.petrick.vtt.feature.tabletop.VttTokenStateAppearance;

import java.util.List;

/** Appearance and state-only attachments inherited by newly placed token instances. */
public record TokenStatePreset(
        VttTokenStateAppearance appearance,
        List<TokenStateAttachmentPreset> attachments
) {
    public TokenStatePreset {
        if (appearance == null) {
            throw new IllegalArgumentException("Token state preset appearance cannot be null");
        }
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
