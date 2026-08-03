package com.petrick.vtt.feature.token;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.transform.Transform2D;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.tabletop.VttAttachmentBinding;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.feature.tabletop.VttTokenStateAppearance;
import com.petrick.vtt.feature.tabletop.VttLight;
import com.petrick.vtt.feature.attachment.AttachmentBindingService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

/** Maintains global token appearance and explicitly saved per-state instance overrides. */
public final class TokenStateOverrideService {
    private static final double EPSILON = 0.0001;
    private final Map<String, Set<String>> attachmentBaselines = new HashMap<>();

    public void beginEditing(VttScene scene, CanvasScene canvas, String tokenId) {
        VttSceneObject token = findToken(scene, tokenId);
        CanvasObject canvasToken = canvas == null ? null : canvas.findObjectById(tokenId);
        if (token == null || canvasToken == null) return;
        if (token.getGlobalStateAppearance() == null) {
            token.setGlobalStateAppearance(capture(canvasToken, token));
        }
        attachmentBaselines.putIfAbsent(key(tokenId, canvasToken.activeStateId()),
                effectiveAttachedIds(scene, tokenId, canvasToken.activeStateId()));
    }

    public boolean switchState(VttScene scene, CanvasScene canvas,
                               String tokenId, String targetStateId) {
        VttSceneObject token = findToken(scene, tokenId);
        CanvasObject canvasToken = canvas == null ? null : canvas.findObjectById(tokenId);
        if (token == null || canvasToken == null || targetStateId == null
                || !canvasToken.states().containsKey(targetStateId)) return false;
        beginEditing(scene, canvas, tokenId);
        reconcileUnsavedChanges(token, canvasToken);
        VttTokenStateAppearance target = token.getStateAppearances().get(targetStateId);
        if (target == null) target = token.getGlobalStateAppearance();
        apply(canvas, token, canvasToken, targetStateId, target);
        attachmentBaselines.put(key(tokenId, targetStateId),
                effectiveAttachedIds(scene, tokenId, targetStateId));
        return true;
    }

    public boolean saveCurrentState(VttScene scene, CanvasScene canvas, String tokenId) {
        VttSceneObject token = findToken(scene, tokenId);
        CanvasObject canvasToken = canvas == null ? null : canvas.findObjectById(tokenId);
        if (token == null || canvasToken == null) return false;
        beginEditing(scene, canvas, tokenId);
        String stateId = canvasToken.activeStateId();
        token.getStateAppearances().put(stateId, capture(canvasToken, token));

        // Attachment scope is explicit in the attachment context menu. Saving
        // an appearance must never silently turn a newly added global
        // attachment into a state-specific one.
        attachmentBaselines.put(key(tokenId, stateId),
                effectiveAttachedIds(scene, tokenId, stateId));
        return true;
    }

    public boolean hasOverride(VttScene scene, String tokenId, String stateId) {
        VttSceneObject token = findToken(scene, tokenId);
        return token != null && stateId != null && token.getStateAppearances().containsKey(stateId);
    }

    public TokenStatePreset captureDefinitionPreset(
            VttScene scene, CanvasScene canvas, String tokenId
    ) {
        if (!saveCurrentState(scene, canvas, tokenId)) return null;
        CanvasObject token = canvas.findObjectById(tokenId);
        VttSceneObject metadata = findToken(scene, tokenId);
        if (token == null || metadata == null) return null;
        String stateId = token.activeStateId();
        List<TokenStateAttachmentPreset> attachments = new ArrayList<>();
        for (VttSceneObject attachment : scene.getObjects()) {
            if (attachment == null || !attachment.isAttachment()) continue;
            VttAttachmentBinding binding = attachment.getAttachmentBinding();
            if (binding == null || !binding.isBound()
                    || !tokenId.equals(binding.getTargetObjectId())
                    || !stateId.equals(binding.getParentStateId())) continue;
            AttachmentBindingService.recapture(scene, canvas, attachment.getId());
            CanvasObject canvasAttachment = canvas.findObjectById(attachment.getId());
            List<VttLight> lights = scene.getLights().stream()
                    .filter(light -> light != null
                            && attachment.getId().equals(light.getAttachedToObjectId()))
                    .map(this::copyLightTemplate).toList();
            attachments.add(new TokenStateAttachmentPreset(
                    attachment.getSourceAttachmentDefinitionId(), attachment.getDisplayName(),
                    canvasAttachment == null || canvasAttachment.visible(),
                    canvasAttachment != null && canvasAttachment.flippedHorizontally(),
                    attachment.getState().getTintColorRgb(), binding.isFollowPosition(),
                    binding.isFollowRotation(), binding.isFollowScale(), binding.isFlipOffset(),
                    binding.getAnchor(),
                    binding.getOffsetX(), binding.getOffsetY(), binding.getRotationOffsetDegrees(),
                    binding.getScaleMultiplierX(), binding.getScaleMultiplierY(), lights));
        }
        VttTokenStateAppearance appearance = metadata.getStateAppearances().get(stateId);
        return appearance == null ? null
                : new TokenStatePreset(appearance.copy(), attachments);
    }

    public Map<String, TokenStatePreset> captureAllDefinitionPresets(
            VttScene scene, CanvasScene canvas, String tokenId
    ) {
        VttSceneObject token = findToken(scene, tokenId);
        if (token == null || canvas == null) return Map.of();
        Map<String, TokenStatePreset> result = new java.util.LinkedHashMap<>();
        for (String stateId : List.copyOf(token.getStateAppearances().keySet())) {
            TokenStatePreset preset = captureDefinitionPresetForState(
                    scene, canvas, tokenId, stateId);
            if (preset != null) result.put(stateId, preset);
        }
        return result;
    }

    public boolean removeCurrentStateOverride(
            VttScene scene, CanvasScene canvas, String tokenId
    ) {
        VttSceneObject token = findToken(scene, tokenId);
        CanvasObject live = canvas == null ? null : canvas.findObjectById(tokenId);
        if (token == null || live == null) return false;
        String stateId = live.activeStateId();
        token.getStateAppearances().remove(stateId);
        Set<String> stateAttachmentIds = new HashSet<>();
        for (VttSceneObject attachment : List.copyOf(scene.getObjects())) {
            VttAttachmentBinding binding = attachment == null
                    ? null : attachment.getAttachmentBinding();
            if (attachment != null && attachment.isAttachment() && binding != null
                    && tokenId.equals(binding.getTargetObjectId())
                    && stateId.equals(binding.getParentStateId())) {
                stateAttachmentIds.add(attachment.getId());
            }
        }
        if (!stateAttachmentIds.isEmpty()) {
            List<String> lightIds = scene.getLights().stream()
                    .filter(light -> light != null
                            && stateAttachmentIds.contains(light.getAttachedToObjectId()))
                    .map(VttLight::getId).toList();
            lightIds.forEach(scene::removeLight);
            stateAttachmentIds.forEach(scene::removeObject);
            canvas.removeObjects(stateAttachmentIds);
        }
        VttTokenStateAppearance global = token.getGlobalStateAppearance();
        if (global != null) apply(canvas, token, live, stateId, global);
        return true;
    }

    private TokenStatePreset captureDefinitionPresetForState(
            VttScene scene, CanvasScene canvas, String tokenId, String stateId
    ) {
        VttSceneObject metadata = findToken(scene, tokenId);
        VttTokenStateAppearance appearance = metadata == null
                ? null : metadata.getStateAppearances().get(stateId);
        if (appearance == null) return null;
        List<TokenStateAttachmentPreset> attachments = new ArrayList<>();
        for (VttSceneObject attachment : scene.getObjects()) {
            if (attachment == null || !attachment.isAttachment()) continue;
            VttAttachmentBinding binding = attachment.getAttachmentBinding();
            if (binding == null || !binding.isBound()
                    || !tokenId.equals(binding.getTargetObjectId())
                    || !stateId.equals(binding.getParentStateId())) continue;
            AttachmentBindingService.recapture(scene, canvas, attachment.getId());
            CanvasObject canvasAttachment = canvas.findObjectById(attachment.getId());
            List<VttLight> lights = scene.getLights().stream()
                    .filter(light -> light != null
                            && attachment.getId().equals(light.getAttachedToObjectId()))
                    .map(this::copyLightTemplate).toList();
            attachments.add(new TokenStateAttachmentPreset(
                    attachment.getSourceAttachmentDefinitionId(), attachment.getDisplayName(),
                    canvasAttachment == null || canvasAttachment.visible(),
                    canvasAttachment != null && canvasAttachment.flippedHorizontally(),
                    attachment.getState().getTintColorRgb(), binding.isFollowPosition(),
                    binding.isFollowRotation(), binding.isFollowScale(), binding.isFlipOffset(),
                    binding.getAnchor(),
                    binding.getOffsetX(), binding.getOffsetY(), binding.getRotationOffsetDegrees(),
                    binding.getScaleMultiplierX(), binding.getScaleMultiplierY(), lights));
        }
        return new TokenStatePreset(appearance.copy(), attachments);
    }

    private void reconcileUnsavedChanges(VttSceneObject token, CanvasObject liveToken) {
        VttTokenStateAppearance live = capture(liveToken, token);
        VttTokenStateAppearance global = token.getGlobalStateAppearance();
        VttTokenStateAppearance saved = token.getStateAppearances().get(liveToken.activeStateId());
        if (saved == null) {
            token.setGlobalStateAppearance(live);
            return;
        }
        double scaleRatioX = ratio(live.getScaleX(), saved.getScaleX());
        double scaleRatioY = ratio(live.getScaleY(), saved.getScaleY());
        global.setScaleX(global.getScaleX() * scaleRatioX);
        global.setScaleY(global.getScaleY() * scaleRatioY);
        global.setRotationDegrees(global.getRotationDegrees()
                + live.getRotationDegrees() - saved.getRotationDegrees());
        if (live.getTintColorRgb() != saved.getTintColorRgb()) {
            global.setTintColorRgb(live.getTintColorRgb());
        }
    }

    private void apply(CanvasScene canvas, VttSceneObject sceneToken, CanvasObject canvasToken,
                       String stateId, VttTokenStateAppearance appearance) {
        if (appearance == null) return;
        Transform2D current = canvasToken.transform();
        Transform2D transform = new Transform2D(current.position(), appearance.getRotationDegrees(),
                new Vec2d(appearance.getScaleX(), appearance.getScaleY()));
        CanvasObject updated = canvasToken.withActiveState(stateId).withTransform(transform);
        canvas.replaceObject(updated);
        sceneToken.getState().setActiveStateId(stateId);
        sceneToken.getState().setTintColorRgb(appearance.getTintColorRgb());
        sceneToken.getTransform().setScaleX(appearance.getScaleX());
        sceneToken.getTransform().setScaleY(appearance.getScaleY());
        sceneToken.getTransform().setRotationDegrees(appearance.getRotationDegrees());
    }

    private VttTokenStateAppearance capture(CanvasObject canvasToken, VttSceneObject sceneToken) {
        return new VttTokenStateAppearance(canvasToken.transform().scale().x(),
                canvasToken.transform().scale().y(), canvasToken.transform().rotationDegrees(),
                sceneToken.getState().getTintColorRgb());
    }

    private Set<String> effectiveAttachedIds(VttScene scene, String tokenId, String stateId) {
        Set<String> ids = new HashSet<>();
        if (scene == null) return ids;
        for (VttSceneObject attachment : scene.getObjects()) {
            if (attachment == null || !attachment.isAttachment()) continue;
            VttAttachmentBinding binding = attachment.getAttachmentBinding();
            if (binding == null || !binding.isBound()
                    || !tokenId.equals(binding.getTargetObjectId())) continue;
            if (binding.getParentStateId() == null
                    || binding.getParentStateId().equals(stateId)) ids.add(attachment.getId());
        }
        return ids;
    }

    private VttSceneObject findToken(VttScene scene, String tokenId) {
        if (scene == null || tokenId == null) return null;
        return scene.getObjects().stream().filter(object -> object != null
                        && tokenId.equals(object.getId())
                        && object.getSourceTokenDefinitionId() != null)
                .findFirst().orElse(null);
    }

    private String key(String tokenId, String stateId) {
        return tokenId + "\u0000" + stateId;
    }

    private double ratio(double value, double base) {
        return Math.abs(base) < EPSILON ? 1.0 : value / base;
    }

    private VttLight copyLightTemplate(VttLight source) {
        VttLight copy = new VttLight(source.getId(), 0.0, 0.0);
        copy.setType(source.getType());
        copy.setOuterRadius(source.getOuterRadius());
        copy.setInnerRadius(source.getInnerRadius());
        copy.setColorRgb(source.getColorRgb());
        copy.setIntensity(source.getIntensity());
        copy.setDirectionDegrees(source.getDirectionDegrees());
        copy.setConeAngleDegrees(source.getConeAngleDegrees());
        copy.setInnerConeAngleDegrees(source.getInnerConeAngleDegrees());
        copy.setTintEnabled(source.isTintEnabled());
        copy.setEnabled(source.isEnabled());
        copy.setAttachmentOffsetX(source.getAttachmentOffsetX());
        copy.setAttachmentOffsetY(source.getAttachmentOffsetY());
        copy.setAttachmentDirectionOffsetDegrees(
                source.getAttachmentDirectionOffsetDegrees());
        return copy;
    }
}
