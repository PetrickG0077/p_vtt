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
import com.petrick.vtt.feature.tabletop.VttSceneMap;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final Map<String, OriginalMapState> originalMaps = new LinkedHashMap<>();
    private String selectedMapId;
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
        if (scene == null || scene.getMaps().isEmpty()
                && scene.getBackgroundAssetId() == null) {
            VTT.LOGGER.warn("[VTT Scene Edit] Cannot begin without a map");
            return false;
        }
        selectedMapId = scene.getMaps().stream()
                .filter(map -> map != null)
                .max(Comparator.comparingInt(VttSceneMap::getLayerIndex))
                .map(VttSceneMap::getId).orElse(null);
        String assetId = selectedAssetId(scene);
        ImageSize size = resolveSize(assetId);
        if (size == null) {
            VTT.LOGGER.warn("[VTT Scene Edit] Could not resolve dimensions for {}",
                    assetId);
            return false;
        }
        sourceWidth = size.width();
        sourceHeight = size.height();
        editedSceneId = scene.getId();
        originalTransform = scene.getBackgroundTransform().copy();
        originalMaps.clear();
        for (VttSceneMap map : scene.getMaps()) {
            if (map != null) {
                originalMaps.put(map.getId(), OriginalMapState.capture(map));
            }
        }
        dragMode = DragMode.NONE;
        activeCorner = null;
        active = true;
        VTT.LOGGER.info("[VTT Scene Edit] Started for scene {} using {} ({}x{})",
                scene.getId(), assetId, sourceWidth, sourceHeight);
        return true;
    }

    public boolean isActive() {
        return active;
    }

    public String getSelectedMapId() {
        return selectedMapId;
    }

    public void confirm() {
        active = false;
        editedSceneId = null;
        selectedMapId = null;
        originalMaps.clear();
        clearDrag();
    }

    public void cancel(VttScene scene) {
        if (active && scene != null && scene.getId().equals(editedSceneId)
                && originalTransform != null) {
            scene.setBackgroundTransform(originalTransform);
            scene.getMaps().clear();
            originalMaps.values().stream()
                    .map(OriginalMapState::restore)
                    .forEach(scene::addMap);
        }
        active = false;
        editedSceneId = null;
        selectedMapId = null;
        originalMaps.clear();
        clearDrag();
    }

    public void reset(VttScene scene) {
        if (active && scene != null && selectedAssetId(scene) != null) {
            selectedTransform(scene).reset();
        }
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
        if (selectedAssetId(scene) == null) return true;
        Vec2d mouseScreen = new Vec2d(mouseX, mouseY);
        Corner corner = findCorner(renderState, scene, mouseScreen);
        if (corner != null) {
            beginResize(scene, corner);
            return true;
        }
        Vec2d mouseWorld = renderState.screenToWorld(mouseScreen);
        VttSceneMap clickedMap = findMapAt(scene, mouseWorld);
        if (clickedMap != null && !clickedMap.getId().equals(selectedMapId)) {
            selectMap(scene, clickedMap);
        }
        if (contains(scene, mouseWorld)) {
            var transform = selectedTransform(scene);
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
            selectedTransform(scene).setX(mouseWorld.x() - moveOffset.x());
            selectedTransform(scene).setY(mouseWorld.y() - moveOffset.y());
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

    public void moveSelectedLayer(VttScene scene, LayerMove move) {
        VttSceneMap selected = selectedMap(scene);
        if (!active || selected == null || move == null) return;
        int maximum = Math.max(0, scene.getMaps().size() - 1);
        int target = switch (move) {
            case UP -> Math.min(maximum, selected.getLayerIndex() + 1);
            case DOWN -> Math.max(0, selected.getLayerIndex() - 1);
            case TOP -> maximum;
            case BOTTOM -> 0;
        };
        if (target == selected.getLayerIndex()) return;
        int previous = selected.getLayerIndex();
        for (VttSceneMap map : scene.getMaps()) {
            if (map == null || map == selected) continue;
            if (target > previous && map.getLayerIndex() > previous
                    && map.getLayerIndex() <= target) {
                map.setLayerIndex(map.getLayerIndex() - 1);
            } else if (target < previous && map.getLayerIndex() >= target
                    && map.getLayerIndex() < previous) {
                map.setLayerIndex(map.getLayerIndex() + 1);
            }
        }
        selected.setLayerIndex(target);
    }

    public void selectMap(VttScene scene, String mapId) {
        if (!active || scene == null || mapId == null) return;
        scene.getMaps().stream()
                .filter(map -> map != null && mapId.equals(map.getId()))
                .findFirst()
                .ifPresent(map -> selectMap(scene, map));
    }

    public boolean deleteSelectedMap(VttScene scene) {
        VttSceneMap selected = selectedMap(scene);
        if (!active || scene == null || selected == null) return false;
        if (!scene.removeMap(selected.getId())) return false;
        normalizeLayers(scene);
        VttSceneMap next = scene.getMaps().stream()
                .filter(map -> map != null)
                .max(Comparator.comparingInt(VttSceneMap::getLayerIndex))
                .orElse(null);
        if (next == null) {
            selectedMapId = null;
            sourceWidth = 0;
            sourceHeight = 0;
            clearDrag();
        } else {
            selectMap(scene, next);
        }
        return true;
    }

    public void render(VRenderContext context, Font font, VttScene scene) {
        if (!active || scene == null) return;
        if (selectedAssetId(scene) == null) {
            renderHeader(context, font, scene,
                    "No map selected - drag one from Map Catalog");
            return;
        }
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

        renderHeader(context, font, scene,
                "Click: select  |  Drag: move  |  Corners: resize  |  Delete: remove  |  Middle drag: camera  |  Enter: apply");
    }

    private void renderHeader(
            VRenderContext context, Font font, VttScene scene, String help
    ) {
        String title = "EDIT SCENE MAP"
                + (selectedMap(scene) == null ? "" : " - " + selectedMap(scene).getDisplayName());
        int boxWidth = Math.max(font.width(title), font.width(help)) + 16;
        int boxX = (context.screenWidth() - boxWidth) / 2;
        context.graphics().fill(boxX, 8, boxX + boxWidth, 40, 0xE0101014);
        context.graphics().drawCenteredString(
                font, title, context.screenWidth() / 2, 13, BORDER_COLOR);
        context.graphics().drawCenteredString(
                font, help, context.screenWidth() / 2, 27, 0xFFFFFFFF);
    }

    private void normalizeLayers(VttScene scene) {
        List<VttSceneMap> ordered = scene.getMaps().stream()
                .filter(map -> map != null)
                .sorted(Comparator.comparingInt(VttSceneMap::getLayerIndex))
                .toList();
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).setLayerIndex(index);
        }
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

    private VttSceneMap findMapAt(VttScene scene, Vec2d point) {
        return scene.getMaps().stream()
                .filter(map -> map != null && map.isVisible())
                .sorted(Comparator.comparingInt(VttSceneMap::getLayerIndex).reversed())
                .filter(map -> contains(map, point))
                .findFirst().orElse(null);
    }

    private boolean contains(VttSceneMap map, Vec2d point) {
        ImageSize size = resolveSize(map.getAssetId());
        if (size == null) return false;
        VttSceneBackgroundTransform transform = map.getTransform();
        double halfWidth = size.width() * transform.getScaleX() / 2.0;
        double halfHeight = size.height() * transform.getScaleY() / 2.0;
        return Math.abs(point.x() - transform.getX()) <= halfWidth
                && Math.abs(point.y() - transform.getY()) <= halfHeight;
    }

    private void selectMap(VttScene scene, VttSceneMap map) {
        ImageSize size = resolveSize(map.getAssetId());
        if (size == null) return;
        selectedMapId = map.getId();
        sourceWidth = size.width();
        sourceHeight = size.height();
        clearDrag();
    }

    private String selectedAssetId(VttScene scene) {
        VttSceneMap map = selectedMap(scene);
        return map == null ? scene.getBackgroundAssetId() : map.getAssetId();
    }

    private VttSceneMap selectedMap(VttScene scene) {
        if (scene == null || selectedMapId == null) return null;
        return scene.getMaps().stream()
                .filter(map -> map != null && selectedMapId.equals(map.getId()))
                .findFirst().orElse(null);
    }

    private VttSceneBackgroundTransform selectedTransform(VttScene scene) {
        VttSceneMap map = selectedMap(scene);
        return map == null ? scene.getBackgroundTransform() : map.getTransform();
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
        var transform = selectedTransform(scene);
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
        var transform = selectedTransform(scene);
        return Math.abs(point.x() - transform.getX()) <= actualWidth(scene) / 2.0
                && Math.abs(point.y() - transform.getY()) <= actualHeight(scene) / 2.0;
    }

    private Corners corners(VttScene scene) {
        var transform = selectedTransform(scene);
        double halfWidth = actualWidth(scene) / 2.0;
        double halfHeight = actualHeight(scene) / 2.0;
        return new Corners(
                new Vec2d(transform.getX() - halfWidth, transform.getY() - halfHeight),
                new Vec2d(transform.getX() + halfWidth, transform.getY() - halfHeight),
                new Vec2d(transform.getX() - halfWidth, transform.getY() + halfHeight),
                new Vec2d(transform.getX() + halfWidth, transform.getY() + halfHeight));
    }

    private double actualWidth(VttScene scene) {
        return sourceWidth * selectedTransform(scene).getScaleX();
    }

    private double actualHeight(VttScene scene) {
        return sourceHeight * selectedTransform(scene).getScaleY();
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
        if (backgroundAssetId == null || backgroundAssetId.isBlank()) return null;
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
    public enum LayerMove { UP, DOWN, TOP, BOTTOM }
    private enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    private record ImageSize(int width, int height) {}
    private record Corners(Vec2d topLeft, Vec2d topRight, Vec2d bottomLeft, Vec2d bottomRight) {}
    private record OriginalMapState(
            String id,
            String displayName,
            String sourceMapDefinitionId,
            String assetId,
            VttSceneBackgroundTransform transform,
            int layerIndex,
            boolean visible,
            com.petrick.vtt.feature.map.MapTextureMode textureMode
    ) {
        private static OriginalMapState capture(VttSceneMap map) {
            return new OriginalMapState(
                    map.getId(), map.getDisplayName(), map.getSourceMapDefinitionId(),
                    map.getAssetId(), map.getTransform().copy(), map.getLayerIndex(),
                    map.isVisible(), map.getTextureMode());
        }

        private VttSceneMap restore() {
            VttSceneMap map = new VttSceneMap(
                    id, displayName, sourceMapDefinitionId, assetId, textureMode);
            map.setTransform(transform);
            map.setLayerIndex(layerIndex);
            map.setVisible(visible);
            return map;
        }
    }
}
