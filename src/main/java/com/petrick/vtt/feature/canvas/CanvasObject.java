package com.petrick.vtt.feature.canvas;

import com.petrick.vtt.core.math.Rectd;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.transform.Transform2D;
import com.petrick.vtt.feature.canvas.visual.CanvasVisual;

import com.petrick.vtt.feature.token.TokenDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Objeto temporário do canvas.
 *
 * Agora ele suporta:
 * - múltiplos estados visuais;
 * - origem em TokenDefinition;
 * - visibilidade;
 * - flip horizontal visual sem usar escala negativa.
 *
 * Futuramente isso será convertido para entidades/componentes do ECS.
 */
public record CanvasObject(
        String id,
        String displayName,
        String sourceTokenDefinitionId,
        Transform2D transform,
        Vec2d size,
        Map<String, CanvasObjectState> states,
        String activeStateId,
        boolean visible,
        boolean flippedHorizontally
) {

    public static final String DEFAULT_STATE_ID = "default";

    public CanvasObject {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Object id cannot be null or blank");
        }

        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Object display name cannot be null or blank");
        }

        if (transform == null) {
            throw new IllegalArgumentException("Transform cannot be null");
        }

        if (size == null) {
            throw new IllegalArgumentException("Size cannot be null");
        }

        if (states == null || states.isEmpty()) {
            throw new IllegalArgumentException("Object must have at least one state");
        }

        if (activeStateId == null || activeStateId.isBlank()) {
            throw new IllegalArgumentException("Active state id cannot be null or blank");
        }

        if (!states.containsKey(activeStateId)) {
            throw new IllegalArgumentException("Active state does not exist: " + activeStateId);
        }

        states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
    }

    /**
     * Construtor de compatibilidade com a versão anterior,
     * antes de existir flippedHorizontally.
     */
    public CanvasObject(
            String id,
            String displayName,
            String sourceTokenDefinitionId,
            Transform2D transform,
            Vec2d size,
            Map<String, CanvasObjectState> states,
            String activeStateId,
            boolean visible
    ) {
        this(
                id,
                displayName,
                sourceTokenDefinitionId,
                transform,
                size,
                states,
                activeStateId,
                visible,
                false
        );
    }

    /**
     * Construtor de compatibilidade.
     *
     * Permite criar objetos com um único visual,
     * que automaticamente vira o estado "default".
     */
    public CanvasObject(
            String id,
            String displayName,
            Transform2D transform,
            Vec2d size,
            CanvasVisual visual,
            boolean visible
    ) {
        this(
                id,
                displayName,
                null,
                transform,
                size,
                createSingleStateMap(visual),
                DEFAULT_STATE_ID,
                visible,
                false
        );
    }

    private static Map<String, CanvasObjectState> createSingleStateMap(CanvasVisual visual) {
        Map<String, CanvasObjectState> states = new LinkedHashMap<>();

        states.put(
                DEFAULT_STATE_ID,
                new CanvasObjectState(
                        DEFAULT_STATE_ID,
                        "Default",
                        visual
                )
        );

        return states;
    }

    public CanvasObjectState currentState() {
        return states.get(activeStateId);
    }

    public CanvasVisual currentVisual() {
        return currentState().visual();
    }

    public CanvasObject withActiveState(String stateId) {
        if (stateId == null || stateId.isBlank()) {
            return this;
        }

        if (!states.containsKey(stateId)) {
            return this;
        }

        return new CanvasObject(
                id,
                displayName,
                sourceTokenDefinitionId,
                transform,
                size,
                states,
                stateId,
                visible,
                flippedHorizontally
        );
    }

    public CanvasObject withTransform(Transform2D transform) {
        return new CanvasObject(
                id,
                displayName,
                sourceTokenDefinitionId,
                transform,
                size,
                states,
                activeStateId,
                visible,
                flippedHorizontally
        );
    }

    public CanvasObject movedBy(Vec2d delta) {
        return withTransform(transform.movedBy(delta));
    }

    public CanvasObject scaledBy(double factor) {
        return withTransform(
                transform.withScale(transform.scale().multiply(factor))
        );
    }

    public CanvasObject rotatedBy(double deltaDegrees) {
        return withTransform(
                transform.rotatedBy(deltaDegrees)
        );
    }

    public CanvasObject resetScaleAndRotation() {
        return withTransform(
                transform
                        .withRotation(0.0)
                        .withScale(new Vec2d(1.0, 1.0))
        );
    }

    public CanvasObject withVisible(boolean visible) {
        return new CanvasObject(
                id,
                displayName,
                sourceTokenDefinitionId,
                transform,
                size,
                states,
                activeStateId,
                visible,
                flippedHorizontally
        );
    }

    public CanvasObject toggledVisibility() {
        return withVisible(!visible);
    }

    public CanvasObject withDisplayName(String displayName) {
        return new CanvasObject(
                id,
                displayName,
                sourceTokenDefinitionId,
                transform,
                size,
                states,
                activeStateId,
                visible,
                flippedHorizontally
        );
    }

    public CanvasObject withFlippedHorizontally(boolean flippedHorizontally) {
        return new CanvasObject(
                id,
                displayName,
                sourceTokenDefinitionId,
                transform,
                size,
                states,
                activeStateId,
                visible,
                flippedHorizontally
        );
    }

    public CanvasObject toggledHorizontalFlip() {
        return withFlippedHorizontally(!flippedHorizontally);
    }

    public CanvasObject duplicatedAs(String newId, Vec2d offset) {
        return new CanvasObject(
                newId,
                displayName + " Copy",
                sourceTokenDefinitionId,
                transform.movedBy(offset),
                size,
                states,
                activeStateId,
                visible,
                flippedHorizontally
        );
    }

    public Vec2d scaledSize() {
        return new Vec2d(
                size.x() * transform.scale().x(),
                size.y() * transform.scale().y()
        );
    }

    public Vec2d worldTopLeft() {
        Vec2d scaledSize = scaledSize();
        return localToWorld(new Vec2d(-scaledSize.x() / 2.0, -scaledSize.y() / 2.0));
    }

    public Vec2d worldTopRight() {
        Vec2d scaledSize = scaledSize();
        return localToWorld(new Vec2d(scaledSize.x() / 2.0, -scaledSize.y() / 2.0));
    }

    public Vec2d worldBottomLeft() {
        Vec2d scaledSize = scaledSize();
        return localToWorld(new Vec2d(-scaledSize.x() / 2.0, scaledSize.y() / 2.0));
    }

    public Vec2d worldBottomRight() {
        Vec2d scaledSize = scaledSize();
        return localToWorld(new Vec2d(scaledSize.x() / 2.0, scaledSize.y() / 2.0));
    }

    public boolean containsWorldPoint(Vec2d worldPoint) {
        Vec2d localPoint = worldToLocal(worldPoint);
        Vec2d scaledSize = scaledSize();

        return Math.abs(localPoint.x()) <= scaledSize.x() / 2.0
                && Math.abs(localPoint.y()) <= scaledSize.y() / 2.0;
    }

    public Rectd bounds() {
        Vec2d topLeft = worldTopLeft();
        Vec2d topRight = worldTopRight();
        Vec2d bottomLeft = worldBottomLeft();
        Vec2d bottomRight = worldBottomRight();

        double minX = Math.min(
                Math.min(topLeft.x(), topRight.x()),
                Math.min(bottomLeft.x(), bottomRight.x())
        );

        double minY = Math.min(
                Math.min(topLeft.y(), topRight.y()),
                Math.min(bottomLeft.y(), bottomRight.y())
        );

        double maxX = Math.max(
                Math.max(topLeft.x(), topRight.x()),
                Math.max(bottomLeft.x(), bottomRight.x())
        );

        double maxY = Math.max(
                Math.max(topLeft.y(), topRight.y()),
                Math.max(bottomLeft.y(), bottomRight.y())
        );

        return new Rectd(
                minX,
                minY,
                maxX - minX,
                maxY - minY
        );
    }

    public CanvasObject withTokenDefinition(TokenDefinition definition) {
        if (definition == null) {
            return this;
        }

        String newActiveStateId = activeStateId;

        if (!definition.states().containsKey(newActiveStateId)) {
            newActiveStateId = definition.defaultStateId();
        }

        return new CanvasObject(
                id,
                displayName,
                sourceTokenDefinitionId,
                transform,
                size,
                definition.states(),
                newActiveStateId,
                visible,
                flippedHorizontally
        );
    }

    private Vec2d localToWorld(Vec2d localPoint) {
        Vec2d rotated = rotate(localPoint, transform.rotationDegrees());
        return transform.position().add(rotated);
    }

    private Vec2d worldToLocal(Vec2d worldPoint) {
        Vec2d translated = worldPoint.subtract(transform.position());
        return rotate(translated, -transform.rotationDegrees());
    }

    private static Vec2d rotate(Vec2d point, double degrees) {
        double radians = Math.toRadians(degrees);

        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        return new Vec2d(
                point.x() * cos - point.y() * sin,
                point.x() * sin + point.y() * cos
        );
    }

    public boolean hasSourceTokenDefinition() {
        return sourceTokenDefinitionId != null && !sourceTokenDefinitionId.isBlank();
    }
}
