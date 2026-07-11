package com.petrick.vtt.editor.tool;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.render.RenderState;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneSize;
import com.petrick.vtt.feature.tabletop.VttSceneTransform;
import com.petrick.vtt.feature.tabletop.VttWall;
import com.petrick.vtt.platform.render.VRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.function.Supplier;

/** Creates, selects and edits persistent rectangular wall areas. */
public final class WallTool implements Tool {
    public static final String ID = "wall";
    private static final int LEFT_MOUSE_BUTTON = 0;
    private static final double MIN_DRAG_PIXELS = 4.0;
    private static final double HANDLE_HIT_PIXELS = 9.0;
    private static final double ROTATION_HANDLE_DISTANCE = 24.0;
    private static final double MIN_WALL_SIZE = 2.0;
    private final Supplier<VttScene> sceneSupplier;
    private final Runnable saveAction;
    private Vec2d start;
    private Vec2d end;
    private String selectedWallId;
    private EditMode editMode = EditMode.NONE;
    private Vec2d lastWorldPosition;
    private double rotationMouseOffset;
    private VttSceneTransform originalTransform;
    private VttSceneSize originalSize;

    public WallTool(Supplier<VttScene> sceneSupplier, Runnable saveAction) {
        this.sceneSupplier = sceneSupplier;
        this.saveAction = saveAction;
    }

    @Override public String getId() { return ID; }

    @Override
    public EditorCursor getCursor(ToolContext context, double mouseX, double mouseY) {
        Handle handle = findHandleAt(context, mouseX, mouseY);
        if (handle == Handle.TOP_LEFT || handle == Handle.BOTTOM_RIGHT) return EditorCursor.RESIZE_NWSE;
        if (handle == Handle.TOP_RIGHT || handle == Handle.BOTTOM_LEFT) return EditorCursor.RESIZE_NESW;
        return EditorCursor.DEFAULT;
    }

    @Override
    public boolean mouseClicked(ToolContext context, double mouseX, double mouseY, int button, int modifiers) {
        if (button != LEFT_MOUSE_BUTTON) return false;
        Vec2d world = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
        VttWall selected = getSelectedWall();
        Handle handle = findHandleAt(context, mouseX, mouseY);
        if (selected != null && handle != null) {
            snapshot(selected);
            if (handle == Handle.ROTATION) {
                editMode = EditMode.ROTATE;
                double mouseAngle = Math.toDegrees(Math.atan2(
                        world.y() - selected.getTransform().getY(),
                        world.x() - selected.getTransform().getX()));
                rotationMouseOffset = selected.getTransform().getRotationDegrees() - mouseAngle;
            } else {
                editMode = EditMode.RESIZE;
            }
            return true;
        }

        VttWall clicked = findTopmostWallAt(world);
        if (clicked != null) {
            selectedWallId = clicked.getId();
            snapshot(clicked);
            editMode = EditMode.MOVE;
            lastWorldPosition = world;
            return true;
        }

        selectedWallId = null;
        start = world;
        end = world;
        editMode = EditMode.CREATE;
        return true;
    }

    @Override
    public boolean mouseDragged(ToolContext context, double mouseX, double mouseY, int button,
                                double dragX, double dragY, int modifiers) {
        if (button != LEFT_MOUSE_BUTTON || editMode == EditMode.NONE) return false;
        Vec2d world = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
        if (editMode == EditMode.CREATE) {
            end = world;
        } else if (editMode == EditMode.MOVE) {
            moveSelectedWall(world);
        } else if (editMode == EditMode.RESIZE) {
            resizeSelectedWall(world);
        } else if (editMode == EditMode.ROTATE) {
            rotateSelectedWall(world);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(ToolContext context, double mouseX, double mouseY, int button, int modifiers) {
        if (button != LEFT_MOUSE_BUTTON || editMode == EditMode.NONE) return false;
        if (editMode == EditMode.CREATE) {
            end = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
            createWallIfLargeEnough(context, mouseX, mouseY);
        } else {
            Vec2d world = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
            if (editMode == EditMode.MOVE) moveSelectedWall(world);
            if (editMode == EditMode.RESIZE) resizeSelectedWall(world);
            if (editMode == EditMode.ROTATE) rotateSelectedWall(world);
            saveAction.run();
        }
        finishOperation();
        return true;
    }

    @Override
    public void render(VRenderContext context, ToolContext toolContext) {
        if (editMode == EditMode.CREATE && start != null && end != null) renderCreationPreview(context);
        VttWall selected = getSelectedWall();
        if (selected != null) renderSelection(context, selected);
    }

    public void cancel() {
        restoreOriginalGeometry();
        finishOperation();
    }

    public void deactivate() {
        cancel();
        selectedWallId = null;
    }

    public boolean isDrawing() { return editMode != EditMode.NONE; }

    public boolean deleteSelectedWall() {
        VttScene scene = sceneSupplier.get();
        if (scene == null || selectedWallId == null) return false;
        boolean removed = scene.removeWall(selectedWallId);
        if (removed) {
            selectedWallId = null;
            finishOperation();
            saveAction.run();
        }
        return removed;
    }

    public boolean scaleSelectedWall(double factor) {
        VttWall wall = getSelectedWall();
        if (wall == null) return false;
        wall.getTransform().setScaleX(Math.max(0.05, wall.getTransform().getScaleX() * factor));
        wall.getTransform().setScaleY(Math.max(0.05, wall.getTransform().getScaleY() * factor));
        saveAction.run();
        return true;
    }

    public boolean rotateSelectedWall(double degrees) {
        VttWall wall = getSelectedWall();
        if (wall == null) return false;
        wall.getTransform().setRotationDegrees(wall.getTransform().getRotationDegrees() + degrees);
        saveAction.run();
        return true;
    }

    public boolean resetSelectedWallTransform() {
        VttWall wall = getSelectedWall();
        if (wall == null) return false;
        wall.getTransform().setScaleX(1.0);
        wall.getTransform().setScaleY(1.0);
        wall.getTransform().setRotationDegrees(0.0);
        saveAction.run();
        return true;
    }

    private void createWallIfLargeEnough(ToolContext context, double mouseX, double mouseY) {
        double width = Math.abs(end.x() - start.x());
        double height = Math.abs(end.y() - start.y());
        Vec2d startScreen = context.renderState().worldToScreen(start);
        boolean largeEnough = Math.abs(mouseX - startScreen.x()) >= MIN_DRAG_PIXELS
                && Math.abs(mouseY - startScreen.y()) >= MIN_DRAG_PIXELS;
        VttScene scene = sceneSupplier.get();
        if (!largeEnough || scene == null) return;
        double centerX = (start.x() + end.x()) / 2.0;
        double centerY = (start.y() + end.y()) / 2.0;
        VttWall wall = new VttWall(nextWallId(scene),
                new VttSceneTransform(centerX, centerY, 1.0, 1.0, 0.0),
                new VttSceneSize(width, height));
        scene.addWall(wall);
        selectedWallId = wall.getId();
        saveAction.run();
    }

    private void moveSelectedWall(Vec2d world) {
        VttWall wall = getSelectedWall();
        if (wall == null || lastWorldPosition == null) return;
        wall.getTransform().setX(wall.getTransform().getX() + world.x() - lastWorldPosition.x());
        wall.getTransform().setY(wall.getTransform().getY() + world.y() - lastWorldPosition.y());
        lastWorldPosition = world;
    }

    private void resizeSelectedWall(Vec2d world) {
        VttWall wall = getSelectedWall();
        if (wall == null) return;
        VttSceneTransform transform = wall.getTransform();
        double radians = Math.toRadians(-transform.getRotationDegrees());
        double dx = world.x() - transform.getX();
        double dy = world.y() - transform.getY();
        double localX = dx * Math.cos(radians) - dy * Math.sin(radians);
        double localY = dx * Math.sin(radians) + dy * Math.cos(radians);
        double scaleX = Math.max(0.05, Math.abs(transform.getScaleX()));
        double scaleY = Math.max(0.05, Math.abs(transform.getScaleY()));
        wall.getSize().setWidth(Math.max(MIN_WALL_SIZE, Math.abs(localX) * 2.0 / scaleX));
        wall.getSize().setHeight(Math.max(MIN_WALL_SIZE, Math.abs(localY) * 2.0 / scaleY));
    }

    private void rotateSelectedWall(Vec2d world) {
        VttWall wall = getSelectedWall();
        if (wall == null) return;
        double angle = Math.toDegrees(Math.atan2(
                world.y() - wall.getTransform().getY(),
                world.x() - wall.getTransform().getX()));
        wall.getTransform().setRotationDegrees(angle + rotationMouseOffset);
    }

    private void renderCreationPreview(VRenderContext context) {
        Vec2d a = context.renderState().worldToScreen(start);
        Vec2d b = context.renderState().worldToScreen(end);
        int left = (int) Math.round(Math.min(a.x(), b.x()));
        int top = (int) Math.round(Math.min(a.y(), b.y()));
        int right = (int) Math.round(Math.max(a.x(), b.x()));
        int bottom = (int) Math.round(Math.max(a.y(), b.y()));
        if (right <= left || bottom <= top) return;
        context.graphics().fill(left, top, right, bottom, 0x66FF8844);
        drawBorder(context, left, top, right, bottom, 0xFFFFAA66);
    }

    private void renderSelection(VRenderContext context, VttWall wall) {
        VttSceneTransform transform = wall.getTransform();
        Vec2d center = context.renderState().worldToScreen(new Vec2d(transform.getX(), transform.getY()));
        double zoom = context.renderState().getCamera().getZoom();
        int width = Math.max(1, (int) Math.round(actualWidth(wall) * zoom));
        int height = Math.max(1, (int) Math.round(actualHeight(wall) * zoom));
        int left = -width / 2;
        int top = -height / 2;
        int right = left + width;
        int bottom = top + height;
        PoseStack pose = context.graphics().pose();
        pose.pushPose();
        pose.translate(center.x(), center.y(), 0.0);
        pose.mulPose(Axis.ZP.rotationDegrees((float) transform.getRotationDegrees()));
        drawBorder(context, left, top, right, bottom, 0xFFFFFFFF);
        pose.popPose();

        for (Vec2d corner : wallCorners(wall)) renderHandle(context, context.renderState().worldToScreen(corner));
        renderRotationHandle(context, rotationHandleScreenPosition(context.renderState(), wall));
    }

    private void drawBorder(VRenderContext context, int left, int top, int right, int bottom, int color) {
        context.graphics().hLine(left, right, top, color);
        context.graphics().hLine(left, right, bottom, color);
        context.graphics().vLine(left, top, bottom, color);
        context.graphics().vLine(right, top, bottom, color);
    }

    private void renderHandle(VRenderContext context, Vec2d position) {
        int x = (int) Math.round(position.x());
        int y = (int) Math.round(position.y());
        context.graphics().fill(x - 3, y - 3, x + 3, y + 3, 0xFFFFFFFF);
    }

    private void renderRotationHandle(VRenderContext context, Vec2d position) {
        int x = (int) Math.round(position.x());
        int y = (int) Math.round(position.y());
        context.graphics().fill(x - 3, y - 3, x + 3, y + 3, 0xFFFFCC66);
    }

    private Handle findHandleAt(ToolContext context, double mouseX, double mouseY) {
        VttWall wall = getSelectedWall();
        if (wall == null) return null;
        Vec2d rotation = rotationHandleScreenPosition(context.renderState(), wall);
        if (distance(rotation, mouseX, mouseY) <= HANDLE_HIT_PIXELS) return Handle.ROTATION;
        Vec2d[] corners = wallCorners(wall);
        Handle[] handles = {Handle.TOP_LEFT, Handle.TOP_RIGHT, Handle.BOTTOM_RIGHT, Handle.BOTTOM_LEFT};
        for (int i = 0; i < corners.length; i++) {
            Vec2d screen = context.renderState().worldToScreen(corners[i]);
            if (distance(screen, mouseX, mouseY) <= HANDLE_HIT_PIXELS) return handles[i];
        }
        return null;
    }

    private VttWall findTopmostWallAt(Vec2d world) {
        VttScene scene = sceneSupplier.get();
        if (scene == null) return null;
        for (int i = scene.getWalls().size() - 1; i >= 0; i--) {
            VttWall wall = scene.getWalls().get(i);
            if (wall != null && wall.isVisible() && containsPoint(wall, world)) return wall;
        }
        return null;
    }

    private boolean containsPoint(VttWall wall, Vec2d world) {
        VttSceneTransform transform = wall.getTransform();
        double radians = Math.toRadians(-transform.getRotationDegrees());
        double dx = world.x() - transform.getX();
        double dy = world.y() - transform.getY();
        double localX = dx * Math.cos(radians) - dy * Math.sin(radians);
        double localY = dx * Math.sin(radians) + dy * Math.cos(radians);
        return Math.abs(localX) <= actualWidth(wall) / 2.0
                && Math.abs(localY) <= actualHeight(wall) / 2.0;
    }

    private Vec2d[] wallCorners(VttWall wall) {
        double halfWidth = actualWidth(wall) / 2.0;
        double halfHeight = actualHeight(wall) / 2.0;
        return new Vec2d[]{
                localToWorld(wall, -halfWidth, -halfHeight),
                localToWorld(wall, halfWidth, -halfHeight),
                localToWorld(wall, halfWidth, halfHeight),
                localToWorld(wall, -halfWidth, halfHeight)
        };
    }

    private Vec2d localToWorld(VttWall wall, double localX, double localY) {
        double radians = Math.toRadians(wall.getTransform().getRotationDegrees());
        double x = localX * Math.cos(radians) - localY * Math.sin(radians);
        double y = localX * Math.sin(radians) + localY * Math.cos(radians);
        return new Vec2d(wall.getTransform().getX() + x, wall.getTransform().getY() + y);
    }

    private Vec2d rotationHandleScreenPosition(RenderState renderState, VttWall wall) {
        Vec2d center = renderState.worldToScreen(
                new Vec2d(wall.getTransform().getX(), wall.getTransform().getY()));
        Vec2d top = renderState.worldToScreen(localToWorld(wall, 0.0, -actualHeight(wall) / 2.0));
        Vec2d direction = top.subtract(center);
        direction = direction.length() < 0.001 ? new Vec2d(0.0, -1.0) : direction.normalize();
        return top.add(direction.multiply(ROTATION_HANDLE_DISTANCE));
    }

    private double actualWidth(VttWall wall) {
        return Math.abs(wall.getSize().getWidth() * wall.getTransform().getScaleX());
    }

    private double actualHeight(VttWall wall) {
        return Math.abs(wall.getSize().getHeight() * wall.getTransform().getScaleY());
    }

    private double distance(Vec2d position, double x, double y) {
        double dx = position.x() - x;
        double dy = position.y() - y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private VttWall getSelectedWall() {
        VttScene scene = sceneSupplier.get();
        if (scene == null || selectedWallId == null) return null;
        return scene.getWalls().stream()
                .filter(wall -> wall != null && selectedWallId.equals(wall.getId()))
                .findFirst().orElse(null);
    }

    private void snapshot(VttWall wall) {
        VttSceneTransform transform = wall.getTransform();
        originalTransform = new VttSceneTransform(transform.getX(), transform.getY(),
                transform.getScaleX(), transform.getScaleY(), transform.getRotationDegrees());
        originalSize = new VttSceneSize(wall.getSize().getWidth(), wall.getSize().getHeight());
    }

    private void restoreOriginalGeometry() {
        VttWall wall = getSelectedWall();
        if (wall != null && originalTransform != null && originalSize != null) {
            wall.setTransform(originalTransform);
            wall.setSize(originalSize);
        }
    }

    private void finishOperation() {
        start = null;
        end = null;
        lastWorldPosition = null;
        originalTransform = null;
        originalSize = null;
        editMode = EditMode.NONE;
    }

    private enum EditMode { NONE, CREATE, MOVE, RESIZE, ROTATE }
    private enum Handle { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT, ROTATION }

    private String nextWallId(VttScene scene) {
        int number = scene.getWalls().size() + 1;
        String id = "wall_" + number;
        while (containsWall(scene, id)) id = "wall_" + ++number;
        return id;
    }

    private boolean containsWall(VttScene scene, String id) {
        return scene.getWalls().stream().anyMatch(wall -> id.equals(wall.getId()));
    }
}
