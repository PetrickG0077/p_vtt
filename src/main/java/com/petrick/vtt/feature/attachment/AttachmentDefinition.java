package com.petrick.vtt.feature.attachment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import com.petrick.vtt.feature.tabletop.VttLight;

/** Reusable attachment asset. Its image is optional by design. */
public record AttachmentDefinition(
        String id,
        String displayName,
        String assetId,
        double defaultWidth,
        double defaultHeight,
        Map<String, AttachmentStateDefinition> states,
        String defaultStateId,
        List<AttachmentCompositeNode> compositeNodes,
        List<VttLight> rootLights
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
        LinkedHashMap<String, AttachmentStateDefinition> normalizedStates = new LinkedHashMap<>();
        if (states != null) states.forEach((key, state) -> {
            if (state != null) normalizedStates.put(state.id(), state);
        });
        if (normalizedStates.isEmpty()) {
            var defaultState = new AttachmentStateDefinition(
                    "default", "Default", assetId, true, 0xFFFFFF);
            normalizedStates.put(defaultState.id(), defaultState);
        }
        states = java.util.Collections.unmodifiableMap(normalizedStates);
        defaultStateId = defaultStateId == null || !states.containsKey(defaultStateId)
                ? states.keySet().iterator().next() : defaultStateId;
        assetId = states.get(defaultStateId).assetId();
        compositeNodes = compositeNodes == null ? List.of() : List.copyOf(compositeNodes);
        rootLights = rootLights == null ? List.of() : List.copyOf(rootLights);
        if (!Double.isFinite(defaultWidth) || !Double.isFinite(defaultHeight)
                || defaultWidth <= 0.0 || defaultHeight <= 0.0
                || defaultWidth > 16_000.0 || defaultHeight > 16_000.0) {
            throw new IllegalArgumentException("Invalid attachment default size");
        }
    }

    public AttachmentDefinition(String id, String displayName, String assetId,
                                double defaultWidth, double defaultHeight) {
        this(id, displayName, assetId, defaultWidth, defaultHeight,
                null, null, null, null);
    }

    public AttachmentDefinition(String id, String displayName, String assetId,
                                double defaultWidth, double defaultHeight,
                                Map<String, AttachmentStateDefinition> states,
                                String defaultStateId) {
        this(id, displayName, assetId, defaultWidth, defaultHeight,
                states, defaultStateId, null, null);
    }

    public boolean hasImage() {
        return assetId != null;
    }

    public boolean isComposite() { return !compositeNodes.isEmpty() || !rootLights.isEmpty(); }
}
