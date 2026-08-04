package com.petrick.vtt.feature.attachment;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.transform.Transform2D;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.tabletop.VttAttachmentBinding;
import com.petrick.vtt.feature.tabletop.VttAttachmentAnchor;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.feature.tabletop.VttLight;

import java.util.Set;

/** Resolves per-instance attachment bindings without coupling them to token definitions. */
public final class AttachmentBindingService {
    private static final double MIN_SCALE = 0.0001;
    public static final int MAX_BINDING_DEPTH = 16;

    private AttachmentBindingService() {}

    public static boolean bind(VttScene scene, CanvasScene canvas,
                               String attachmentId, String targetTokenId) {
        VttSceneObject attachment = find(scene, attachmentId);
        CanvasObject child = canvas == null ? null : canvas.findObjectById(attachmentId);
        CanvasObject parent = canvas == null ? null : canvas.findObjectById(targetTokenId);
        if (attachment == null || !attachment.isAttachment() || child == null || parent == null
                || !(parent.hasSourceTokenDefinition() || parent.hasSourceAttachmentDefinition())
                || !canBind(scene, attachmentId, targetTokenId)) {
            return false;
        }
        VttAttachmentBinding binding = attachment.getAttachmentBinding();
        boolean newBinding = binding == null;
        if (newBinding) binding = new VttAttachmentBinding();
        binding.setTargetObjectId(targetTokenId);
        attachment.setAttachmentBinding(binding);
        captureCurrentTransform(binding, child, parent);
        // A newly created binding follows the token's flip by default. Reparenting
        // retains the attachment's anchor, scope and follow settings.
        if (newBinding) binding.setFlipOffset(false);
        return true;
    }

    public static boolean canBind(VttScene scene, String attachmentId, String targetId) {
        if (scene == null || attachmentId == null || targetId == null
                || attachmentId.equals(targetId)) return false;
        VttSceneObject target = find(scene, targetId);
        if (target == null || (!target.isAttachment()
                && (target.getSourceTokenDefinitionId() == null
                || target.getSourceTokenDefinitionId().isBlank()))) return false;
        String current = targetId;
        Set<String> visited = new java.util.HashSet<>();
        for (int depth = 0; depth < MAX_BINDING_DEPTH; depth++) {
            if (!visited.add(current) || attachmentId.equals(current)) return false;
            VttSceneObject object = find(scene, current);
            if (object == null || !object.isAttachment()
                    || object.getAttachmentBinding() == null
                    || !object.getAttachmentBinding().isBound()) return true;
            current = object.getAttachmentBinding().getTargetObjectId();
        }
        return false;
    }

    public static boolean detach(VttScene scene, String attachmentId) {
        VttSceneObject attachment = find(scene, attachmentId);
        if (attachment == null || attachment.getAttachmentBinding() == null) return false;
        attachment.setAttachmentBinding(null);
        return true;
    }

    public static void recapture(VttScene scene, CanvasScene canvas, String attachmentId) {
        VttSceneObject attachment = find(scene, attachmentId);
        if (attachment == null || canvas == null) return;
        VttAttachmentBinding binding = attachment.getAttachmentBinding();
        if (binding == null || !binding.isBound()) return;
        CanvasObject child = canvas.findObjectById(attachmentId);
        CanvasObject parent = canvas.findObjectById(binding.getTargetObjectId());
        if (child != null && parent != null) captureCurrentTransform(binding, child, parent);
    }

    public static boolean setAnchor(
            VttScene scene, CanvasScene canvas, String attachmentId,
            VttAttachmentAnchor anchor
    ) {
        VttSceneObject attachment = find(scene, attachmentId);
        VttAttachmentBinding binding = attachment == null
                ? null : attachment.getAttachmentBinding();
        CanvasObject child = canvas == null ? null : canvas.findObjectById(attachmentId);
        CanvasObject parent = binding == null || canvas == null ? null
                : canvas.findObjectById(binding.getTargetObjectId());
        if (binding == null || !binding.isBound() || child == null || parent == null) return false;
        binding.setAnchor(anchor);
        // Recalculate the residual offset against the new anchor so changing
        // anchor never makes an attachment jump.
        captureCurrentTransform(binding, child, parent);
        return true;
    }

    public static void captureSelectedOffsets(VttScene scene, CanvasScene canvas,
                                              Set<String> selectedIds) {
        if (scene == null || canvas == null || selectedIds == null) return;
        for (String id : selectedIds) {
            VttSceneObject attachment = find(scene, id);
            if (attachment == null || !attachment.isAttachment()) continue;
            VttAttachmentBinding binding = attachment.getAttachmentBinding();
            if (binding == null || !binding.isBound()) continue;
            CanvasObject child = canvas.findObjectById(id);
            CanvasObject parent = canvas.findObjectById(binding.getTargetObjectId());
            if (child != null && parent != null) captureCurrentTransform(binding, child, parent);
        }
    }

    public static void synchronize(VttScene scene, CanvasScene canvas, Set<String> ignoredIds) {
        if (scene == null || canvas == null) return;
        Set<String> ignored = ignoredIds == null ? Set.of() : ignoredIds;
        // Resolve parents before descendants. Repeated passes are bounded by
        // MAX_BINDING_DEPTH and make scene-list order irrelevant.
        for (int pass = 0; pass < MAX_BINDING_DEPTH; pass++) {
            boolean changed = false;
            for (VttSceneObject sceneObject : scene.getObjects()) {
                if (sceneObject == null || !sceneObject.isAttachment()
                        || ignored.contains(sceneObject.getId())) continue;
                VttAttachmentBinding binding = sceneObject.getAttachmentBinding();
                if (binding == null || !binding.isBound()) continue;
                CanvasObject child = canvas.findObjectById(sceneObject.getId());
                CanvasObject parent = canvas.findObjectById(binding.getTargetObjectId());
                if (child == null || parent == null) continue;
                Transform2D resolved = resolve(binding, child.transform(), parent.transform(),
                        parent.flippedHorizontally());
                boolean flipped = parent.flippedHorizontally() ^ binding.isFlipOffset();
                if (!same(child.transform(), resolved) || child.flippedHorizontally() != flipped) {
                    canvas.replaceObject(child.withTransform(resolved)
                            .withFlippedHorizontally(flipped));
                    changed = true;
                }
            }
            if (!changed) break;
        }
    }

    public static boolean bindLight(VttScene scene, CanvasScene canvas,
                                    String lightId, String attachmentId) {
        VttLight light = findLight(scene, lightId);
        CanvasObject attachment = canvas == null ? null : canvas.findObjectById(attachmentId);
        if (light == null || attachment == null
                || !attachment.hasSourceAttachmentDefinition()) return false;
        light.setAttachedToObjectId(attachmentId);
        light.setAttachmentStateId(attachment.activeStateId());
        captureLightTransform(light, attachment);
        return true;
    }

    public static boolean detachLight(VttScene scene, String lightId) {
        VttLight light = findLight(scene, lightId);
        if (light == null || !light.isAttached()) return false;
        light.setAttachedToObjectId(null);
        light.setAttachmentStateId(null);
        return true;
    }

    public static void recaptureLight(VttScene scene, CanvasScene canvas, String lightId) {
        VttLight light = findLight(scene, lightId);
        if (light == null || canvas == null || !light.isAttached()) return;
        CanvasObject attachment = canvas.findObjectById(light.getAttachedToObjectId());
        if (attachment != null) captureLightTransform(light, attachment);
    }

    public static void synchronizeLights(VttScene scene, CanvasScene canvas,
                                         String ignoredLightId) {
        if (scene == null || canvas == null) return;
        for (VttLight light : scene.getLights()) {
            if (light == null || !light.isAttached()
                    || light.getId().equals(ignoredLightId)) continue;
            CanvasObject attachment = canvas.findObjectById(light.getAttachedToObjectId());
            if (attachment == null || !attachment.hasSourceAttachmentDefinition()) {
                light.setAttachedToObjectId(null);
                light.setAttachmentStateId(null);
                continue;
            }
            double localOffsetX = attachment.flippedHorizontally()
                    ? -light.getAttachmentOffsetX() : light.getAttachmentOffsetX();
            Vec2d offset = new Vec2d(
                    localOffsetX * attachment.transform().scale().x(),
                    light.getAttachmentOffsetY() * attachment.transform().scale().y());
            offset = rotate(offset, attachment.transform().rotationDegrees());
            Vec2d position = attachment.transform().position().add(offset);
            light.setX(position.x());
            light.setY(position.y());
            double localDirection = attachment.flippedHorizontally()
                    ? mirrorHorizontalDirection(light.getAttachmentDirectionOffsetDegrees())
                    : light.getAttachmentDirectionOffsetDegrees();
            light.setDirectionDegrees(attachment.transform().rotationDegrees() + localDirection);
        }
    }

    public static VttLight findLight(VttScene scene, String lightId) {
        if (scene == null || lightId == null) return null;
        return scene.getLights().stream()
                .filter(light -> light != null && lightId.equals(light.getId()))
                .findFirst().orElse(null);
    }

    public static VttSceneObject find(VttScene scene, String objectId) {
        if (scene == null || objectId == null) return null;
        return scene.getObjects().stream()
                .filter(object -> object != null && objectId.equals(object.getId()))
                .findFirst().orElse(null);
    }

    private static void captureCurrentTransform(VttAttachmentBinding binding,
                                                CanvasObject child, CanvasObject parent) {
        Vec2d offset = child.transform().position().subtract(parent.transform().position());
        if (binding.isFollowRotation()) {
            offset = rotate(offset, -parent.transform().rotationDegrees());
        }
        if (parent.flippedHorizontally()) offset = new Vec2d(-offset.x(), offset.y());
        Vec2d anchor = scaledAnchorOffset(binding.getAnchor(), parent);
        Vec2d residual = offset.subtract(anchor);
        if (binding.isFollowScale()) {
            residual = new Vec2d(
                    binding.isInheritScaleX()
                            ? residual.x() / safeScale(parent.transform().scale().x()) : residual.x(),
                    binding.isInheritScaleY()
                            ? residual.y() / safeScale(parent.transform().scale().y()) : residual.y());
        }
        if (!binding.isLockOffsetX()) binding.setOffsetX(residual.x());
        if (!binding.isLockOffsetY()) binding.setOffsetY(residual.y());
        binding.setFlipOffset(child.flippedHorizontally() ^ parent.flippedHorizontally());
        binding.setRotationOffsetDegrees(child.transform().rotationDegrees()
                - parent.transform().rotationDegrees());
        binding.setScaleMultiplierX(binding.isInheritScaleX()
                ? child.transform().scale().x() / safeScale(parent.transform().scale().x())
                : child.transform().scale().x());
        binding.setScaleMultiplierY(binding.isInheritScaleY()
                ? child.transform().scale().y() / safeScale(parent.transform().scale().y())
                : child.transform().scale().y());
    }

    private static void captureLightTransform(VttLight light, CanvasObject attachment) {
        Vec2d offset = new Vec2d(light.getX(), light.getY())
                .subtract(attachment.transform().position());
        offset = rotate(offset, -attachment.transform().rotationDegrees());
        offset = new Vec2d(
                offset.x() / safeScale(attachment.transform().scale().x()),
                offset.y() / safeScale(attachment.transform().scale().y()));
        light.setAttachmentOffsetX(offset.x());
        light.setAttachmentOffsetY(offset.y());
        light.setAttachmentDirectionOffsetDegrees(light.getDirectionDegrees()
                - attachment.transform().rotationDegrees());
    }

    private static Transform2D resolve(VttAttachmentBinding binding, Transform2D child,
                                       Transform2D parent, boolean parentFlippedHorizontally) {
        Vec2d position = child.position();
        if (binding.isFollowPosition()) {
            Vec2d anchor = scaledAnchorOffset(binding.getAnchor(), parent);
            Vec2d residual = new Vec2d(binding.getOffsetX(), binding.getOffsetY());
            if (binding.isFollowScale()) {
                residual = new Vec2d(
                        binding.isInheritScaleX() ? residual.x() * parent.scale().x() : residual.x(),
                        binding.isInheritScaleY() ? residual.y() * parent.scale().y() : residual.y());
            }
            Vec2d offset = anchor.add(residual);
            if (parentFlippedHorizontally) offset = new Vec2d(-offset.x(), offset.y());
            if (binding.isFollowRotation()) {
                offset = rotate(offset, parent.rotationDegrees());
            }
            position = parent.position().add(offset);
        }
        double rotation = binding.isFollowRotation()
                ? parent.rotationDegrees() + binding.getRotationOffsetDegrees()
                : child.rotationDegrees();
        Vec2d scale = binding.isFollowScale()
                ? new Vec2d(binding.isInheritScaleX()
                ? parent.scale().x() * binding.getScaleMultiplierX()
                : binding.getScaleMultiplierX(),
                binding.isInheritScaleY()
                        ? parent.scale().y() * binding.getScaleMultiplierY()
                        : binding.getScaleMultiplierY())
                : child.scale();
        scale = new Vec2d(binding.constrainScale(scale.x()),
                binding.constrainScale(scale.y()));
        return new Transform2D(position, rotation, scale);
    }

    private static Vec2d rotate(Vec2d point, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec2d(point.x() * cos - point.y() * sin,
                point.x() * sin + point.y() * cos);
    }

    private static Vec2d scaledAnchorOffset(VttAttachmentAnchor anchor, CanvasObject parent) {
        VttAttachmentAnchor safe = anchor == null ? VttAttachmentAnchor.CUSTOM : anchor;
        return new Vec2d(parent.size().x() * parent.transform().scale().x()
                        * 0.5 * safe.horizontal(),
                parent.size().y() * parent.transform().scale().y()
                        * 0.5 * safe.vertical());
    }

    private static double mirrorHorizontalDirection(double degrees) {
        double mirrored = 180.0 - degrees;
        mirrored %= 360.0;
        return mirrored < 0.0 ? mirrored + 360.0 : mirrored;
    }

    private static double safeScale(double value) {
        if (!Double.isFinite(value) || Math.abs(value) < MIN_SCALE) return 1.0;
        return value;
    }

    private static boolean same(Transform2D left, Transform2D right) {
        return left.position().subtract(right.position()).length() < 0.0001
                && Math.abs(left.rotationDegrees() - right.rotationDegrees()) < 0.0001
                && left.scale().subtract(right.scale()).length() < 0.0001;
    }
}
