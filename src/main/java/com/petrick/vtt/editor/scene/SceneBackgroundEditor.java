package com.petrick.vtt.editor.scene;

import com.petrick.vtt.VTT;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.render.RenderState;
import com.petrick.vtt.feature.asset.AssetRef;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.feature.asset.LibraryTextureAssetRef;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnailRegistry;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneBackgroundTransform;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;

/** Modal editor for the active scene background. */
public final class SceneBackgroundEditor {
    private static final double MIN_SIZE = 16.0;
    private static final double HANDLE_HIT_RADIUS = 7.0;
    private static final int BORDER_COLOR = 0xFFFFAA44;
    private static final int HANDLE_COLOR = 0xFFFFFFFF;

    private final AssetRegistry assetRegistry;
    private final AssetThumbnailRegistry thumbnailRegistry;

    private boolean active;
    private int sourceWidth;
    private int sourceHeight;
    private String editedSceneId;
    private VttSceneBackgroundTransform originalTransform;
    private DragMode dragMode = DragMode.NONE;
    private Corner activeCorner;
    private Vec2d moveOffset;
    private Vec2d resizeAnchor;
    private double resizeAspectRatio;

    public SceneBackgroundEditor(
            AssetRegistry assetRegistry,
            AssetThumbnailRegistry thumbnailRegistry
    ) {
        this.assetRegistry = assetRegistry;
        this.thumbnailRegistry = thumbnailRegistry;
    }

    public boolean begin(VttScene scene) {
        if (scene == null || scene.getBackgroundAssetId() == null) {
            VTT.LOGGER.warn("[VTT Scene Edit] Cannot begin without an active background");
            return false;
        }
        ImageSize size = resolveSize(scene.getBackgroundAssetId());
        if (size == null) {
            VTT.LOGGER.warn("[VTT Scene Edit] Could not resolve dimensions for {}",
                    scene.getBackgroundAssetId());
            return false;
        }
        sourceWidth = size.width();
        sourceHeight = size.height();
        editedSceneId = scene.getId();
        originalTransform = scene.getBackgroundTransform().copy();
        dragMode = DragMode.NONE;
        activeCorner = null;
        active = true;
        VTT.LOGGER.info("[VTT Scene Edit] Started for scene {} using {} ({}x{})",
                scene.getId(), scene.getBackgroundAssetId(), sourceWidth, sourceHeight);
        return true;
    }

    public boolean isActive() {
        return active;
    }

    public void confirm() {
        active = false;
        editedSceneId = null;
        clearDrag();
    }

    public void cancel(VttScene scene) {
        if (active && scene != null && scene.getId().equals(editedSceneId)
                && originalTransform != null) {
            scene.setBackgroundTransform(originalTransform);
        }
        active = false;
        editedSceneId = null;
        clearDrag();
    }

    public void reset(VttScene scene) {
        if (active && scene != null) scene.getBackgroundTransform().reset();
    }

    public boolean mouseClicked(
            VttScene scene,
            RenderState renderState,
            double mouseX,
            double mouseY,
            int button
    ) {
        if (!active) return false;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || scene == null) return true;
        Vec2d mouseScreen = new Vec2d(mouseX, mouseY);
        Corner corner = findCorner(renderState, scene, mouseScreen);
        if (corner != null) {
            beginResize(scene, corner);
            return true;
        }
        Vec2d mouseWorld = renderState.screenToWorld(mouseScreen);
        if (contains(scene, mouseWorld)) {
            var transform = scene.getBackgroundTransform();
            dragMode = DragMode.MOVE;
            moveOffset = new Vec2d(
                    mouseWorld.x() - transform.getX(),
                    mouseWorld.y() - transform.getY());
        }
        return true;
    }

    public boolean mouseDragged(
            VttScene scene,
            RenderState renderState,
            double mouseX,
            double mouseY,
            int modifiers
    ) {
        if (!active || scene == null) return false;
        Vec2d mouseWorld = renderState.screenToWorld(new Vec2d(mouseX, mouseY));
        if (dragMode == DragMode.MOVE && moveOffset != null) {
            scene.getBackgroundTransform().setX(mouseWorld.x() - moveOffset.x());
            scene.getBackgroundTransform().setY(mouseWorld.y() - moveOffset.y());
        } else if (dragMode == DragMode.RESIZE && resizeAnchor != null && activeCorner != null) {
            resize(scene, mouseWorld, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
        }
        return true;
    }

    public boolean mouseReleased(int button) {
        if (!active) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) clearDrag();
        return true;
    }

    public void render(VRenderContext context, Font font, VttScene scene) {
        if (!active || scene == null) return;
        Corners corners = corners(scene);
        Vec2d topLeft = context.renderState().worldToScreen(corners.topLeft());
        Vec2d topRight = context.renderState().worldToScreen(corners.topRight());
        Vec2d bottomLeft = context.renderState().worldToScreen(corners.bottomLeft());
        Vec2d bottomRight = context.renderState().worldToScreen(corners.bottomRight());

        line(context, topLeft, topRight, BORDER_COLOR);
        line(context, topRight, bottomRight, BORDER_COLOR);
        line(context, bottomRight, bottomLeft, BORDER_COLOR);
        line(context, bottomLeft, topLeft, BORDER_COLOR);
        handle(context, topLeft);
        handle(context, topRight);
        handle(context, bottomLeft);
        handle(context, bottomRight);

        String title = "EDIT SCENE BACKGROUND";
        String help = "Drag: move  |  Corners: resize  |  Shift: keep ratio  |  R: reset  |  Enter: apply  |  Esc: cancel";
        int boxWidth = Math.max(font.width(title), font.width(help)) + 16;
        int boxX = (context.screenWidth() - boxWidth) / 2;
        context.graphics().fill(boxX, 8, boxX + boxWidth, 40, 0xE0101014);
        context.graphics().drawCenteredString(
                font, title, context.screenWidth() / 2, 13, BORDER_COLOR);
        context.graphics().drawCenteredString(
                font, help, context.screenWidth() / 2, 27, 0xFFFFFFFF);
    }

    private void beginResize(VttScene scene, Corner corner) {
        Corners corners = corners(scene);
        activeCorner = corner;
        resizeAnchor = switch (corner) {
            case TOP_LEFT -> corners.bottomRight();
            case TOP_RIGHT -> corners.bottomLeft();
            case BOTTOM_LEFT -> corners.topRight();
            case BOTTOM_RIGHT -> corners.topLeft();
        };
        resizeAspectRatio = actualWidth(scene) / actualHeight(scene);
        dragMode = DragMode.RESIZE;
    }

    private void resize(VttScene scene, Vec2d mouseWorld, boolean proportional) {
        double width = Math.max(MIN_SIZE, Math.abs(mouseWorld.x() - resizeAnchor.x()));
        double height = Math.max(MIN_SIZE, Math.abs(mouseWorld.y() - resizeAnchor.y()));
        if (proportional) {
            if (width / height > resizeAspectRatio) height = width / resizeAspectRatio;
            else width = height * resizeAspectRatio;
        }
        int signX = activeCorner == Corner.TOP_LEFT || activeCorner == Corner.BOTTOM_LEFT ? -1 : 1;
        int signY = activeCorner == Corner.TOP_LEFT || activeCorner == Corner.TOP_RIGHT ? -1 : 1;
        var transform = scene.getBackgroundTransform();
        transform.setX(resizeAnchor.x() + signX * width / 2.0);
        transform.setY(resizeAnchor.y() + signY * height / 2.0);
        transform.setScaleX(width / sourceWidth);
        transform.setScaleY(height / sourceHeight);
    }

    private Corner findCorner(RenderState renderState, VttScene scene, Vec2d mouseScreen) {
        Corners corners = corners(scene);
        if (near(mouseScreen, renderState.worldToScreen(corners.topLeft()))) {
            return Corner.TOP_LEFT;
        }
        if (near(mouseScreen, renderState.worldToScreen(corners.topRight()))) {
            return Corner.TOP_RIGHT;
        }
        if (near(mouseScreen, renderState.worldToScreen(corners.bottomLeft()))) {
            return Corner.BOTTOM_LEFT;
        }
        if (near(mouseScreen, renderState.worldToScreen(corners.bottomRight()))) {
            return Corner.BOTTOM_RIGHT;
        }
        return null;
    }

    private boolean contains(VttScene scene, Vec2d point) {
        var transform = scene.getBackgroundTransform();
        return Math.abs(point.x() - transform.getX()) <= actualWidth(scene) / 2.0
                && Math.abs(point.y() - transform.getY()) <= actualHeight(scene) / 2.0;
    }

    private Corners corners(VttScene scene) {
        var transform = scene.getBackgroundTransform();
        double halfWidth = actualWidth(scene) / 2.0;
        double halfHeight = actualHeight(scene) / 2.0;
        return new Corners(
                new Vec2d(transform.getX() - halfWidth, transform.getY() - halfHeight),
                new Vec2d(transform.getX() + halfWidth, transform.getY() - halfHeight),
                new Vec2d(transform.getX() - halfWidth, transform.getY() + halfHeight),
                new Vec2d(transform.getX() + halfWidth, transform.getY() + halfHeight));
    }

    private double actualWidth(VttScene scene) {
        return sourceWidth * scene.getBackgroundTransform().getScaleX();
    }

    private double actualHeight(VttScene scene) {
        return sourceHeight * scene.getBackgroundTransform().getScaleY();
    }

    private boolean near(Vec2d first, Vec2d second) {
        return Math.abs(first.x() - second.x()) <= HANDLE_HIT_RADIUS
                && Math.abs(first.y() - second.y()) <= HANDLE_HIT_RADIUS;
    }

    private void handle(VRenderContext context, Vec2d position) {
        int x = (int) Math.round(position.x());
        int y = (int) Math.round(position.y());
        context.graphics().fill(x - 4, y - 4, x + 5, y + 5, 0xFF111116);
        context.graphics().fill(x - 3, y - 3, x + 4, y + 4, HANDLE_COLOR);
    }

    private void line(VRenderContext context, Vec2d start, Vec2d end, int color) {
        int x1 = (int) Math.round(start.x());
        int y1 = (int) Math.round(start.y());
        int x2 = (int) Math.round(end.x());
        int y2 = (int) Math.round(end.y());
        if (y1 == y2) context.graphics().hLine(Math.min(x1, x2), Math.max(x1, x2), y1, color);
        else context.graphics().vLine(x1, Math.min(y1, y2), Math.max(y1, y2), color);
    }

    private ImageSize resolveSize(String backgroundAssetId) {
        if (backgroundAssetId.startsWith("library:")) {
            String id = backgroundAssetId.substring("library:".length());
            ImageSize thumbnailSize = thumbnailRegistry.findById(id)
                    .map(value -> new ImageSize(value.width(), value.height())).orElse(null);
            if (thumbnailSize != null) return thumbnailSize;
            thumbnailSize = thumbnailRegistry.findById(backgroundAssetId)
                    .map(value -> new ImageSize(value.width(), value.height())).orElse(null);
            if (thumbnailSize != null) return thumbnailSize;
        }
        String id = backgroundAssetId.startsWith("registered:")
                ? backgroundAssetId.substring("registered:".length()) : backgroundAssetId;
        AssetRef asset = assetRegistry.findById(id)
                .or(() -> assetRegistry.findById(backgroundAssetId))
                .orElse(null);
        if (asset instanceof BuiltInTextureAssetRef builtIn) {
            return new ImageSize(builtIn.textureWidth(), builtIn.textureHeight());
        }
        if (asset instanceof LibraryTextureAssetRef library) {
            return new ImageSize(library.textureWidth(), library.textureHeight());
        }
        return null;
    }

    private void clearDrag() {
        dragMode = DragMode.NONE;
        activeCorner = null;
        moveOffset = null;
        resizeAnchor = null;
    }

    private enum DragMode { NONE, MOVE, RESIZE }
    private enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    private record ImageSize(int width, int height) {}
    private record Corners(Vec2d topLeft, Vec2d topRight, Vec2d bottomLeft, Vec2d bottomRight) {}
}
