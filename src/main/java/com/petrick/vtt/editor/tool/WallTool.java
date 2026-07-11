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
import org.lwjgl.glfw.GLFW;
import java.util.function.Supplier;

/** Creates, selects and edits persistent rectangular wall areas. */
public final class WallTool implements Tool {
    public static final String ID = "wall";
    private static final int LEFT_MOUSE_BUTTON = 0;
    private static final double MIN_DRAG_PIXELS = 4.0;
    private static final double HANDLE_HIT_PIXELS = 9.0;
    private static final double ROTATION_HANDLE_DISTANCE = 24.0;
    private static final double MIN_WALL_SIZE = 2.0;
    private static final double SNAP_TOLERANCE_PIXELS = 8.0;
    private final Supplier<VttScene> sceneSupplier;
    private final Runnable saveAction;
    private Vec2d start;
    private Vec2d end;
    private String selectedWallId;
    private EditMode editMode = EditMode.NONE;
    private Vec2d lastWorldPosition;
    private Vec2d interactionStartWorld;
    private Vec2d resizeAnchorWorld;
    private Handle activeHandle;
    private SnapGuide snapGuideU;
    private SnapGuide snapGuideV;
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
                activeHandle = handle;
                resizeAnchorWorld = oppositeCorner(selected, handle);
            }
            return true;
        }

        VttWall clicked = findTopmostWallAt(world);
        if (clicked != null) {
            selectedWallId = clicked.getId();
            snapshot(clicked);
            editMode = EditMode.MOVE;
            lastWorldPosition = world;
            interactionStartWorld = world;
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
            moveSelectedWall(context, world);
        } else if (editMode == EditMode.RESIZE) {
            resizeSelectedWall(context, world, modifiers);
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
            if (editMode == EditMode.MOVE) moveSelectedWall(context, world);
            if (editMode == EditMode.RESIZE) resizeSelectedWall(context, world, modifiers);
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
        renderSnapGuides(context);
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

    private void moveSelectedWall(ToolContext context, Vec2d world) {
        VttWall wall = getSelectedWall();
        if (wall == null || interactionStartWorld == null || originalTransform == null) return;
        double candidateX = originalTransform.getX() + world.x() - interactionStartWorld.x();
        double candidateY = originalTransform.getY() + world.y() - interactionStartWorld.y();
        SnapOffset snap = snapBoundsToOtherWalls(context, wall, candidateX, candidateY);
        wall.getTransform().setX(candidateX + snap.x());
        wall.getTransform().setY(candidateY + snap.y());
        lastWorldPosition = world;
    }

    private void resizeSelectedWall(ToolContext context, Vec2d world, int modifiers) {
        VttWall wall = getSelectedWall();
        if (wall == null || resizeAnchorWorld == null || activeHandle == null) return;
        world = snapPointToOtherWalls(context, world, wall);
        VttSceneTransform transform = wall.getTransform();
        double radians = Math.toRadians(-transform.getRotationDegrees());
        double dx = world.x() - resizeAnchorWorld.x();
        double dy = world.y() - resizeAnchorWorld.y();
        double localX = dx * Math.cos(radians) - dy * Math.sin(radians);
        double localY = dx * Math.sin(radians) + dy * Math.cos(radians);
        double scaleX = Math.max(0.05, Math.abs(transform.getScaleX()));
        double scaleY = Math.max(0.05, Math.abs(transform.getScaleY()));
        double newWidth = Math.max(MIN_WALL_SIZE, Math.abs(localX));
        double newHeight = Math.max(MIN_WALL_SIZE, Math.abs(localY));
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && originalSize != null && originalTransform != null) {
            double originalWidth = Math.abs(originalSize.getWidth() * originalTransform.getScaleX());
            double originalHeight = Math.abs(originalSize.getHeight() * originalTransform.getScaleY());
            double aspect = originalHeight <= 0.0 ? 1.0 : originalWidth / originalHeight;
            if (newWidth / newHeight > aspect) newWidth = newHeight * aspect;
            else newHeight = newWidth / aspect;
        }
        int signX = handleSignX(activeHandle);
        int signY = handleSignY(activeHandle);
        Vec2d centerOffset = rotate(new Vec2d(signX * newWidth / 2.0, signY * newHeight / 2.0),
                transform.getRotationDegrees());
        transform.setX(resizeAnchorWorld.x() + centerOffset.x());
        transform.setY(resizeAnchorWorld.y() + centerOffset.y());
        wall.getSize().setWidth(newWidth / scaleX);
        wall.getSize().setHeight(newHeight / scaleY);
    }

    private void rotateSelectedWall(Vec2d world) {
        VttWall wall = getSelectedWall();
        if (wall == null) return;
        double angle = Math.toDegrees(Math.atan2(
                world.y() - wall.getTransform().getY(),
                world.x() - wall.getTransform().getX()));
        wall.getTransform().setRotationDegrees(angle + rotationMouseOffset);
    }

    private Vec2d oppositeCorner(VttWall wall, Handle handle) {
        Vec2d[] corners = wallCorners(wall);
        return switch (handle) {
            case TOP_LEFT -> corners[2];
            case TOP_RIGHT -> corners[3];
            case BOTTOM_RIGHT -> corners[0];
            case BOTTOM_LEFT -> corners[1];
            case ROTATION -> new Vec2d(wall.getTransform().getX(), wall.getTransform().getY());
        };
    }

    private int handleSignX(Handle handle) {
        return switch (handle) {
            case TOP_LEFT, BOTTOM_LEFT -> -1;
            case TOP_RIGHT, BOTTOM_RIGHT -> 1;
            case ROTATION -> 0;
        };
    }

    private int handleSignY(Handle handle) {
        return switch (handle) {
            case TOP_LEFT, TOP_RIGHT -> -1;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> 1;
            case ROTATION -> 0;
        };
    }

    private Vec2d rotate(Vec2d point, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec2d(point.x() * cos - point.y() * sin,
                point.x() * sin + point.y() * cos);
    }

    private SnapOffset snapBoundsToOtherWalls(
            ToolContext context, VttWall wall, double candidateX, double candidateY
    ) {
        snapGuideU = null;
        snapGuideV = null;
        double tolerance = SNAP_TOLERANCE_PIXELS / Math.max(0.0001, context.camera().getZoom());
        Vec2d u = wallAxis(wall, true);
        Vec2d v = wallAxis(wall, false);
        AxisSnap snapU = findMoveAxisSnap(wall, candidateX, candidateY, u, tolerance);
        AxisSnap snapV = findMoveAxisSnap(wall, candidateX, candidateY, v, tolerance);
        if (snapU.snapped()) snapGuideU = new SnapGuide(u, snapU.guideOffset());
        if (snapV.snapped()) snapGuideV = new SnapGuide(v, snapV.guideOffset());
        Vec2d deltaU = u.multiply(snapU.delta());
        Vec2d deltaV = v.multiply(snapV.delta());
        return new SnapOffset(deltaU.x() + deltaV.x(), deltaU.y() + deltaV.y());
    }

    private AxisSnap findMoveAxisSnap(
            VttWall selected, double candidateX, double candidateY, Vec2d normal, double tolerance
    ) {
        Projection source = projectionAt(selected, normal, candidateX, candidateY);
        Vec2d tangent = new Vec2d(-normal.y(), normal.x());
        Projection sourceTangent = projectionAt(selected, tangent, candidateX, candidateY);
        double proximity = tolerance * 3.0;
        AxisSnap best = AxisSnap.none();
        VttScene scene = sceneSupplier.get();
        if (scene == null) return best;

        for (VttWall target : scene.getWalls()) {
            if (!isSnapTarget(selected, target) || !hasParallelAxis(target, normal)) continue;
            Projection targetTangent = projectionAt(target, tangent,
                    target.getTransform().getX(), target.getTransform().getY());
            if (intervalGap(sourceTangent, targetTangent) > proximity) continue;
            Projection targetProjection = projectionAt(target, normal,
                    target.getTransform().getX(), target.getTransform().getY());
            best = chooseSnap(best, targetProjection.min() - source.min(), targetProjection.min(), tolerance);
            best = chooseSnap(best, targetProjection.max() - source.min(), targetProjection.max(), tolerance);
            best = chooseSnap(best, targetProjection.min() - source.max(), targetProjection.min(), tolerance);
            best = chooseSnap(best, targetProjection.max() - source.max(), targetProjection.max(), tolerance);
            best = chooseSnap(best, targetProjection.center() - source.center(), targetProjection.center(), tolerance);
        }
        return best;
    }

    private Vec2d snapPointToOtherWalls(ToolContext context, Vec2d point, VttWall selected) {
        snapGuideU = null;
        snapGuideV = null;
        double tolerance = SNAP_TOLERANCE_PIXELS / Math.max(0.0001, context.camera().getZoom());
        Vec2d u = wallAxis(selected, true);
        Vec2d v = wallAxis(selected, false);
        AxisSnap snapU = findPointAxisSnap(selected, point, u, tolerance);
        AxisSnap snapV = findPointAxisSnap(selected, point, v, tolerance);
        if (snapU.snapped()) snapGuideU = new SnapGuide(u, snapU.guideOffset());
        if (snapV.snapped()) snapGuideV = new SnapGuide(v, snapV.guideOffset());
        Vec2d deltaU = u.multiply(snapU.delta());
        Vec2d deltaV = v.multiply(snapV.delta());
        return point.add(new Vec2d(deltaU.x() + deltaV.x(), deltaU.y() + deltaV.y()));
    }

    private AxisSnap findPointAxisSnap(VttWall selected, Vec2d point, Vec2d normal, double tolerance) {
        AxisSnap best = AxisSnap.none();
        double source = dot(point, normal);
        Vec2d tangent = new Vec2d(-normal.y(), normal.x());
        double sourceTangent = dot(point, tangent);
        double proximity = tolerance * 3.0;
        VttScene scene = sceneSupplier.get();
        if (scene == null) return best;
        for (VttWall target : scene.getWalls()) {
            if (!isSnapTarget(selected, target) || !hasParallelAxis(target, normal)) continue;
            Projection targetTangent = projectionAt(target, tangent,
                    target.getTransform().getX(), target.getTransform().getY());
            if (sourceTangent < targetTangent.min() - proximity
                    || sourceTangent > targetTangent.max() + proximity) continue;
            Projection targetProjection = projectionAt(target, normal,
                    target.getTransform().getX(), target.getTransform().getY());
            best = chooseSnap(best, targetProjection.min() - source, targetProjection.min(), tolerance);
            best = chooseSnap(best, targetProjection.max() - source, targetProjection.max(), tolerance);
        }
        return best;
    }

    private AxisSnap chooseSnap(AxisSnap current, double delta, double guideOffset, double tolerance) {
        if (Math.abs(delta) > tolerance) return current;
        if (!current.snapped() || Math.abs(delta) < Math.abs(current.delta())) {
            return new AxisSnap(delta, guideOffset, true);
        }
        return current;
    }

    private boolean isSnapTarget(VttWall selected, VttWall target) {
        return target != null && target != selected && target.isVisible();
    }

    private boolean hasParallelAxis(VttWall wall, Vec2d normal) {
        double threshold = Math.cos(Math.toRadians(0.5));
        return Math.abs(dot(normal, wallAxis(wall, true))) >= threshold
                || Math.abs(dot(normal, wallAxis(wall, false))) >= threshold;
    }

    private Vec2d wallAxis(VttWall wall, boolean horizontal) {
        double radians = Math.toRadians(wall.getTransform().getRotationDegrees());
        return horizontal
                ? new Vec2d(Math.cos(radians), Math.sin(radians))
                : new Vec2d(-Math.sin(radians), Math.cos(radians));
    }

    private Projection projectionAt(VttWall wall, Vec2d normal, double centerX, double centerY) {
        Vec2d u = wallAxis(wall, true);
        Vec2d v = wallAxis(wall, false);
        double extent = Math.abs(dot(u, normal)) * actualWidth(wall) / 2.0
                + Math.abs(dot(v, normal)) * actualHeight(wall) / 2.0;
        double center = centerX * normal.x() + centerY * normal.y();
        return new Projection(center - extent, center, center + extent);
    }

    private double intervalGap(Projection first, Projection second) {
        if (first.max() < second.min()) return second.min() - first.max();
        if (second.max() < first.min()) return first.min() - second.max();
        return 0.0;
    }

    private double dot(Vec2d first, Vec2d second) {
        return first.x() * second.x() + first.y() * second.y();
    }

    private void renderSnapGuides(VRenderContext context) {
        if (snapGuideU != null) renderSnapGuide(context, snapGuideU);
        if (snapGuideV != null) renderSnapGuide(context, snapGuideV);
    }

    private void renderSnapGuide(VRenderContext context, SnapGuide guide) {
        Vec2d point = guide.normal().multiply(guide.offset());
        Vec2d direction = new Vec2d(-guide.normal().y(), guide.normal().x());
        double worldLength = Math.max(context.screenWidth(), context.screenHeight())
                / Math.max(0.0001, context.renderState().getCamera().getZoom()) * 2.0;
        Vec2d a = context.renderState().worldToScreen(point.subtract(direction.multiply(worldLength)));
        Vec2d b = context.renderState().worldToScreen(point.add(direction.multiply(worldLength)));
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = Math.sqrt(dx * dx + dy * dy);
        PoseStack pose = context.graphics().pose();
        pose.pushPose();
        pose.translate(a.x(), a.y(), 0.0);
        pose.mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        context.graphics().fill(0, 0, (int) Math.round(length), 1, 0xFF44FFFF);
        pose.popPose();
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
        interactionStartWorld = null;
        resizeAnchorWorld = null;
        activeHandle = null;
        snapGuideU = null;
        snapGuideV = null;
        originalTransform = null;
        originalSize = null;
        editMode = EditMode.NONE;
    }

    private enum EditMode { NONE, CREATE, MOVE, RESIZE, ROTATE }
    private enum Handle { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT, ROTATION }

    private record SnapOffset(double x, double y) {}

    private record AxisSnap(double delta, double guideOffset, boolean snapped) {
        private static AxisSnap none() { return new AxisSnap(0.0, 0.0, false); }
    }

    private record Projection(double min, double center, double max) {}

    private record SnapGuide(Vec2d normal, double offset) {}

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
