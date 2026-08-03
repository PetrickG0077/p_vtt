package com.petrick.vtt.feature.token;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.canvas.CanvasObjectState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Define um token reutilizável.
 *
 * Diferente de CanvasObject:
 * - TokenDefinition é o modelo/template do token.
 * - CanvasObject é uma instância colocada no tabletop.
 *
 * Futuramente uma TokenDefinition poderá ser salva em pasta,
 * importada, exportada e reutilizada em vários tabletops.
 */
public record TokenDefinition(
        String id,
        String displayName,
        Vec2d defaultSize,
        Map<String, CanvasObjectState> states,
        String defaultStateId,
        String defaultOwnerId,
        Map<String, TokenStatePreset> statePresets
) {
    public static final double MIN_DEFAULT_SIZE = 1.0;
    public static final double MAX_DEFAULT_SIZE = 10_000.0;

    public TokenDefinition(
            String id, String displayName, Vec2d defaultSize,
            Map<String, CanvasObjectState> states, String defaultStateId
    ) {
        this(id, displayName, defaultSize, states, defaultStateId, null, Map.of());
    }

    public TokenDefinition(
            String id, String displayName, Vec2d defaultSize,
            Map<String, CanvasObjectState> states, String defaultStateId,
            String defaultOwnerId
    ) {
        this(id, displayName, defaultSize, states, defaultStateId,
                defaultOwnerId, Map.of());
    }

    public TokenDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Token definition id cannot be null or blank");
        }

        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Token definition display name cannot be null or blank");
        }

        if (defaultSize == null || !Double.isFinite(defaultSize.x())
                || !Double.isFinite(defaultSize.y())
                || defaultSize.x() < MIN_DEFAULT_SIZE
                || defaultSize.y() < MIN_DEFAULT_SIZE
                || defaultSize.x() > MAX_DEFAULT_SIZE
                || defaultSize.y() > MAX_DEFAULT_SIZE) {
            throw new IllegalArgumentException("Token definition default size is invalid");
        }

        if (states == null || states.isEmpty()) {
            throw new IllegalArgumentException("Token definition must have at least one state");
        }

        if (defaultStateId == null || defaultStateId.isBlank()) {
            throw new IllegalArgumentException("Default state id cannot be null or blank");
        }

        if (!states.containsKey(defaultStateId)) {
            throw new IllegalArgumentException("Default state does not exist: " + defaultStateId);
        }

        defaultOwnerId = defaultOwnerId == null || defaultOwnerId.isBlank()
                ? null : defaultOwnerId.trim();

        states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
        Map<String, TokenStatePreset> normalizedPresets = new LinkedHashMap<>();
        if (statePresets != null) {
            for (Map.Entry<String, TokenStatePreset> entry : statePresets.entrySet()) {
                String stateId = entry.getKey();
                TokenStatePreset preset = entry.getValue();
                if (stateId != null && states.containsKey(stateId) && preset != null) {
                    normalizedPresets.put(stateId, preset);
                }
            }
        }
        statePresets = Collections.unmodifiableMap(normalizedPresets);
    }
}
