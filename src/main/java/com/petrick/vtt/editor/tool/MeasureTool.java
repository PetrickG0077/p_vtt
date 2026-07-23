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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.util.Locale;
import java.util.function.Supplier;

/** Temporary ruler that measures world distance without changing the active scene. */
public final class MeasureTool implements Tool {
    public static final String ID = "measure";

    private static final int LEFT_MOUSE_BUTTON = 0;
    private static final int RIGHT_MOUSE_BUTTON = 1;
    private static final double DEFAULT_GRID_SIZE = 64.0;
    private static final double SNAP_TOLERANCE_PIXELS = 8.0;
    private static final int LINE_COLOR = 0xFFFFD45A;
    private static final int ENDPOINT_COLOR = 0xFFFFFFFF;
    private static final int SNAP_COLOR = 0xFF44FFFF;

    private final Supplier<VttScene> sceneSupplier;

    private Vec2d start;
    private Vec2d end;
    private boolean dragging;
    private boolean startSnapped;
    private boolean endSnapped;

    public MeasureTool(Supplier<VttScene> sceneSupplier) {
        this.sceneSupplier = sceneSupplier;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean mouseClicked(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            int modifiers
    ) {
        if (button == RIGHT_MOUSE_BUTTON) {
            cancel();
            return true;
        }
        if (button != LEFT_MOUSE_BUTTON) return false;

        SnappedPoint point = snapPoint(context, screenToWorld(context, mouseX, mouseY));
        start = point.position();
        end = point.position();
        startSnapped = point.snapped();
        endSnapped = point.snapped();
        dragging = true;
        return true;
    }

    @Override
    public boolean mouseDragged(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY,
            int modifiers
    ) {
        if (button != LEFT_MOUSE_BUTTON || !dragging || start == null) return false;
        updateEnd(context, mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            int modifiers
    ) {
        if (button != LEFT_MOUSE_BUTTON || !dragging || start == null) return false;
        updateEnd(context, mouseX, mouseY);
        dragging = false;
        return true;
    }

    @Override
    public void render(VRenderContext context, ToolContext toolContext) {
        if (start == null || end == null) return;

        Vec2d screenStart = context.renderState().worldToScreen(start);
        Vec2d screenEnd = context.renderState().worldToScreen(end);
        renderMeasurementLine(context, screenStart, screenEnd);
        renderEndpoint(context, screenStart, startSnapped);
        renderEndpoint(context, screenEnd, endSnapped);
        renderDistanceLabel(context, screenStart, screenEnd);
    }

    public boolean cancel() {
        if (start == null && end == null && !dragging) return false;
        start = null;
        end = null;
        startSnapped = false;
        endSnapped = false;
        dragging = false;
        return true;
    }

    public void deactivate() {
        cancel();
    }

    private void updateEnd(ToolContext context, double mouseX, double mouseY) {
        SnappedPoint point = snapPoint(context, screenToWorld(context, mouseX, mouseY));
        end = point.position();
        endSnapped = point.snapped();
    }

    private Vec2d screenToWorld(ToolContext context, double mouseX, double mouseY) {
        return context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
    }

    private SnappedPoint snapPoint(ToolContext context, Vec2d point) {
        double tolerance = SNAP_TOLERANCE_PIXELS
                / Math.max(0.0001, context.camera().getZoom());
        SnapCandidate best = SnapCandidate.none(point);
        VttScene scene = sceneSupplier.get();
        double gridSize = scene == null
                ? DEFAULT_GRID_SIZE : scene.getGrid().getGridSize();

        Vec2d gridPoint = new Vec2d(
                Math.round(point.x() / gridSize) * gridSize,
                Math.round(point.y() / gridSize) * gridSize);
        best = chooseCloser(point, gridPoint, tolerance, best);

        if (scene != null) {
            for (VttWall wall : scene.getWalls()) {
                if (wall != null && wall.isVisible()) {
                    best = snapToRectangleAnchors(
                            point, wall.getTransform(), wall.getSize(), tolerance, best);
                }
            }
            for (VttDoor door : scene.getDoors()) {
                if (door != null && door.isVisible()) {
                    best = snapToRectangleAnchors(
                            point, door.getTransform(), door.getSize(), tolerance, best);
                }
            }
        }

        return new SnappedPoint(best.position(), best.snapped());
    }

    private SnapCandidate snapToRectangleAnchors(
            Vec2d point,
            VttSceneTransform transform,
            VttSceneSize size,
            double tolerance,
            SnapCandidate current
    ) {
        double halfWidth = Math.abs(size.getWidth() * transform.getScaleX()) / 2.0;
        double halfHeight = Math.abs(size.getHeight() * transform.getScaleY()) / 2.0;
        double[][] anchors = {
                {-halfWidth, -halfHeight},
                {halfWidth, -halfHeight},
                {halfWidth, halfHeight},
                {-halfWidth, halfHeight},
                {-halfWidth, 0.0},
                {halfWidth, 0.0},
                {0.0, -halfHeight},
                {0.0, halfHeight}
        };
        SnapCandidate best = current;
        for (double[] anchor : anchors) {
            Vec2d worldAnchor = localToWorld(transform, anchor[0], anchor[1]);
            best = chooseCloser(point, worldAnchor, tolerance, best);
        }
        return best;
    }

    private SnapCandidate chooseCloser(
            Vec2d source,
            Vec2d candidate,
            double tolerance,
            SnapCandidate current
    ) {
        double distance = source.distance(candidate);
        if (distance > tolerance || distance >= current.distance()) return current;
        return new SnapCandidate(candidate, distance, true);
    }

    private Vec2d localToWorld(VttSceneTransform transform, double localX, double localY) {
        double radians = Math.toRadians(transform.getRotationDegrees());
        double rotatedX = localX * Math.cos(radians) - localY * Math.sin(radians);
        double rotatedY = localX * Math.sin(radians) + localY * Math.cos(radians);
        return new Vec2d(transform.getX() + rotatedX, transform.getY() + rotatedY);
    }

    private void renderMeasurementLine(
            VRenderContext context,
            Vec2d screenStart,
            Vec2d screenEnd
    ) {
        double dx = screenEnd.x() - screenStart.x();
        double dy = screenEnd.y() - screenStart.y();
        int length = Math.max(1, (int) Math.round(Math.sqrt(dx * dx + dy * dy)));
        PoseStack pose = context.graphics().pose();
        pose.pushPose();
        pose.translate(screenStart.x(), screenStart.y(), 0.0);
        pose.mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        context.graphics().fill(0, -1, length, 1, LINE_COLOR);
        context.graphics().fill(-1, -5, 1, 5, ENDPOINT_COLOR);
        context.graphics().fill(length - 1, -5, length + 1, 5, ENDPOINT_COLOR);
        pose.popPose();
    }

    private void renderEndpoint(VRenderContext context, Vec2d point, boolean snapped) {
        int x = (int) Math.round(point.x());
        int y = (int) Math.round(point.y());
        int color = snapped ? SNAP_COLOR : ENDPOINT_COLOR;
        context.graphics().fill(x - 2, y - 2, x + 3, y + 3, color);
        context.graphics().fill(x - 1, y - 1, x + 2, y + 2, 0xFF101014);
    }

    private void renderDistanceLabel(
            VRenderContext context,
            Vec2d screenStart,
            Vec2d screenEnd
    ) {
        double distance = start.distance(end);
        VttScene scene = sceneSupplier.get();
        double gridSize = scene == null
                ? DEFAULT_GRID_SIZE : scene.getGrid().getGridSize();
        String label = String.format(Locale.ROOT, "%.2f m", distance / gridSize);
        Font font = Minecraft.getInstance().font;
        int centerX = (int) Math.round((screenStart.x() + screenEnd.x()) / 2.0);
        int centerY = (int) Math.round((screenStart.y() + screenEnd.y()) / 2.0) - 15;
        int width = font.width(label) + 8;
        context.graphics().fill(
                centerX - width / 2, centerY - 3,
                centerX + (width + 1) / 2, centerY + 11,
                0xDD101014);
        context.graphics().drawCenteredString(font, label, centerX, centerY, 0xFFFFFFFF);
    }

    private record SnappedPoint(Vec2d position, boolean snapped) {
    }

    private record SnapCandidate(Vec2d position, double distance, boolean snapped) {
        private static SnapCandidate none(Vec2d position) {
            return new SnapCandidate(position, Double.POSITIVE_INFINITY, false);
        }
    }
}
