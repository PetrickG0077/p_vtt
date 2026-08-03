package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.editor.hud.EditorHudTheme;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.selection.SelectionManager;
import com.petrick.vtt.feature.tabletop.VttLight;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneMap;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.feature.tabletop.VttAttachmentBinding;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** A selectable scene tree: token roots own their bound attachments and attached lights. */
public final class SceneOutlinerOverlay {
    private static final int PANEL_WIDTH = 220;
    private static final int PADDING = 8;
    private static final int LINE_HEIGHT = 10;
    private static final int PANEL_X = 10;
    private static final int PANEL_Y = 105;
    private static final int MAX_VISIBLE_MAPS = 5;
    private static final int MAX_VISIBLE_ENTRIES = 14;
    private static final int PANEL_BACKGROUND = 0xAA000000;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFFDDDDDD;
    private static final int MUTED_TEXT_COLOR = 0xFF888888;

    private int mapScrollOffset;
    private int entryScrollOffset;
    private final Set<String> collapsedObjectIds = new HashSet<>();
    private ScrollSection draggingScrollbar = ScrollSection.NONE;
    private String hierarchyDragCandidateId;
    private String hierarchyDraggedId;
    private String hierarchyDropTargetId;
    private boolean hierarchyDropTargetValid;
    private long hierarchyDragPressedAt;
    private double hierarchyDragStartX;
    private double hierarchyDragStartY;

    private static final long HIERARCHY_HOLD_MS = 500L;
    private static final double HIERARCHY_DRAG_DISTANCE_SQUARED = 16.0;

    public void render(VRenderContext context, Font font, CanvasScene canvasScene, VttScene vttScene,
                       SelectionManager selectionManager, String selectedMapId, String selectedLightId) {
        List<VttSceneMap> maps = sortedMaps(vttScene);
        List<TreeEntry> entries = treeEntries(canvasScene, vttScene);
        Layout layout = layout(maps.size(), entries.size());
        renderPanelBackground(context, PANEL_X, PANEL_Y, PANEL_WIDTH, layout.panelHeight());
        int textX = PANEL_X + PADDING;
        drawLine(context, font, "Scene Outliner", textX, PANEL_Y + PADDING, TITLE_COLOR);
        drawLine(context, font, "Maps: " + maps.size(), textX, layout.mapsTitleY(), TEXT_COLOR);
        if (maps.isEmpty()) {
            drawLine(context, font, "  No maps", textX, layout.firstMapY(), MUTED_TEXT_COLOR);
        } else {
            for (int index = 0; index < layout.visibleMaps(); index++) {
                VttSceneMap map = maps.get(mapScrollOffset + index);
                boolean selected = map.getId().equals(selectedMapId);
                String text = (selected ? "> " : "  ") + (map.isVisible() ? "[V] " : "[H] ")
                        + "[" + map.getLayerIndex() + "] " + map.getDisplayName();
                drawLine(context, font, text, textX, layout.firstMapY() + index * LINE_HEIGHT,
                        selected ? EditorHudTheme.opaqueSelection()
                                : map.isVisible() ? TEXT_COLOR : MUTED_TEXT_COLOR);
            }
            if (maps.size() > MAX_VISIBLE_MAPS) {
                drawLine(context, font, range(mapScrollOffset, layout.visibleMaps(), maps.size()), textX,
                        layout.mapRangeY(), MUTED_TEXT_COLOR);
                EditorScrollbar.render(context, scrollbarX(), layout.firstMapY(), 4,
                        MAX_VISIBLE_MAPS * LINE_HEIGHT, maps.size(), MAX_VISIBLE_MAPS,
                        mapScrollOffset, EditorHudTheme.outline());
            }
        }

        drawLine(context, font, "Scene tree: " + entries.size(), textX, layout.entriesTitleY(), TEXT_COLOR);
        if (entries.isEmpty()) {
            drawLine(context, font, "  No objects or lights", textX, layout.firstEntryY(), MUTED_TEXT_COLOR);
        } else {
            for (int index = 0; index < layout.visibleEntries(); index++) {
                TreeEntry entry = entries.get(entryScrollOffset + index);
                int rowY = layout.firstEntryY() + index * LINE_HEIGHT;
                if (hierarchyDraggedId != null && entry.object != null
                        && entry.id.equals(hierarchyDropTargetId)) {
                    int color = hierarchyDropTargetValid
                            ? 0x8833AA66 : 0x88AA3333;
                    context.graphics().fill(PANEL_X + 2, rowY - 1,
                            PANEL_X + PANEL_WIDTH - 2, rowY + LINE_HEIGHT, color);
                }
                boolean selected = entry.object != null
                        ? selectionManager.isSelected(entry.id)
                        : entry.id.equals(selectedLightId);
                boolean visible = entry.object != null ? entry.object.visible() : entry.light.isEnabled();
                String marker = selected ? "> " : "  ";
                String expansion = entry.expandable
                        ? (collapsedObjectIds.contains(entry.id) ? "[+] " : "[-] ") : "    ";
                String text = marker + "  ".repeat(entry.depth) + expansion + entry.label;
                drawLine(context, font, text, textX, rowY,
                        selected ? EditorHudTheme.opaqueSelection() : visible ? TEXT_COLOR : MUTED_TEXT_COLOR);
            }
            if (entries.size() > MAX_VISIBLE_ENTRIES) {
                drawLine(context, font, range(entryScrollOffset, layout.visibleEntries(), entries.size()), textX,
                        layout.entryRangeY(), MUTED_TEXT_COLOR);
                EditorScrollbar.render(context, scrollbarX(), layout.firstEntryY(), 4,
                        MAX_VISIBLE_ENTRIES * LINE_HEIGHT, entries.size(), MAX_VISIBLE_ENTRIES,
                        entryScrollOffset, EditorHudTheme.outline());
            }
        }
        if (hierarchyDraggedId != null) {
            int detachY = layout.detachY();
            int detachColor = isInsideDetachZone(lastHierarchyMouseX, lastHierarchyMouseY,
                    layout) ? 0xAA9A3A3A : 0xAA351C1C;
            context.graphics().fill(PANEL_X + 2, detachY,
                    PANEL_X + PANEL_WIDTH - 2, detachY + 13, detachColor);
            drawLine(context, font, "Detach attachment", textX + 4,
                    detachY + 2, TITLE_COLOR);
        }
    }

    private double lastHierarchyMouseX;
    private double lastHierarchyMouseY;

    /** Arms hierarchy dragging for attachment rows only. */
    public boolean beginHierarchyDrag(CanvasScene canvasScene, VttScene scene,
                                      double mouseX, double mouseY) {
        TreeEntry entry = entryAt(canvasScene, scene, mouseX, mouseY);
        if (entry == null || entry.object == null || !entry.attachment) return false;
        hierarchyDragCandidateId = entry.id;
        hierarchyDragPressedAt = System.currentTimeMillis();
        hierarchyDragStartX = mouseX;
        hierarchyDragStartY = mouseY;
        lastHierarchyMouseX = mouseX;
        lastHierarchyMouseY = mouseY;
        return true;
    }

    public boolean mouseDraggedHierarchy(CanvasScene canvasScene, VttScene scene,
                                         double mouseX, double mouseY,
                                         Predicate<String> validTarget) {
        if (hierarchyDragCandidateId == null && hierarchyDraggedId == null) return false;
        lastHierarchyMouseX = mouseX;
        lastHierarchyMouseY = mouseY;
        if (hierarchyDraggedId == null) {
            double dx = mouseX - hierarchyDragStartX;
            double dy = mouseY - hierarchyDragStartY;
            boolean held = System.currentTimeMillis() - hierarchyDragPressedAt >= HIERARCHY_HOLD_MS;
            boolean moved = dx * dx + dy * dy >= HIERARCHY_DRAG_DISTANCE_SQUARED;
            if (!held && !moved) return true;
            hierarchyDraggedId = hierarchyDragCandidateId;
        }
        TreeEntry target = entryAt(canvasScene, scene, mouseX, mouseY);
        hierarchyDropTargetId = target != null && target.object != null ? target.id : null;
        hierarchyDropTargetValid = hierarchyDropTargetId != null
                && validTarget.test(hierarchyDropTargetId);
        return true;
    }

    public HierarchyDrop mouseReleasedHierarchy(CanvasScene canvasScene, VttScene scene,
                                                 double mouseX, double mouseY) {
        boolean consumed = hierarchyDragCandidateId != null || hierarchyDraggedId != null;
        if (!consumed) return HierarchyDrop.none();
        String attachmentId = hierarchyDraggedId;
        Layout layout = layout(sortedMaps(scene).size(), treeEntries(canvasScene, scene).size());
        boolean detach = attachmentId != null && isInsideDetachZone(mouseX, mouseY, layout);
        String targetId = attachmentId != null && hierarchyDropTargetValid
                ? hierarchyDropTargetId : null;
        clearHierarchyDrag();
        return new HierarchyDrop(true, attachmentId, targetId, detach);
    }

    public void clearHierarchyDrag() {
        hierarchyDragCandidateId = null;
        hierarchyDraggedId = null;
        hierarchyDropTargetId = null;
        hierarchyDropTargetValid = false;
    }

    public String hierarchyDraggedId() {
        return hierarchyDraggedId != null ? hierarchyDraggedId : hierarchyDragCandidateId;
    }

    private boolean isInsideDetachZone(double mouseX, double mouseY, Layout layout) {
        return mouseX >= PANEL_X + 2 && mouseX <= PANEL_X + PANEL_WIDTH - 2
                && mouseY >= layout.detachY() && mouseY <= layout.detachY() + 13;
    }

    public Optional<String> findMapIdAt(VttScene scene, CanvasScene canvasScene, double mouseX, double mouseY) {
        List<VttSceneMap> maps = sortedMaps(scene);
        Layout layout = layout(maps.size(), treeEntries(canvasScene, scene).size());
        if (!insidePanel(mouseX, mouseY, layout)) return Optional.empty();
        for (int index = 0; index < layout.visibleMaps(); index++) {
            if (insideRow(mouseY, layout.firstMapY() + index * LINE_HEIGHT)) {
                return Optional.of(maps.get(mapScrollOffset + index).getId());
            }
        }
        return Optional.empty();
    }

    public Optional<String> findObjectIdAt(CanvasScene canvasScene, VttScene scene,
                                           double mouseX, double mouseY) {
        TreeEntry entry = entryAt(canvasScene, scene, mouseX, mouseY);
        return entry != null && entry.object != null ? Optional.of(entry.id) : Optional.empty();
    }

    /** Handles the small expansion control at the beginning of a tree row. */
    public boolean toggleExpansionAt(
            CanvasScene canvasScene, VttScene scene, double mouseX, double mouseY
    ) {
        TreeEntry entry = entryAt(canvasScene, scene, mouseX, mouseY);
        if (entry == null || entry.object == null || !entry.expandable) return false;
        int expansionRight = PANEL_X + PADDING + 14 + entry.depth * 8 + 26;
        if (mouseX > expansionRight) return false;
        if (!collapsedObjectIds.add(entry.id)) collapsedObjectIds.remove(entry.id);
        return true;
    }

    public Optional<String> findLightIdAt(CanvasScene canvasScene, VttScene scene,
                                          double mouseX, double mouseY) {
        TreeEntry entry = entryAt(canvasScene, scene, mouseX, mouseY);
        return entry != null && entry.light != null ? Optional.of(entry.id) : Optional.empty();
    }

    public boolean mouseClickedScrollbar(CanvasScene canvasScene, VttScene scene,
                                         double mouseX, double mouseY) {
        List<VttSceneMap> maps = sortedMaps(scene);
        List<TreeEntry> entries = treeEntries(canvasScene, scene);
        Layout layout = layout(maps.size(), entries.size());
        if (maps.size() > MAX_VISIBLE_MAPS && EditorScrollbar.contains(mouseX, mouseY,
                scrollbarX() - 3, layout.firstMapY(), 10, MAX_VISIBLE_MAPS * LINE_HEIGHT)) {
            draggingScrollbar = ScrollSection.MAPS;
            mapScrollOffset = EditorScrollbar.offsetForMouse(mouseY, layout.firstMapY(),
                    MAX_VISIBLE_MAPS * LINE_HEIGHT, maps.size(), MAX_VISIBLE_MAPS);
            return true;
        }
        if (entries.size() > MAX_VISIBLE_ENTRIES && EditorScrollbar.contains(mouseX, mouseY,
                scrollbarX() - 3, layout.firstEntryY(), 10, MAX_VISIBLE_ENTRIES * LINE_HEIGHT)) {
            draggingScrollbar = ScrollSection.ENTRIES;
            entryScrollOffset = EditorScrollbar.offsetForMouse(mouseY, layout.firstEntryY(),
                    MAX_VISIBLE_ENTRIES * LINE_HEIGHT, entries.size(), MAX_VISIBLE_ENTRIES);
            return true;
        }
        return false;
    }

    public boolean mouseDraggedScrollbar(CanvasScene canvasScene, VttScene scene, double mouseY) {
        List<VttSceneMap> maps = sortedMaps(scene);
        List<TreeEntry> entries = treeEntries(canvasScene, scene);
        Layout layout = layout(maps.size(), entries.size());
        if (draggingScrollbar == ScrollSection.MAPS) {
            mapScrollOffset = EditorScrollbar.offsetForMouse(mouseY, layout.firstMapY(),
                    MAX_VISIBLE_MAPS * LINE_HEIGHT, maps.size(), MAX_VISIBLE_MAPS);
            return true;
        }
        if (draggingScrollbar == ScrollSection.ENTRIES) {
            entryScrollOffset = EditorScrollbar.offsetForMouse(mouseY, layout.firstEntryY(),
                    MAX_VISIBLE_ENTRIES * LINE_HEIGHT, entries.size(), MAX_VISIBLE_ENTRIES);
            return true;
        }
        return false;
    }

    public boolean mouseReleasedScrollbar() {
        boolean dragging = draggingScrollbar != ScrollSection.NONE;
        draggingScrollbar = ScrollSection.NONE;
        return dragging;
    }

    public boolean mouseScrolled(CanvasScene canvasScene, VttScene scene,
                                 double mouseX, double mouseY, double scrollY) {
        List<VttSceneMap> maps = sortedMaps(scene);
        List<TreeEntry> entries = treeEntries(canvasScene, scene);
        Layout layout = layout(maps.size(), entries.size());
        if (!insidePanel(mouseX, mouseY, layout)) return false;
        int delta = scrollY < 0 ? 1 : scrollY > 0 ? -1 : 0;
        if (mouseY < layout.entriesTitleY()) {
            mapScrollOffset = EditorScrollbar.clampOffset(mapScrollOffset + delta,
                    maps.size(), MAX_VISIBLE_MAPS);
        } else {
            entryScrollOffset = EditorScrollbar.clampOffset(entryScrollOffset + delta,
                    entries.size(), MAX_VISIBLE_ENTRIES);
        }
        return true;
    }

    public boolean contains(CanvasScene canvasScene, VttScene scene, double mouseX, double mouseY) {
        return insidePanel(mouseX, mouseY,
                layout(sortedMaps(scene).size(), treeEntries(canvasScene, scene).size()));
    }

    private TreeEntry entryAt(CanvasScene canvasScene, VttScene scene, double mouseX, double mouseY) {
        List<TreeEntry> entries = treeEntries(canvasScene, scene);
        Layout layout = layout(sortedMaps(scene).size(), entries.size());
        if (!insidePanel(mouseX, mouseY, layout)) return null;
        for (int index = 0; index < layout.visibleEntries(); index++) {
            if (insideRow(mouseY, layout.firstEntryY() + index * LINE_HEIGHT)) {
                return entries.get(entryScrollOffset + index);
            }
        }
        return null;
    }

    private List<TreeEntry> treeEntries(CanvasScene canvasScene, VttScene scene) {
        if (canvasScene == null) return List.of();
        Map<String, VttSceneObject> sceneObjects = new HashMap<>();
        if (scene != null) {
            for (VttSceneObject object : scene.getObjects()) {
                if (object != null) sceneObjects.put(object.getId(), object);
            }
        }
        Map<String, List<CanvasObject>> children = new HashMap<>();
        List<CanvasObject> roots = new ArrayList<>();
        for (CanvasObject object : canvasScene.getObjects()) {
            VttSceneObject metadata = sceneObjects.get(object.id());
            String parentId = metadata != null && metadata.isAttachment()
                    && metadata.getAttachmentBinding() != null
                    && metadata.getAttachmentBinding().isBound()
                    ? metadata.getAttachmentBinding().getTargetObjectId() : null;
            if (parentId == null) roots.add(object);
            else children.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(object);
        }
        List<TreeEntry> entries = new ArrayList<>();
        Set<String> renderedObjects = new HashSet<>();
        Set<String> renderedLights = new HashSet<>();
        for (CanvasObject root : roots) {
            appendObject(entries, root, 0, children, scene, renderedObjects, renderedLights);
        }
        for (CanvasObject object : canvasScene.getObjects()) {
            if (!renderedObjects.contains(object.id())) {
                appendObject(entries, object, 0, children, scene, renderedObjects, renderedLights);
            }
        }
        if (scene != null) {
            for (VttLight light : scene.getLights()) {
                if (light != null && !renderedLights.contains(light.getId())) {
                    entries.add(TreeEntry.light(light, 0));
                }
            }
        }
        return entries;
    }

    private void appendObject(List<TreeEntry> entries, CanvasObject object, int depth,
                              Map<String, List<CanvasObject>> children, VttScene scene,
                              Set<String> renderedObjects, Set<String> renderedLights) {
        if (!renderedObjects.add(object.id())) return;
        List<CanvasObject> childObjects = children.getOrDefault(object.id(), List.of());
        boolean hasAttachedLights = scene != null && scene.getLights().stream()
                .anyMatch(light -> light != null && object.id().equals(light.getAttachedToObjectId()));
        boolean expandable = !childObjects.isEmpty() || hasAttachedLights;
        VttSceneObject metadata = scene == null ? null : scene.getObjects().stream()
                .filter(value -> value != null && object.id().equals(value.getId()))
                .findFirst().orElse(null);
        entries.add(TreeEntry.object(object, metadata, depth, expandable));
        if (expandable && collapsedObjectIds.contains(object.id())) return;
        for (CanvasObject child : childObjects) {
            appendObject(entries, child, depth + 1, children, scene, renderedObjects, renderedLights);
        }
        if (scene == null) return;
        for (VttLight light : scene.getLights()) {
            if (light != null && object.id().equals(light.getAttachedToObjectId())
                    && renderedLights.add(light.getId())) {
                entries.add(TreeEntry.light(light, depth + 1));
            }
        }
    }

    private Layout layout(int mapCount, int entryCount) {
        mapScrollOffset = EditorScrollbar.clampOffset(mapScrollOffset, mapCount, MAX_VISIBLE_MAPS);
        entryScrollOffset = EditorScrollbar.clampOffset(entryScrollOffset, entryCount, MAX_VISIBLE_ENTRIES);
        int visibleMaps = Math.min(MAX_VISIBLE_MAPS, mapCount);
        int visibleEntries = Math.min(MAX_VISIBLE_ENTRIES, entryCount);
        int mapsTitleY = PANEL_Y + PADDING + LINE_HEIGHT + 4;
        int firstMapY = mapsTitleY + LINE_HEIGHT + 2;
        int mapRangeY = firstMapY + Math.max(1, visibleMaps) * LINE_HEIGHT;
        int entriesTitleY = mapRangeY + (mapCount > MAX_VISIBLE_MAPS ? LINE_HEIGHT : 0) + 5;
        int firstEntryY = entriesTitleY + LINE_HEIGHT + 2;
        int entryRangeY = firstEntryY + Math.max(1, visibleEntries) * LINE_HEIGHT;
        int bottom = entryRangeY + (entryCount > MAX_VISIBLE_ENTRIES ? LINE_HEIGHT : 0);
        int detachY = bottom + 3;
        if (hierarchyDraggedId != null) bottom = detachY + 13;
        return new Layout(visibleMaps, visibleEntries, mapsTitleY, firstMapY, mapRangeY,
                entriesTitleY, firstEntryY, entryRangeY, detachY,
                bottom - PANEL_Y + PADDING);
    }

    private List<VttSceneMap> sortedMaps(VttScene scene) {
        if (scene == null) return List.of();
        return scene.getMaps().stream().filter(map -> map != null)
                .sorted(Comparator.comparingInt(VttSceneMap::getLayerIndex).reversed()).toList();
    }

    private boolean insidePanel(double x, double y, Layout layout) {
        return x >= PANEL_X && x <= PANEL_X + PANEL_WIDTH
                && y >= PANEL_Y && y <= PANEL_Y + layout.panelHeight();
    }

    private boolean insideRow(double mouseY, int rowY) {
        return mouseY >= rowY && mouseY <= rowY + LINE_HEIGHT;
    }

    private int scrollbarX() { return PANEL_X + PANEL_WIDTH - PADDING - 4; }
    private String range(int offset, int visible, int total) { return (offset + 1) + "-" + (offset + visible) + " / " + total; }

    private void renderPanelBackground(VRenderContext context, int x, int y, int width, int height) {
        context.graphics().fill(x, y, x + width, y + height, PANEL_BACKGROUND);
        context.graphics().hLine(x, x + width, y, EditorHudTheme.outline());
        context.graphics().hLine(x, x + width, y + height, EditorHudTheme.outline());
        context.graphics().vLine(x, y, y + height, EditorHudTheme.outline());
        context.graphics().vLine(x + width, y, y + height, EditorHudTheme.outline());
    }

    private void drawLine(VRenderContext context, Font font, String text, int x, int y, int color) {
        context.graphics().drawString(font, text, x, y, color, false);
    }

    private enum ScrollSection { NONE, MAPS, ENTRIES }

    private record TreeEntry(
            String id, CanvasObject object, VttLight light, int depth, String label,
            boolean expandable, boolean attachment
    ) {
        private static TreeEntry object(CanvasObject object, VttSceneObject metadata,
                                        int depth, boolean expandable) {
            boolean attachment = metadata != null && metadata.isAttachment();
            String badge = "";
            if (attachment) {
                VttAttachmentBinding binding = metadata.getAttachmentBinding();
                if (binding != null) {
                    String scope = binding.getParentStateId() == null
                            ? "G" : "S:" + binding.getParentStateId();
                    badge = " [" + binding.getAnchor().name() + "][" + scope + "]";
                } else {
                    badge = " [DETACHED]";
                }
            }
            return new TreeEntry(object.id(), object, null, depth,
                    (object.visible() ? "[V] " : "[H] ") + object.displayName() + badge,
                    expandable, attachment);
        }

        private static TreeEntry light(VttLight light, int depth) {
            return new TreeEntry(light.getId(), null, light, depth,
                    (light.isEnabled() ? "[V] " : "[H] ") + "Light: " + light.getType().name(),
                    false, false);
        }
    }

    public record HierarchyDrop(boolean consumed, String attachmentId,
                                String targetId, boolean detach) {
        private static HierarchyDrop none() {
            return new HierarchyDrop(false, null, null, false);
        }
        public boolean hasAction() {
            return attachmentId != null && (targetId != null || detach);
        }
    }

    private record Layout(int visibleMaps, int visibleEntries, int mapsTitleY, int firstMapY,
                          int mapRangeY, int entriesTitleY, int firstEntryY, int entryRangeY,
                          int detachY, int panelHeight) {}
}
