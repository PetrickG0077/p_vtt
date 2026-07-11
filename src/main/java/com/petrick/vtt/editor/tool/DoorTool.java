package com.petrick.vtt.editor.tool;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.VttDoor;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneSize;
import com.petrick.vtt.feature.tabletop.VttSceneTransform;
import com.petrick.vtt.feature.tabletop.VttWall;
import com.petrick.vtt.platform.render.VRenderContext;

import java.util.function.Supplier;

/** Creates and performs the first editing operations for scene doors. */
public final class DoorTool implements Tool {
    public static final String ID = "door";
    private static final int LEFT_MOUSE_BUTTON = 0;
    private static final double MIN_DRAG_PIXELS = 4.0;
    private static final double SNAP_TOLERANCE_PIXELS = 8.0;

    private final Supplier<VttScene> sceneSupplier;
    private final Runnable saveAction;
    private String selectedDoorId;
    private Vec2d start;
    private Vec2d end;
    private Vec2d dragStartWorld;
    private VttSceneTransform originalTransform;
    private VttSceneSize originalSize;
    private String originalWallId;
    private Vec2d wallGuideStart;
    private Vec2d wallGuideEnd;
    private Vec2d snapGuideStart;
    private Vec2d snapGuideEnd;
    private Mode mode = Mode.NONE;

    public DoorTool(Supplier<VttScene> sceneSupplier, Runnable saveAction) {
        this.sceneSupplier = sceneSupplier;
        this.saveAction = saveAction;
    }

    @Override public String getId() { return ID; }

    @Override
    public boolean mouseClicked(ToolContext context, double mouseX, double mouseY, int button, int modifiers) {
        if (button != LEFT_MOUSE_BUTTON) return false;
        Vec2d world = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
        VttDoor clicked = findTopmostDoorAt(world);
        if (clicked != null) {
            selectedDoorId = clicked.getId();
            originalTransform = copyTransform(clicked.getTransform());
            originalSize = new VttSceneSize(clicked.getSize().getWidth(), clicked.getSize().getHeight());
            originalWallId = clicked.getWallId();
            dragStartWorld = world;
            mode = Mode.MOVE;
            return true;
        }
        selectedDoorId = null;
        start = world;
        end = world;
        mode = Mode.CREATE;
        return true;
    }

    @Override
    public boolean mouseDragged(ToolContext context, double mouseX, double mouseY, int button,
                                double dragX, double dragY, int modifiers) {
        if (button != LEFT_MOUSE_BUTTON || mode == Mode.NONE) return false;
        Vec2d world = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
        if (mode == Mode.CREATE) end = world;
        if (mode == Mode.MOVE) moveSelectedDoor(context, world);
        return true;
    }

    @Override
    public boolean mouseReleased(ToolContext context, double mouseX, double mouseY, int button, int modifiers) {
        if (button != LEFT_MOUSE_BUTTON || mode == Mode.NONE) return false;
        Vec2d world = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
        if (mode == Mode.CREATE) {
            end = world;
            createDoorIfLargeEnough(context, mouseX, mouseY);
        } else {
            moveSelectedDoor(context, world);
            saveAction.run();
        }
        finishOperation();
        return true;
    }

    @Override
    public void render(VRenderContext context, ToolContext toolContext) {
        if (mode == Mode.CREATE && start != null && end != null) renderCreationPreview(context);
        VttDoor selected = getSelectedDoor();
        if (selected != null) renderSelection(context, selected);
        renderGuides(context);
    }

    public boolean isEditing() { return mode != Mode.NONE; }

    public void cancel() {
        VttDoor selected = getSelectedDoor();
        if (selected != null && originalTransform != null) {
            selected.setTransform(originalTransform);
            if (originalSize != null) selected.setSize(originalSize);
            selected.setWallId(originalWallId);
        }
        finishOperation();
    }

    public void deactivate() {
        cancel();
        selectedDoorId = null;
    }

    public boolean deleteSelectedDoor() {
        VttScene scene = sceneSupplier.get();
        if (scene == null || selectedDoorId == null) return false;
        boolean removed = scene.removeDoor(selectedDoorId);
        if (removed) {
            selectedDoorId = null;
            finishOperation();
            saveAction.run();
        }
        return removed;
    }

    public boolean toggleSelectedDoorOpen() {
        VttDoor door = getSelectedDoor();
        if (door == null) return false;
        door.setOpen(!door.isOpen());
        saveAction.run();
        return true;
    }

    public boolean toggleSelectedDoorLocked() {
        VttDoor door = getSelectedDoor();
        if (door == null) return false;
        door.setLocked(!door.isLocked());
        saveAction.run();
        return true;
    }

    public boolean scaleSelectedDoor(double factor) {
        VttDoor door = getSelectedDoor();
        if (door == null) return false;
        door.getTransform().setScaleX(Math.max(0.05, door.getTransform().getScaleX() * factor));
        door.getTransform().setScaleY(Math.max(0.05, door.getTransform().getScaleY() * factor));
        saveAction.run();
        return true;
    }

    public boolean rotateSelectedDoor(double degrees) {
        VttDoor door = getSelectedDoor();
        if (door == null) return false;
        door.setWallId(null);
        door.getTransform().setRotationDegrees(door.getTransform().getRotationDegrees() + degrees);
        saveAction.run();
        return true;
    }

    public boolean resetSelectedDoorTransform() {
        VttDoor door = getSelectedDoor();
        if (door == null) return false;
        door.getTransform().setScaleX(1.0);
        door.getTransform().setScaleY(1.0);
        door.getTransform().setRotationDegrees(0.0);
        door.setWallId(null);
        saveAction.run();
        return true;
    }

    private void createDoorIfLargeEnough(ToolContext context, double mouseX, double mouseY) {
        Vec2d startScreen = context.renderState().worldToScreen(start);
        if (Math.abs(mouseX - startScreen.x()) < MIN_DRAG_PIXELS
                || Math.abs(mouseY - startScreen.y()) < MIN_DRAG_PIXELS) return;
        VttScene scene = sceneSupplier.get();
        if (scene == null) return;
        double width = Math.abs(end.x() - start.x());
        double height = Math.abs(end.y() - start.y());
        VttDoor door = new VttDoor(nextDoorId(scene), null,
                new VttSceneTransform((start.x() + end.x()) / 2.0,
                        (start.y() + end.y()) / 2.0, 1.0, 1.0, 0.0),
                new VttSceneSize(width, height));
        attachToContainingWall(door, context.camera().getZoom());
        scene.addDoor(door);
        selectedDoorId = door.getId();
        saveAction.run();
    }

    private void moveSelectedDoor(ToolContext context, Vec2d world) {
        VttDoor door = getSelectedDoor();
        if (door == null || originalTransform == null || dragStartWorld == null) return;
        door.getTransform().setX(originalTransform.getX() + world.x() - dragStartWorld.x());
        door.getTransform().setY(originalTransform.getY() + world.y() - dragStartWorld.y());
        attachToContainingWall(door, context.camera().getZoom());
    }

    private void attachToContainingWall(VttDoor door, double zoom) {
        if (door == null) return;
        wallGuideStart = null;
        wallGuideEnd = null;
        snapGuideStart = null;
        snapGuideEnd = null;
        double tolerance = SNAP_TOLERANCE_PIXELS / Math.max(0.0001, zoom);
        Vec2d rawCenter = new Vec2d(door.getTransform().getX(), door.getTransform().getY());
        VttWall wall = findNearestWall(rawCenter, tolerance);
        if (wall == null) {
            door.setWallId(null);
            return;
        }
        double radians = Math.toRadians(wall.getTransform().getRotationDegrees());
        Vec2d axis = new Vec2d(Math.cos(radians), Math.sin(radians));
        Vec2d normal = new Vec2d(-Math.sin(radians), Math.cos(radians));
        Vec2d wallCenter = new Vec2d(wall.getTransform().getX(), wall.getTransform().getY());
        double wallHalfWidth = actualWidth(wall) / 2.0;
        double desiredDoorWidth = Math.min(actualWidth(wall),
                Math.max(actualWidth(door), actualHeight(door)));
        double doorHalfWidth = desiredDoorWidth / 2.0;
        double rawLocalX = dot(rawCenter.subtract(wallCenter), axis);
        double minCenter = -wallHalfWidth + doorHalfWidth;
        double maxCenter = wallHalfWidth - doorHalfWidth;
        double localX = Math.max(minCenter, Math.min(maxCenter, rawLocalX));
        SnapValue snap = snapDoorAlongWall(door, wall, localX, doorHalfWidth, tolerance, axis, wallCenter);
        localX = snap.value();

        door.setWallId(wall.getId());
        door.getTransform().setRotationDegrees(wall.getTransform().getRotationDegrees());
        door.getTransform().setX(wallCenter.x() + axis.x() * localX);
        door.getTransform().setY(wallCenter.y() + axis.y() * localX);
        double scaleX = Math.max(0.05, Math.abs(door.getTransform().getScaleX()));
        double scaleY = Math.max(0.05, Math.abs(door.getTransform().getScaleY()));
        door.getSize().setWidth(desiredDoorWidth / scaleX);
        door.getSize().setHeight(actualHeight(wall) / scaleY);

        wallGuideStart = wallCenter.subtract(axis.multiply(wallHalfWidth));
        wallGuideEnd = wallCenter.add(axis.multiply(wallHalfWidth));
        if (snap.snapped()) {
            Vec2d guideCenter = wallCenter.add(axis.multiply(snap.guideLocalX()));
            double guideHalf = actualHeight(wall) / 2.0 + tolerance;
            snapGuideStart = guideCenter.subtract(normal.multiply(guideHalf));
            snapGuideEnd = guideCenter.add(normal.multiply(guideHalf));
        }
    }

    private VttWall findNearestWall(Vec2d world, double tolerance) {
        VttScene scene = sceneSupplier.get();
        if (scene == null) return null;
        VttWall best = null;
        double bestDistance = Double.MAX_VALUE;
        for (VttWall wall : scene.getWalls()) {
            if (wall == null || !wall.isVisible()) continue;
            double radians = Math.toRadians(-wall.getTransform().getRotationDegrees());
            double dx = world.x() - wall.getTransform().getX();
            double dy = world.y() - wall.getTransform().getY();
            double localX = dx * Math.cos(radians) - dy * Math.sin(radians);
            double localY = dx * Math.sin(radians) + dy * Math.cos(radians);
            if (Math.abs(localX) <= actualWidth(wall) / 2.0 + tolerance
                    && Math.abs(localY) <= actualHeight(wall) / 2.0 + tolerance
                    && Math.abs(localY) < bestDistance) {
                best = wall;
                bestDistance = Math.abs(localY);
            }
        }
        return best;
    }

    private SnapValue snapDoorAlongWall(
            VttDoor selected,
            VttWall wall,
            double rawValue,
            double doorHalfWidth,
            double tolerance,
            Vec2d axis,
            Vec2d wallCenter
    ) {
        double wallHalfWidth = actualWidth(wall) / 2.0;
        SnapValue best = SnapValue.none(rawValue);
        best = chooseSnap(best, rawValue, 0.0, 0.0, tolerance);
        best = chooseSnap(best, rawValue, -wallHalfWidth + doorHalfWidth, -wallHalfWidth, tolerance);
        best = chooseSnap(best, rawValue, wallHalfWidth - doorHalfWidth, wallHalfWidth, tolerance);

        VttScene scene = sceneSupplier.get();
        if (scene == null) return best;
        for (VttDoor target : scene.getDoors()) {
            if (target == null || target == selected || !wall.getId().equals(target.getWallId())) continue;
            double targetCenter = dot(new Vec2d(target.getTransform().getX(), target.getTransform().getY())
                    .subtract(wallCenter), axis);
            double targetHalf = actualWidth(target) / 2.0;
            best = chooseSnap(best, rawValue, targetCenter, targetCenter, tolerance);
            best = chooseSnap(best, rawValue,
                    targetCenter - targetHalf + doorHalfWidth, targetCenter - targetHalf, tolerance);
            best = chooseSnap(best, rawValue,
                    targetCenter + targetHalf - doorHalfWidth, targetCenter + targetHalf, tolerance);
            best = chooseSnap(best, rawValue,
                    targetCenter - targetHalf - doorHalfWidth, targetCenter - targetHalf, tolerance);
            best = chooseSnap(best, rawValue,
                    targetCenter + targetHalf + doorHalfWidth, targetCenter + targetHalf, tolerance);
        }
        double min = -wallHalfWidth + doorHalfWidth;
        double max = wallHalfWidth - doorHalfWidth;
        return new SnapValue(Math.max(min, Math.min(max, best.value())), best.guideLocalX(), best.snapped());
    }

    private SnapValue chooseSnap(
            SnapValue current, double rawValue, double candidate, double guide, double tolerance
    ) {
        double distance = Math.abs(candidate - rawValue);
        if (distance > tolerance) return current;
        if (!current.snapped() || distance < Math.abs(current.value() - rawValue)) {
            return new SnapValue(candidate, guide, true);
        }
        return current;
    }

    private double dot(Vec2d first, Vec2d second) {
        return first.x() * second.x() + first.y() * second.y();
    }

    private void renderGuides(VRenderContext context) {
        if (wallGuideStart != null && wallGuideEnd != null) {
            renderWorldLine(context, wallGuideStart, wallGuideEnd, 0xAA44FFFF);
        }
        if (snapGuideStart != null && snapGuideEnd != null) {
            renderWorldLine(context, snapGuideStart, snapGuideEnd, 0xFF44FFFF);
        }
    }

    private void renderWorldLine(VRenderContext context, Vec2d worldStart, Vec2d worldEnd, int color) {
        Vec2d a = context.renderState().worldToScreen(worldStart);
        Vec2d b = context.renderState().worldToScreen(worldEnd);
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0) return;
        PoseStack pose = context.graphics().pose();
        pose.pushPose();
        pose.translate(a.x(), a.y(), 0.0);
        pose.mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        context.graphics().fill(0, 0, (int) Math.round(length), 1, color);
        pose.popPose();
    }

    private VttDoor findTopmostDoorAt(Vec2d world) {
        VttScene scene = sceneSupplier.get();
        if (scene == null) return null;
        for (int i = scene.getDoors().size() - 1; i >= 0; i--) {
            VttDoor door = scene.getDoors().get(i);
            if (door != null && door.isVisible() && containsDoor(door, world)) return door;
        }
        return null;
    }

    private VttWall findTopmostWallAt(Vec2d world) {
        VttScene scene = sceneSupplier.get();
        if (scene == null) return null;
        for (int i = scene.getWalls().size() - 1; i >= 0; i--) {
            VttWall wall = scene.getWalls().get(i);
            if (wall != null && wall.isVisible() && containsWall(wall, world)) return wall;
        }
        return null;
    }

    private boolean containsDoor(VttDoor door, Vec2d world) {
        return containsRect(world, door.getTransform(),
                Math.abs(door.getSize().getWidth() * door.getTransform().getScaleX()),
                Math.abs(door.getSize().getHeight() * door.getTransform().getScaleY()));
    }

    private boolean containsWall(VttWall wall, Vec2d world) {
        return containsRect(world, wall.getTransform(),
                Math.abs(wall.getSize().getWidth() * wall.getTransform().getScaleX()), actualHeight(wall));
    }

    private boolean containsRect(Vec2d world, VttSceneTransform transform, double width, double height) {
        double radians = Math.toRadians(-transform.getRotationDegrees());
        double dx = world.x() - transform.getX();
        double dy = world.y() - transform.getY();
        double localX = dx * Math.cos(radians) - dy * Math.sin(radians);
        double localY = dx * Math.sin(radians) + dy * Math.cos(radians);
        return Math.abs(localX) <= width / 2.0 && Math.abs(localY) <= height / 2.0;
    }

    private double actualHeight(VttWall wall) {
        return Math.abs(wall.getSize().getHeight() * wall.getTransform().getScaleY());
    }

    private double actualWidth(VttWall wall) {
        return Math.abs(wall.getSize().getWidth() * wall.getTransform().getScaleX());
    }

    private double actualWidth(VttDoor door) {
        return Math.abs(door.getSize().getWidth() * door.getTransform().getScaleX());
    }

    private double actualHeight(VttDoor door) {
        return Math.abs(door.getSize().getHeight() * door.getTransform().getScaleY());
    }

    private VttDoor getSelectedDoor() {
        VttScene scene = sceneSupplier.get();
        if (scene == null || selectedDoorId == null) return null;
        return scene.getDoors().stream()
                .filter(door -> door != null && selectedDoorId.equals(door.getId()))
                .findFirst().orElse(null);
    }

    private VttSceneTransform copyTransform(VttSceneTransform source) {
        return new VttSceneTransform(source.getX(), source.getY(), source.getScaleX(),
                source.getScaleY(), source.getRotationDegrees());
    }

    private void renderCreationPreview(VRenderContext context) {
        Vec2d a = context.renderState().worldToScreen(start);
        Vec2d b = context.renderState().worldToScreen(end);
        int left = (int) Math.round(Math.min(a.x(), b.x()));
        int top = (int) Math.round(Math.min(a.y(), b.y()));
        int right = (int) Math.round(Math.max(a.x(), b.x()));
        int bottom = (int) Math.round(Math.max(a.y(), b.y()));
        context.graphics().fill(left, top, right, bottom, 0x6655FF88);
    }

    private void renderSelection(VRenderContext context, VttDoor door) {
        var transform = door.getTransform();
        Vec2d center = context.renderState().worldToScreen(new Vec2d(transform.getX(), transform.getY()));
        double zoom = context.renderState().getCamera().getZoom();
        int width = Math.max(1, (int) Math.round(Math.abs(door.getSize().getWidth() * transform.getScaleX()) * zoom));
        int height = Math.max(1, (int) Math.round(Math.abs(door.getSize().getHeight() * transform.getScaleY()) * zoom));
        int left = -width / 2;
        int top = -height / 2;
        int right = left + width;
        int bottom = top + height;
        PoseStack pose = context.graphics().pose();
        pose.pushPose();
        pose.translate(center.x(), center.y(), 0.0);
        pose.mulPose(Axis.ZP.rotationDegrees((float) transform.getRotationDegrees()));
        context.graphics().hLine(left, right, top, 0xFFFFFFFF);
        context.graphics().hLine(left, right, bottom, 0xFFFFFFFF);
        context.graphics().vLine(left, top, bottom, 0xFFFFFFFF);
        context.graphics().vLine(right, top, bottom, 0xFFFFFFFF);
        pose.popPose();
    }

    private String nextDoorId(VttScene scene) {
        int number = scene.getDoors().size() + 1;
        String id = "door_" + number;
        while (containsDoorId(scene, id)) id = "door_" + ++number;
        return id;
    }

    private boolean containsDoorId(VttScene scene, String id) {
        return scene.getDoors().stream().anyMatch(door -> door != null && id.equals(door.getId()));
    }

    private void finishOperation() {
        start = null;
        end = null;
        dragStartWorld = null;
        originalTransform = null;
        originalSize = null;
        originalWallId = null;
        wallGuideStart = null;
        wallGuideEnd = null;
        snapGuideStart = null;
        snapGuideEnd = null;
        mode = Mode.NONE;
    }

    private enum Mode { NONE, CREATE, MOVE }

    private record SnapValue(double value, double guideLocalX, boolean snapped) {
        private static SnapValue none(double value) { return new SnapValue(value, value, false); }
    }
}
