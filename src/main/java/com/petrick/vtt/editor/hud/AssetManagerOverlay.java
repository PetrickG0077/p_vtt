package com.petrick.vtt.editor.hud;

import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.asset.AssetRef;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.feature.asset.LibraryTextureAssetRef;
import com.petrick.vtt.feature.asset.animation.AnimatedTextureService;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnail;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnailRegistry;
import com.petrick.vtt.feature.canvas.CanvasObjectState;
import com.petrick.vtt.feature.canvas.visual.CanvasVisual;
import com.petrick.vtt.feature.canvas.visual.CanvasVisualRenderer;
import com.petrick.vtt.feature.map.MapDefinition;
import com.petrick.vtt.feature.map.MapDefinitionRegistry;
import com.petrick.vtt.feature.map.persistence.CreatedMapStorage;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttTabletop;
import com.petrick.vtt.feature.tabletop.persistence.TabletopStorage;
import com.petrick.vtt.feature.token.TokenDefinition;
import com.petrick.vtt.feature.token.TokenDefinitionRegistry;
import com.petrick.vtt.feature.token.persistence.CreatedTokenStorage;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Central searchable manager for scene, map and token definitions. */
public final class AssetManagerOverlay {
    private static final int CARD_WIDTH = 112;
    private static final int CARD_HEIGHT = 116;
    private static final int CARD_GAP = 10;
    private static final int HEADER_HEIGHT = 54;
    private static final int TABS_HEIGHT = 34;
    private static final int SCROLLBAR_WIDTH = 5;
    private static final int MIN_SCROLLBAR_THUMB_HEIGHT = 18;
    private static final ResourceLocation SCENE_ICON = ResourceLocation.fromNamespaceAndPath(
            VTT.MOD_ID, "textures/gui/editor_hud/scenes.png");
    private static final ResourceLocation MAP_ICON = ResourceLocation.fromNamespaceAndPath(
            VTT.MOD_ID, "textures/gui/editor_hud/maps.png");
    private static final ResourceLocation TOKEN_ICON = ResourceLocation.fromNamespaceAndPath(
            VTT.MOD_ID, "textures/gui/editor_hud/tokens.png");
    private static final ResourceLocation EDIT_ICON = ResourceLocation.fromNamespaceAndPath(
            VTT.MOD_ID, "textures/gui/editor_hud/edit.png");
    private static final ResourceLocation TRASH_ICON = ResourceLocation.fromNamespaceAndPath(
            VTT.MOD_ID, "textures/gui/editor_hud/trash.png");
    private static final ResourceLocation DUPLICATE_ICON = ResourceLocation.fromNamespaceAndPath(
            VTT.MOD_ID, "textures/gui/editor_hud/duplicate.png");
    private static final ResourceLocation FOLDER_ICON = ResourceLocation.fromNamespaceAndPath(
            VTT.MOD_ID, "textures/gui/editor_hud/folder.png");
    private static final ResourceLocation REFRESH_ICON = ResourceLocation.fromNamespaceAndPath(
            VTT.MOD_ID, "textures/gui/editor_hud/redo.png");
    private static final long DRAG_HOLD_MS = 500L;
    private static final long DRAG_AUTO_SCROLL_INTERVAL_MS = 120L;
    private static final int DRAG_AUTO_SCROLL_EDGE = 28;
    private final CanvasVisualRenderer visualRenderer =
            new CanvasVisualRenderer(new AnimatedTextureService());
    private final SceneThumbnailRenderer sceneThumbnailRenderer;

    private Section section = Section.SCENES;
    private String search = "";
    private String selectedId;
    private final LinkedHashSet<String> selectedIds = new LinkedHashSet<>();
    private String selectionAnchorId;
    private final EnumMap<Section, Integer> scrollRows = new EnumMap<>(Section.class);
    private final EnumMap<Section, String> currentFolders = new EnumMap<>(Section.class);
    private String lastClickedKey;
    private long lastClickedAt;
    private boolean searchFocused;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private Item pressedItem;
    private long pressedAt;
    private double pressedX;
    private double pressedY;
    private Item draggingItem;
    private List<Item> draggingItems = List.of();
    private String dragTargetFolder;
    private long lastDragAutoScrollAt;
    private List<Breadcrumb> renderedBreadcrumbs = List.of();
    private String pendingOperation;

    public AssetManagerOverlay(TabletopStorage tabletopStorage) {
        this.sceneThumbnailRenderer = new SceneThumbnailRenderer(tabletopStorage);
        for (Section candidate : Section.values()) {
            scrollRows.put(candidate, 0);
            currentFolders.put(candidate, "");
        }
    }

    public void render(
            VRenderContext context,
            Font font,
            VttTabletop tabletop,
            VttScene activeScene,
            MapDefinitionRegistry maps,
            TokenDefinitionRegistry tokens,
            AssetRegistry assets,
            AssetThumbnailRegistry thumbnails
    ) {
        Bounds panel = panel(context.screenWidth(), context.screenHeight());
        reconcileCurrentFolder(tabletop);
        context.graphics().fill(
                panel.x(), panel.y(), panel.right(), panel.bottom(), 0xF21B1B1F);
        border(context, panel, EditorHudTheme.outline());
        context.graphics().fill(
                panel.x() + 1, panel.y() + 1, panel.right() - 1,
                panel.y() + HEADER_HEIGHT, 0xFF4A4A4A);
        String currentFolder = currentFolder();
        if (!currentFolder.isBlank()) {
            Bounds back = backBounds(panel);
            boolean parentDropTarget = draggingItem != null
                    && parent(currentFolder).equals(dragTargetFolder);
            if (parentDropTarget) {
                context.graphics().fill(
                        back.x() - 3, back.y(), back.right() + 3,
                        back.bottom(), EditorHudTheme.selection());
                thickBorder(context, new Bounds(
                        back.x() - 3, back.y(), back.width() + 6,
                        back.height()), EditorHudTheme.opaqueSelection());
            }
            context.graphics().drawString(
                    font, "<", back.x(), panel.y() + 18,
                    parentDropTarget ? 0xFFFFFFFF
                            : back.contains(context.mouseX(), context.mouseY())
                            ? EditorHudTheme.opaqueSelection() : 0xFFFFFFFF, false);
        }
        renderBreadcrumbs(context, font, panel, searchBounds(panel));

        Bounds searchBox = searchBounds(panel);
        context.graphics().fill(
                searchBox.x(), searchBox.y(), searchBox.right(), searchBox.bottom(),
                searchFocused ? 0xFF242D33 : 0xFF202024);
        border(context, searchBox, searchFocused
                ? EditorHudTheme.opaqueSelection() : EditorHudTheme.outline());
        String searchText = search.isBlank()
                ? (searchFocused ? "" : "Search...")
                : search;
        int searchColor = search.isBlank() ? 0xFF909096 : 0xFFFFFFFF;
        context.graphics().drawString(
                font, searchText,
                searchBox.x() + 10, searchBox.y() + (searchBox.height() - font.lineHeight) / 2,
                searchColor, false);
        if (searchFocused) {
            int caretX = searchBox.x() + 10 + font.width(
                    search.isBlank() ? "" : search);
            context.graphics().vLine(
                    caretX, searchBox.y() + 8, searchBox.bottom() - 8,
                    0xFFFFFFFF);
        }

        Bounds refresh = refreshBounds(panel);
        context.graphics().fill(
                refresh.x(), refresh.y(), refresh.right(), refresh.bottom(),
                refresh.contains(context.mouseX(), context.mouseY())
                        ? 0xFF56565C : 0xFF35353A);
        border(context, refresh, 0xFFFFFFFF);
        context.graphics().blit(
                REFRESH_ICON, refresh.x() + 7, refresh.y() + 7,
                16, 16, 0.0F, 0.0F, 32, 32, 32, 32);

        Bounds createFolder = createFolderBounds(panel);
        context.graphics().fill(
                createFolder.x(), createFolder.y(),
                createFolder.right(), createFolder.bottom(),
                createFolder.contains(context.mouseX(), context.mouseY())
                        ? 0xFF56565C : 0xFF35353A);
        border(context, createFolder, 0xFFFFFFFF);
        context.graphics().blit(
                FOLDER_ICON, createFolder.x() + 7, createFolder.y() + 7,
                16, 16, 0.0F, 0.0F, 32, 32, 32, 32);
        context.graphics().drawCenteredString(
                font, "+", createFolder.x() + 1 + createFolder.width() / 2,
                createFolder.y() + 2 + 11, 0xFFFFFFFF);

        Bounds add = addBounds(panel);
        context.graphics().fill(
                add.x(), add.y(), add.right(), add.bottom(),
                add.contains(context.mouseX(), context.mouseY())
                        ? 0xFFFFFFFF : 0xFFE5E5E5);
        drawScaledCenteredString(
                context, font, "ADD +",
                add.x() + add.width() / 2,
                add.y() + add.height() / 2,
                1.2F, 0xFF000000);

        for (Section candidate : Section.values()) {
            Bounds tab = tabBounds(panel, candidate);
            boolean active = candidate == section;
            context.graphics().fill(
                    tab.x(), tab.y(), tab.right(), tab.bottom(),
                    active ? 0xFF4A4A4A : 0xFF121214);
            int groupWidth = 16 + 5 + font.width(candidate.label);
            int groupX = tab.x() + (tab.width() - groupWidth) / 2;
            int iconY = tab.y() + (tab.height() - 16) / 2;
            context.graphics().blit(
                    candidate.icon(), groupX, iconY,
                    16, 16, 0.0F, 0.0F, 32, 32, 32, 32);
            context.graphics().drawString(
                    font, candidate.label, groupX + 21,
                    tab.y() + (tab.height() - font.lineHeight) / 2,
                    active ? 0xFFFFFFFF : 0xFFCCCCCC, false);
        }
        if (selectedIds.size() > 1 && panel.width() >= 560) {
            context.graphics().drawString(
                    font, selectedIds.size() + " selected",
                    panel.x() + 438,
                    panel.y() + HEADER_HEIGHT
                            + (TABS_HEIGHT - font.lineHeight) / 2,
                    0xFFCCCCCC, false);
        }
        List<Item> items = visibleItems(tabletop, maps, tokens);
        Grid grid = grid(panel, items.size());
        updateDraggingState(items);
        if (autoScrollWhileDragging(
                context.mouseX(), context.mouseY(), panel, grid)) {
            grid = grid(panel, items.size());
        }
        updateDragTarget(context.mouseX(), context.mouseY(), panel, grid, items);
        for (int index = grid.firstIndex(); index < grid.lastIndex(); index++) {
            int visibleIndex = index - grid.firstIndex();
            int column = visibleIndex % grid.columns();
            int row = visibleIndex / grid.columns();
            int x = grid.x() + column * (CARD_WIDTH + CARD_GAP);
            int y = grid.y() + row * (CARD_HEIGHT + CARD_GAP);
            Item item = items.get(index);
            Bounds card = new Bounds(x, y, CARD_WIDTH, CARD_HEIGHT);
            renderCard(context, font, card,
                    item, tabletop, activeScene, assets, thumbnails);
            if (draggingItem != null && draggingItems.stream()
                    .anyMatch(source -> sameItem(item, source))) {
                context.graphics().fill(
                        card.x(), card.y(), card.right(), card.bottom(), 0x88000000);
            }
            if (draggingItem != null && item.folder()
                    && item.path().equals(dragTargetFolder)) {
                thickBorder(context, card, EditorHudTheme.opaqueSelection());
            }
        }
        renderScrollbar(context, panel, grid);
        if (items.isEmpty()) {
            context.graphics().drawCenteredString(
                    font, search.isBlank() ? "No assets in this section" : "No search results",
                    panel.x() + panel.width() / 2, panel.y() + HEADER_HEIGHT
                            + TABS_HEIGHT + 30, 0xFF88888E);
        }
        if (draggingItem != null) {
            renderDragPreview(context, font, draggingItem, draggingItems.size());
        }
        if (pendingOperation != null && !pendingOperation.isBlank()) {
            String label = trim("Working: " + pendingOperation, 42);
            int statusWidth = font.width(label) + 12;
            int statusY = panel.bottom() - font.lineHeight - 9;
            context.graphics().fill(
                    panel.x() + 8, statusY - 3,
                    panel.x() + 8 + statusWidth, panel.bottom() - 3,
                    0xEE18181C);
            context.graphics().drawString(
                    font, label, panel.x() + 14, statusY,
                    EditorHudTheme.opaqueSelection(), false);
        }
        border(context, panel, EditorHudTheme.outline());
    }

    public Interaction mouseClicked(
            double mouseX,
            double mouseY,
            int button,
            int screenWidth,
            int screenHeight,
            int modifiers,
            VttTabletop tabletop,
            MapDefinitionRegistry maps,
            TokenDefinitionRegistry tokens
    ) {
        Bounds panel = panel(screenWidth, screenHeight);
        if (!panel.contains(mouseX, mouseY)) {
            searchFocused = false;
            return Interaction.none();
        }
        if (button != 0) {
            searchFocused = false;
            clearPressedItem();
            return Interaction.handled();
        }
        if (!currentFolder().isBlank()
                && backBounds(panel).contains(mouseX, mouseY)) {
            return new Interaction(
                    Action.BACK_FOLDER, section, currentFolder(), parent(currentFolder()), true);
        }
        for (Breadcrumb breadcrumb : renderedBreadcrumbs) {
            if (breadcrumb.path().equals(currentFolder())
                    || !breadcrumb.bounds().contains(mouseX, mouseY)) continue;
            return new Interaction(
                    Action.OPEN_FOLDER, section, breadcrumb.path(),
                    breadcrumb.path(), true);
        }
        if (searchBounds(panel).contains(mouseX, mouseY)) {
            searchFocused = true;
            return Interaction.handled();
        }
        searchFocused = false;
        if (refreshBounds(panel).contains(mouseX, mouseY)) {
            return new Interaction(Action.REFRESH, section, null, true);
        }
        if (createFolderBounds(panel).contains(mouseX, mouseY)) {
            return new Interaction(
                    Action.CREATE_FOLDER, section, currentFolder(), null, true);
        }
        if (addBounds(panel).contains(mouseX, mouseY)) {
            return new Interaction(Action.ADD, section, null, true);
        }
        for (Section candidate : Section.values()) {
            if (tabBounds(panel, candidate).contains(mouseX, mouseY)) {
                section = candidate;
                clearSelection();
                draggingScrollbar = false;
                clearPressedItem();
                return Interaction.handled();
            }
        }
        List<Item> items = visibleItems(tabletop, maps, tokens);
        Grid grid = grid(panel, items.size());
        Scrollbar scrollbar = scrollbar(panel, grid);
        if (scrollbar != null && scrollbar.track().contains(mouseX, mouseY)) {
            if (scrollbar.thumb().contains(mouseX, mouseY)) {
                draggingScrollbar = true;
                scrollbarGrabOffset = mouseY - scrollbar.thumb().y();
            } else {
                int direction = mouseY < scrollbar.thumb().y() ? -1 : 1;
                setScrollRow(scrollRow() + direction * grid.visibleRows());
                grid(panel, items.size());
            }
            return Interaction.handled();
        }
        for (int index = grid.firstIndex(); index < grid.lastIndex(); index++) {
            int visibleIndex = index - grid.firstIndex();
            int column = visibleIndex % grid.columns();
            int row = visibleIndex / grid.columns();
            Bounds card = new Bounds(
                    grid.x() + column * (CARD_WIDTH + CARD_GAP),
                    grid.y() + row * (CARD_HEIGHT + CARD_GAP),
                    CARD_WIDTH, CARD_HEIGHT);
            if (!card.contains(mouseX, mouseY)) continue;
            Item item = items.get(index);
            boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            if (shift && selectionAnchorId != null) {
                selectRange(items, selectionAnchorId, item.id(), control);
            } else if (control) {
                toggleSelection(item.id());
            } else if (!selectedIds.contains(item.id())) {
                selectOnly(item.id());
            }
            if (editBounds(card).contains(mouseX, mouseY) && item.editable()) {
                selectOnly(item.id());
                return new Interaction(
                        item.folder() ? Action.RENAME_FOLDER : Action.EDIT,
                        item.section(), item.id(), item.path(), true);
            }
            if (duplicateBounds(card).contains(mouseX, mouseY)
                    && item.editable()) {
                selectOnly(item.id());
                return new Interaction(
                        item.folder() ? Action.DUPLICATE_FOLDER : Action.DUPLICATE,
                        item.section(), item.id(), item.path(), true);
            }
            if (deleteBounds(card).contains(mouseX, mouseY) && item.deletable()) {
                if (selectedIds.contains(item.id()) && selectedIds.size() > 1) {
                    return new Interaction(
                            Action.DELETE_SELECTION, item.section(), item.id(),
                            null, true, items.stream()
                            .filter(candidate -> selectedIds.contains(candidate.id()))
                            .map(candidate -> new MoveEntry(
                                    candidate.folder(), candidate.id(), candidate.path()))
                            .toList());
                }
                selectOnly(item.id());
                return new Interaction(
                        item.folder() ? Action.DELETE_FOLDER : Action.DELETE,
                        item.section(), item.id(), item.path(), true);
            }
            long now = System.currentTimeMillis();
            String key = item.section().name() + ":" + item.id();
            boolean doubleClick = key.equals(lastClickedKey) && now - lastClickedAt <= 350L;
            lastClickedKey = key;
            lastClickedAt = now;
            if (item.editable() && selectedIds.contains(item.id())) {
                pressedItem = item;
                pressedAt = now;
                pressedX = mouseX;
                pressedY = mouseY;
            } else {
                clearPressedItem();
            }
            if (doubleClick && item.folder()) {
                clearPressedItem();
                return new Interaction(
                        Action.OPEN_FOLDER, item.section(), item.id(), item.path(), true);
            }
            return new Interaction(
                    doubleClick && item.editable() ? Action.EDIT : Action.SELECT,
                    item.section(), item.id(), true);
        }
        clearSelection();
        return Interaction.handled();
    }

    public boolean charTyped(
            char codePoint,
            VttTabletop tabletop,
            MapDefinitionRegistry maps,
            TokenDefinitionRegistry tokens
    ) {
        if (!searchFocused) return false;
        if (Character.isISOControl(codePoint) || search.length() >= 48) return false;
        search += codePoint;
        selectFirstGlobalMatch(tabletop, maps, tokens);
        return true;
    }

    public boolean backspace(
            VttTabletop tabletop,
            MapDefinitionRegistry maps,
            TokenDefinitionRegistry tokens
    ) {
        if (!searchFocused) return false;
        if (search.isEmpty()) return false;
        search = search.substring(0, search.length() - 1);
        selectFirstGlobalMatch(tabletop, maps, tokens);
        return true;
    }

    public boolean mouseScrolled(
            double mouseX, double mouseY, double scrollY,
            int screenWidth, int screenHeight,
            VttTabletop tabletop, MapDefinitionRegistry maps,
            TokenDefinitionRegistry tokens
    ) {
        Bounds panel = panel(screenWidth, screenHeight);
        if (!panel.contains(mouseX, mouseY)) return false;
        Grid grid = grid(panel, visibleItems(tabletop, maps, tokens).size());
        setScrollRow(Math.max(0, Math.min(grid.maximumScrollRow(),
                scrollRow() + (scrollY < 0 ? 1 : scrollY > 0 ? -1 : 0))));
        return true;
    }

    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            int screenWidth,
            int screenHeight,
            VttTabletop tabletop,
            MapDefinitionRegistry maps,
            TokenDefinitionRegistry tokens
    ) {
        if (button != 0) return false;
        if (!draggingScrollbar) {
            if (pressedItem != null
                    && System.currentTimeMillis() - pressedAt >= DRAG_HOLD_MS) {
                draggingItem = pressedItem;
                Bounds panel = panel(screenWidth, screenHeight);
                List<Item> items = visibleItems(tabletop, maps, tokens);
                draggingItems = selectedIds.contains(pressedItem.id())
                        ? items.stream()
                        .filter(item -> item.editable()
                                && selectedIds.contains(item.id()))
                        .toList()
                        : List.of(pressedItem);
                updateDragTarget(mouseX, mouseY, panel, grid(panel, items.size()), items);
                return true;
            }
            return pressedItem != null;
        }
        Bounds panel = panel(screenWidth, screenHeight);
        Grid grid = grid(panel, visibleItems(tabletop, maps, tokens).size());
        Scrollbar scrollbar = scrollbar(panel, grid);
        if (scrollbar == null) {
            draggingScrollbar = false;
            return true;
        }
        int travel = scrollbar.track().height() - scrollbar.thumb().height();
        if (travel <= 0 || grid.maximumScrollRow() <= 0) {
            setScrollRow(0);
            return true;
        }
        double thumbY = mouseY - scrollbarGrabOffset;
        double progress = (thumbY - scrollbar.track().y()) / travel;
        setScrollRow((int) Math.round(
                Math.max(0.0, Math.min(1.0, progress))
                        * grid.maximumScrollRow()));
        return true;
    }

    public Interaction mouseReleased(
            double mouseX,
            double mouseY,
            int button,
            int screenWidth,
            int screenHeight,
            VttTabletop tabletop,
            MapDefinitionRegistry maps,
            TokenDefinitionRegistry tokens
    ) {
        if (button != 0) return Interaction.none();
        if (draggingScrollbar) {
            draggingScrollbar = false;
            clearPressedItem();
            return Interaction.handled();
        }
        if (draggingItem != null) {
            Item source = draggingItem;
            List<MoveEntry> sources = draggingItems.stream()
                    .map(item -> new MoveEntry(
                            item.folder(), item.id(), item.path()))
                    .toList();
            String target = dragTargetFolder;
            clearPressedItem();
            if (target == null) return Interaction.handled();
            clearSelection();
            if (sources.size() > 1) {
                return new Interaction(
                        Action.MOVE_SELECTION, source.section(), source.id(),
                        target, true, sources);
            }
            return new Interaction(
                    source.folder() ? Action.MOVE_FOLDER : Action.MOVE_ITEM,
                    source.section(), source.folder() ? source.path() : source.id(),
                    target, true);
        }
        clearPressedItem();
        return Interaction.handled();
    }

    public boolean contains(double x, double y, int screenWidth, int screenHeight) {
        return panel(screenWidth, screenHeight).contains(x, y);
    }

    public Section section() { return section; }
    public String selectedId() { return selectedId; }
    public int selectedCount() { return selectedIds.size(); }
    public String currentFolderPath() { return currentFolder(); }

    public void setPendingOperation(String operation) {
        pendingOperation = operation;
    }

    public void restoreSelection(Section targetSection, List<MoveEntry> entries) {
        if (targetSection == null || entries == null || entries.isEmpty()) return;
        section = targetSection;
        selectedIds.clear();
        for (MoveEntry entry : entries) {
            if (entry == null) continue;
            String id = entry.folder() ? "folder:" + normalizeFolder(entry.path()) : entry.id();
            if (id != null && !id.isBlank()) selectedIds.add(id);
        }
        selectedId = selectedIds.stream().reduce((first, second) -> second).orElse(null);
        selectionAnchorId = selectedId;
    }

    public void cancelPointerInteraction() {
        draggingScrollbar = false;
        clearPressedItem();
    }

    public boolean cancelActiveDrag() {
        boolean active = draggingScrollbar || draggingItem != null || pressedItem != null;
        if (active) cancelPointerInteraction();
        return active;
    }

    public boolean clearMultiSelection() {
        if (selectedIds.size() <= 1) return false;
        clearSelection();
        return true;
    }

    public void select(Section section, String id) {
        this.section = section == null ? Section.SCENES : section;
        selectOnly(id);
    }

    public void openFolder(Section section, String path) {
        if (section != null) this.section = section;
        currentFolders.put(this.section, normalizeFolder(path));
        clearSelection();
        setScrollRow(0);
        search = "";
        searchFocused = false;
        clearPressedItem();
    }

    public void goBackFolder() {
        openFolder(section, parent(currentFolder()));
    }

    public void reconcileCurrentFolder(VttTabletop tabletop) {
        String current = currentFolder();
        if (current.isBlank() || tabletop == null) return;
        Set<String> existing = new LinkedHashSet<>(
                tabletop.getCatalogFolders(section.name()));
        String valid = current;
        while (!valid.isBlank() && !existing.contains(valid)) {
            valid = parent(valid);
        }
        if (valid.equals(current)) return;
        currentFolders.put(section, valid);
        clearSelection();
        setScrollRow(0);
        clearPressedItem();
    }

    public void reconcileSelection(
            VttTabletop tabletop,
            MapDefinitionRegistry maps,
            TokenDefinitionRegistry tokens
    ) {
        Set<String> validIds = new LinkedHashSet<>();
        for (Item item : allItems(tabletop, maps, tokens)) {
            if (item.section() == section) validIds.add(item.id());
        }
        if (tabletop != null) {
            for (String folder : tabletop.getCatalogFolders(section.name())) {
                validIds.add("folder:" + normalizeFolder(folder));
            }
        }
        selectedIds.removeIf(id -> !validIds.contains(id));
        if (selectedId != null && !selectedIds.contains(selectedId)) {
            selectedId = selectedIds.stream()
                    .reduce((first, second) -> second).orElse(null);
        }
        if (selectionAnchorId != null && !selectedIds.contains(selectionAnchorId)) {
            selectionAnchorId = selectedId;
        }
        clearPressedItem();
    }

    private void selectOnly(String id) {
        selectedIds.clear();
        selectedId = id;
        selectionAnchorId = id;
        if (id != null && !id.isBlank()) selectedIds.add(id);
    }

    private void clearSelection() {
        selectedIds.clear();
        selectedId = null;
        selectionAnchorId = null;
    }

    private void toggleSelection(String id) {
        if (id == null || id.isBlank()) return;
        if (!selectedIds.remove(id)) selectedIds.add(id);
        selectedId = selectedIds.contains(id)
                ? id : selectedIds.stream().reduce((first, second) -> second).orElse(null);
        selectionAnchorId = id;
    }

    private void selectRange(
            List<Item> items,
            String anchorId,
            String targetId,
            boolean additive
    ) {
        int anchor = -1;
        int target = -1;
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).id().equals(anchorId)) anchor = index;
            if (items.get(index).id().equals(targetId)) target = index;
        }
        if (anchor < 0 || target < 0) {
            selectOnly(targetId);
            return;
        }
        if (!additive) selectedIds.clear();
        int start = Math.min(anchor, target);
        int end = Math.max(anchor, target);
        for (int index = start; index <= end; index++) {
            selectedIds.add(items.get(index).id());
        }
        selectedId = targetId;
    }

    private void selectFirstGlobalMatch(
            VttTabletop tabletop,
            MapDefinitionRegistry maps,
            TokenDefinitionRegistry tokens
    ) {
        if (search.isBlank()) {
            clearSelection();
            setScrollRow(0);
            return;
        }
        String needle = search.toLowerCase(Locale.ROOT);
        List<Item> all = allItems(tabletop, maps, tokens);
        Item match = all.stream()
                .filter(item -> item.name().toLowerCase(Locale.ROOT).startsWith(needle))
                .findFirst()
                .orElseGet(() -> all.stream()
                        .filter(item -> item.name().toLowerCase(Locale.ROOT).contains(needle))
                        .findFirst().orElse(null));
        if (match != null) {
            section = match.section();
            selectOnly(match.id());
            currentFolders.put(section, match.path());
            setScrollRow(0);
        }
    }

    private List<Item> visibleItems(
            VttTabletop tabletop,
            MapDefinitionRegistry maps,
            TokenDefinitionRegistry tokens
    ) {
        String needle = search.toLowerCase(Locale.ROOT);
        List<Item> regularItems = allItems(tabletop, maps, tokens).stream()
                .filter(item -> item.section() == section)
                .toList();
        if (!needle.isBlank()) {
            return regularItems.stream()
                    .filter(item -> item.name().toLowerCase(Locale.ROOT).contains(needle))
                    .toList();
        }

        String current = currentFolder();
        Set<String> knownFolders = new LinkedHashSet<>();
        if (tabletop != null) {
            knownFolders.addAll(tabletop.getCatalogFolders(section.name()));
        }
        regularItems.stream().map(Item::path)
                .filter(path -> path != null && !path.isBlank())
                .forEach(knownFolders::add);

        List<Item> result = new ArrayList<>();
        knownFolders.stream()
                .map(path -> immediateChild(current, path))
                .filter(path -> path != null && !path.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(path -> result.add(new Item(
                        section, "folder:" + path, leaf(path), null,
                        true, true, true, path)));
        regularItems.stream()
                .filter(item -> current.equals(normalizeFolder(item.path())))
                .sorted(Comparator.comparing(Item::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(result::add);
        return List.copyOf(result);
    }

    private List<Item> allItems(
            VttTabletop tabletop,
            MapDefinitionRegistry maps,
            TokenDefinitionRegistry tokens
    ) {
        List<Item> result = new ArrayList<>();
        if (tabletop != null) {
            boolean canDeleteScene = tabletop.getSceneIds().size() > 1;
            tabletop.getSceneIds().forEach(id -> result.add(new Item(
                    Section.SCENES, id, tabletop.getSceneDisplayName(id),
                    null, true, canDeleteScene, false,
                    tabletop.getSceneFolder(id))));
        }
        maps.getAll().stream()
                .sorted(Comparator.comparing(MapDefinition::displayName))
                .forEach(map -> result.add(new Item(
                        Section.MAPS, map.id(), map.displayName(), map,
                        CreatedMapStorage.isUserCreatedMap(map),
                        CreatedMapStorage.isUserCreatedMap(map), false,
                        maps.folderOf(map.id()))));
        tokens.getAll().stream()
                .sorted(Comparator.comparing(TokenDefinition::displayName))
                .forEach(token -> result.add(new Item(
                        Section.TOKENS, token.id(), token.displayName(), token,
                        CreatedTokenStorage.isUserCreatedToken(token),
                        CreatedTokenStorage.isUserCreatedToken(token), false,
                        tokens.folderOf(token.id()))));
        return result;
    }

    private void renderCard(
            VRenderContext context, Font font, Bounds card, Item item,
            VttTabletop tabletop, VttScene activeScene,
            AssetRegistry assets, AssetThumbnailRegistry thumbnails
    ) {
        boolean selected = selectedIds.contains(item.id());
        boolean hovered = card.contains(context.mouseX(), context.mouseY());
        context.graphics().fill(
                card.x(), card.y(), card.right(), card.bottom(),
                selected ? EditorHudTheme.selection()
                        : item.folder() ? hovered
                        ? EditorHudTheme.folderHover()
                        : EditorHudTheme.folderBackground()
                        : hovered ? 0xFF303034 : 0xFF202024);
        border(context, card, selected
                ? EditorHudTheme.opaqueSelection() : EditorHudTheme.outline());
        Bounds preview = new Bounds(card.x() + 8, card.y() + 8,
                card.width() - 16, card.height() - 34);
        if (item.folder()) {
            renderFolderPreview(context, font, item, preview);
        } else {
            renderPreview(
                    context, item, preview, tabletop, activeScene, assets, thumbnails);
        }
        context.graphics().drawString(
                font, trim(item.name(), 16), card.x() + 8, card.bottom() - 18,
                0xFFFFFFFF, false);
        if (item.editable()) {
            Bounds edit = editBounds(card);
            context.graphics().fill(
                    edit.x(), edit.y(), edit.right(), edit.bottom(), 0xCC101014);
            context.graphics().blit(
                    EDIT_ICON, edit.x() + 1, edit.y() + 1,
                    16, 16, 0.0F, 0.0F, 32, 32, 32, 32);
            Bounds duplicate = duplicateBounds(card);
            context.graphics().fill(
                    duplicate.x(), duplicate.y(), duplicate.right(), duplicate.bottom(),
                    0xCC101014);
            context.graphics().blit(
                    DUPLICATE_ICON, duplicate.x() + 1, duplicate.y() + 1,
                    16, 16, 0.0F, 0.0F, 32, 32, 32, 32);
        }
        if (item.deletable()) {
            Bounds delete = deleteBounds(card);
            context.graphics().fill(
                    delete.x(), delete.y(), delete.right(), delete.bottom(), 0xCC101014);
            context.graphics().blit(
                    TRASH_ICON, delete.x() + 1, delete.y() + 1,
                    16, 16, 0.0F, 0.0F, 32, 32, 32, 32);
        }
    }

    private void renderFolderPreview(
            VRenderContext context,
            Font font,
            Item item,
            Bounds bounds
    ) {
        int size = Math.min(64, Math.min(bounds.width(), bounds.height()));
        int x = bounds.x() + (bounds.width() - size) / 2;
        int y = bounds.y() + (bounds.height() - size) / 2;
        context.graphics().blit(
                FOLDER_ICON, x, y, size, size,
                0.0F, 0.0F, 32, 32, 32, 32);
        String initial = item.name().isBlank()
                ? "?" : item.name().substring(0, 1).toUpperCase(Locale.ROOT);
        drawScaledCenteredString(
                context, font, initial,
                x + size / 2, y + size / 2 + 6,
                1.4F, 0xFFFFFFFF);
    }

    private void renderPreview(
            VRenderContext context, Item item, Bounds bounds,
            VttTabletop tabletop, VttScene activeScene,
            AssetRegistry assets, AssetThumbnailRegistry thumbnails
    ) {
        if (item.section() == Section.SCENES
                && sceneThumbnailRenderer.render(
                context, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                tabletop, activeScene, item.id(), assets, thumbnails)) {
            return;
        }
        if (item.value() instanceof MapDefinition map) {
            Texture texture = texture(map.assetId(), assets, thumbnails);
            if (texture != null) {
                Bounds fitted = fitInside(bounds, texture.width(), texture.height());
                context.graphics().blit(
                        texture.location(), fitted.x(), fitted.y(),
                        fitted.width(), fitted.height(), 0.0F, 0.0F,
                        texture.width(), texture.height(), texture.width(), texture.height());
                return;
            }
        }
        if (item.value() instanceof TokenDefinition token) {
            CanvasObjectState state = token.states().get(token.defaultStateId());
            CanvasVisual visual = state == null ? null : state.visual();
            if (visual != null) {
                Bounds fitted = fitInside(
                        bounds, token.defaultSize().x(), token.defaultSize().y());
                visualRenderer.render(
                        context, visual, fitted.x(), fitted.y(),
                        fitted.right(), fitted.bottom());
                return;
            }
        }
        context.graphics().blit(
                SCENE_ICON, bounds.x() + (bounds.width() - 32) / 2,
                bounds.y() + (bounds.height() - 32) / 2,
                32, 32, 0.0F, 0.0F, 32, 32, 32, 32);
    }

    private Texture texture(
            String assetId, AssetRegistry assets, AssetThumbnailRegistry thumbnails
    ) {
        if (assetId == null) return null;
        if (assetId.startsWith("library:")) {
            String id = assetId.substring("library:".length());
            AssetThumbnail thumbnail = thumbnails.findById(id)
                    .or(() -> thumbnails.findById(assetId)).orElse(null);
            if (thumbnail != null) {
                return new Texture(
                        thumbnail.texture(), thumbnail.width(), thumbnail.height());
            }
        }
        String id = assetId.startsWith("registered:")
                ? assetId.substring("registered:".length()) : assetId;
        AssetRef asset = assets.findById(id).or(() -> assets.findById(assetId)).orElse(null);
        if (asset instanceof BuiltInTextureAssetRef builtIn) {
            return new Texture(
                    builtIn.texture(), builtIn.textureWidth(), builtIn.textureHeight());
        }
        if (asset instanceof LibraryTextureAssetRef library) {
            return new Texture(
                    library.texture(), library.textureWidth(), library.textureHeight());
        }
        return null;
    }

    private Grid grid(Bounds panel, int itemCount) {
        int x = panel.x() + 16;
        int y = panel.y() + HEADER_HEIGHT + TABS_HEIGHT + 14;
        int width = panel.width() - 32;
        int height = panel.height() - HEADER_HEIGHT - TABS_HEIGHT - 24;
        int columns = Math.max(1, (width + CARD_GAP) / (CARD_WIDTH + CARD_GAP));
        int visibleRows = Math.max(1, (height + CARD_GAP) / (CARD_HEIGHT + CARD_GAP));
        int rows = (itemCount + columns - 1) / columns;
        int maximumScrollRow = Math.max(0, rows - visibleRows);
        setScrollRow(Math.max(0, Math.min(scrollRow(), maximumScrollRow)));
        int first = scrollRow() * columns;
        return new Grid(x, y, columns, first,
                Math.min(itemCount, first + visibleRows * columns),
                height, rows, visibleRows, maximumScrollRow);
    }

    private void renderScrollbar(VRenderContext context, Bounds panel, Grid grid) {
        Scrollbar scrollbar = scrollbar(panel, grid);
        if (scrollbar == null) return;
        Bounds track = scrollbar.track();
        Bounds thumb = scrollbar.thumb();
        context.graphics().fill(
                track.x(), track.y(), track.right(), track.bottom(), 0x66333338);
        boolean hovered = track.contains(context.mouseX(), context.mouseY());
        context.graphics().fill(
                thumb.x(), thumb.y(), thumb.right(), thumb.bottom(),
                draggingScrollbar ? EditorHudTheme.opaqueSelection()
                        : hovered ? 0xFFB8EAF2 : 0xFFD8D8DC);
    }

    private Scrollbar scrollbar(Bounds panel, Grid grid) {
        if (grid.maximumScrollRow() <= 0 || grid.totalRows() <= 0) return null;
        Bounds track = new Bounds(
                panel.right() - 11, grid.y(),
                SCROLLBAR_WIDTH, grid.height());
        int thumbHeight = Math.max(
                MIN_SCROLLBAR_THUMB_HEIGHT,
                Math.min(track.height(), (int) Math.round(
                        track.height() * (grid.visibleRows() / (double) grid.totalRows()))));
        int travel = Math.max(0, track.height() - thumbHeight);
        int thumbY = track.y() + (int) Math.round(
                travel * (scrollRow() / (double) grid.maximumScrollRow()));
        return new Scrollbar(
                track,
                new Bounds(track.x(), thumbY, track.width(), thumbHeight));
    }

    private int scrollRow() {
        return scrollRows.getOrDefault(section, 0);
    }

    private void setScrollRow(int value) {
        scrollRows.put(section, Math.max(0, value));
    }

    private Bounds panel(int screenWidth, int screenHeight) {
        int width = Math.max(420, Math.min(820, screenWidth - 80));
        int height = Math.max(280, Math.min(500, screenHeight - 90));
        return new Bounds((screenWidth - width) / 2, (screenHeight - height) / 2,
                width, height);
    }

    private Bounds searchBounds(Bounds panel) {
        return new Bounds(panel.right() - 390, panel.y() + 12, 174, 30);
    }

    private Bounds refreshBounds(Bounds panel) {
        return new Bounds(panel.right() - 202, panel.y() + 12, 30, 30);
    }

    private Bounds createFolderBounds(Bounds panel) {
        return new Bounds(panel.right() - 164, panel.y() + 12, 30, 30);
    }

    private Bounds addBounds(Bounds panel) {
        return new Bounds(panel.right() - 120, panel.y() + 12, 96, 30);
    }

    private Bounds backBounds(Bounds panel) {
        return new Bounds(panel.x() + 10, panel.y() + 10, 14, 32);
    }

    private Bounds tabBounds(Bounds panel, Section section) {
        return new Bounds(panel.x() + 1 + section.ordinal() * 140,
                panel.y() + HEADER_HEIGHT, 140, TABS_HEIGHT);
    }

    private void renderBreadcrumbs(
            VRenderContext context,
            Font font,
            Bounds panel,
            Bounds searchBox
    ) {
        String current = currentFolder();
        if (current.isBlank()) {
            renderedBreadcrumbs = List.of();
            context.graphics().drawString(
                    font, "Asset Manager", panel.x() + 14,
                    panel.y() + 18, 0xFFFFFFFF, false);
            return;
        }

        List<BreadcrumbPart> parts = new ArrayList<>();
        parts.add(new BreadcrumbPart("Assets", ""));
        String path = "";
        for (String segment : current.split("/")) {
            path = path.isBlank() ? segment : path + "/" + segment;
            parts.add(new BreadcrumbPart(segment, path));
        }

        int startX = panel.x() + 28;
        int availableWidth = Math.max(0, searchBox.x() - 8 - startX);
        boolean collapsed = false;
        while (breadcrumbWidth(font, parts) > availableWidth && parts.size() > 2) {
            parts.remove(1);
            collapsed = true;
        }
        if (collapsed) parts.add(1, new BreadcrumbPart("...", null));

        List<Breadcrumb> rendered = new ArrayList<>();
        int x = startX;
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) {
                String separator = " > ";
                context.graphics().drawString(
                        font, separator, x, panel.y() + 18, 0xFF88888E, false);
                x += font.width(separator);
            }
            BreadcrumbPart part = parts.get(index);
            int remaining = Math.max(0, searchBox.x() - 8 - x);
            String label = trimToWidth(font, part.label(), remaining);
            if (label.isEmpty()) break;
            Bounds bounds = new Bounds(
                    x - 2, panel.y() + 12,
                    font.width(label) + 4, 30);
            boolean currentPart = current.equals(part.path());
            boolean hovered = part.path() != null
                    && !currentPart
                    && bounds.contains(context.mouseX(), context.mouseY());
            boolean dropTarget = draggingItem != null
                    && part.path() != null
                    && part.path().equals(dragTargetFolder);
            if (dropTarget) {
                context.graphics().fill(
                        bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                        EditorHudTheme.selection());
                thickBorder(context, bounds, EditorHudTheme.opaqueSelection());
            }
            context.graphics().drawString(
                    font, label, x, panel.y() + 18,
                    dropTarget || currentPart ? 0xFFFFFFFF
                            : hovered ? EditorHudTheme.opaqueSelection() : 0xFFCCCCCC,
                    false);
            if (part.path() != null) {
                rendered.add(new Breadcrumb(part.path(), bounds));
            }
            x += font.width(label);
        }
        renderedBreadcrumbs = List.copyOf(rendered);
    }

    private int breadcrumbWidth(Font font, List<BreadcrumbPart> parts) {
        int width = 0;
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) width += font.width(" > ");
            width += font.width(parts.get(index).label());
        }
        return width;
    }

    private String trimToWidth(Font font, String value, int maximumWidth) {
        if (value == null || maximumWidth <= 0) return "";
        if (font.width(value) <= maximumWidth) return value;
        String suffix = "...";
        if (font.width(suffix) > maximumWidth) return "";
        int length = value.length();
        while (length > 0
                && font.width(value.substring(0, length) + suffix) > maximumWidth) {
            length--;
        }
        return length == 0 ? suffix : value.substring(0, length) + suffix;
    }

    private Bounds fitInside(Bounds container, double contentWidth, double contentHeight) {
        if (contentWidth <= 0.0 || contentHeight <= 0.0) return container;
        double scale = Math.min(
                container.width() / contentWidth,
                container.height() / contentHeight);
        int width = Math.max(1, (int) Math.round(contentWidth * scale));
        int height = Math.max(1, (int) Math.round(contentHeight * scale));
        return new Bounds(
                container.x() + (container.width() - width) / 2,
                container.y() + (container.height() - height) / 2,
                width, height);
    }

    private Bounds editBounds(Bounds card) {
        return new Bounds(card.x() + 4, card.y() + 4, 18, 18);
    }

    private Bounds duplicateBounds(Bounds card) {
        return new Bounds(card.right() - 22, card.y() + 4, 18, 18);
    }

    private Bounds deleteBounds(Bounds card) {
        return new Bounds(card.right() - 22, card.bottom() - 24, 18, 18);
    }

    private void updateDraggingState(List<Item> items) {
        if (draggingItem == null && pressedItem != null
                && System.currentTimeMillis() - pressedAt >= DRAG_HOLD_MS) {
            draggingItem = pressedItem;
            draggingItems = selectedIds.contains(pressedItem.id())
                    ? items.stream()
                    .filter(item -> item.editable()
                            && selectedIds.contains(item.id()))
                    .toList()
                    : List.of(pressedItem);
        }
    }

    private boolean autoScrollWhileDragging(
            double mouseX,
            double mouseY,
            Bounds panel,
            Grid grid
    ) {
        if (draggingItem == null || grid.maximumScrollRow() <= 0
                || mouseX < grid.x() || mouseX > panel.right() - 16
                || mouseY < grid.y() || mouseY > grid.y() + grid.height()) {
            return false;
        }
        int direction = 0;
        if (mouseY <= grid.y() + DRAG_AUTO_SCROLL_EDGE) {
            direction = -1;
        } else if (mouseY >= grid.y() + grid.height() - DRAG_AUTO_SCROLL_EDGE) {
            direction = 1;
        }
        if (direction == 0) return false;

        long now = System.currentTimeMillis();
        if (now - lastDragAutoScrollAt < DRAG_AUTO_SCROLL_INTERVAL_MS) return false;
        int previous = scrollRow();
        setScrollRow(Math.max(0, Math.min(
                grid.maximumScrollRow(), previous + direction)));
        lastDragAutoScrollAt = now;
        return scrollRow() != previous;
    }

    private void updateDragTarget(
            double mouseX,
            double mouseY,
            Bounds panel,
            Grid grid,
            List<Item> items
    ) {
        dragTargetFolder = null;
        if (draggingItem == null) return;
        if (!currentFolder().isBlank() && backBounds(panel).contains(mouseX, mouseY)) {
            dragTargetFolder = parent(currentFolder());
            return;
        }
        for (Breadcrumb breadcrumb : renderedBreadcrumbs) {
            if (breadcrumb.bounds().contains(mouseX, mouseY)
                    && validDropTarget(draggingItems, breadcrumb.path())) {
                dragTargetFolder = breadcrumb.path();
                return;
            }
        }
        for (int index = grid.firstIndex(); index < grid.lastIndex(); index++) {
            int visibleIndex = index - grid.firstIndex();
            int column = visibleIndex % grid.columns();
            int row = visibleIndex / grid.columns();
            Bounds card = new Bounds(
                    grid.x() + column * (CARD_WIDTH + CARD_GAP),
                    grid.y() + row * (CARD_HEIGHT + CARD_GAP),
                    CARD_WIDTH, CARD_HEIGHT);
            Item target = items.get(index);
            if (!target.folder() || !card.contains(mouseX, mouseY)
                    || !validDropTarget(draggingItems, target.path())) continue;
            dragTargetFolder = target.path();
            return;
        }
    }

    private boolean validDropTarget(Item source, String targetFolder) {
        if (source == null || targetFolder == null) return false;
        if (!source.folder()) return !normalizeFolder(source.path())
                .equals(normalizeFolder(targetFolder));
        String sourcePath = normalizeFolder(source.path());
        String targetPath = normalizeFolder(targetFolder);
        return !sourcePath.equals(targetPath)
                && !targetPath.startsWith(sourcePath + "/");
    }

    private boolean validDropTarget(List<Item> sources, String targetFolder) {
        return sources != null && !sources.isEmpty()
                && sources.stream().allMatch(
                source -> validDropTarget(source, targetFolder));
    }

    private void renderDragPreview(
            VRenderContext context,
            Font font,
            Item item,
            int itemCount
    ) {
        int x = (int) Math.round(context.mouseX()) + 12;
        int y = (int) Math.round(context.mouseY()) + 12;
        int width = Math.min(150, Math.max(80, font.width(item.name()) + 34));
        context.graphics().fill(x, y, x + width, y + 28, 0xAA201426);
        context.graphics().blit(
                item.folder() ? FOLDER_ICON : item.section().icon(),
                x + 5, y + 6, 16, 16,
                0.0F, 0.0F, 32, 32, 32, 32);
        context.graphics().drawString(
                font, itemCount > 1 ? itemCount + " selected" : trim(item.name(), 17),
                x + 26, y + 10,
                0xCCFFFFFF, false);
        border(context, new Bounds(x, y, width, 28), 0xCCFFFFFF);
    }

    private void thickBorder(VRenderContext context, Bounds bounds, int color) {
        border(context, bounds, color);
        border(context, new Bounds(
                bounds.x() + 1, bounds.y() + 1,
                bounds.width() - 2, bounds.height() - 2), color);
        border(context, new Bounds(
                bounds.x() + 2, bounds.y() + 2,
                bounds.width() - 4, bounds.height() - 4), color);
    }

    private boolean sameItem(Item first, Item second) {
        return first != null && second != null
                && first.section() == second.section()
                && first.id().equals(second.id());
    }

    private void clearPressedItem() {
        pressedItem = null;
        pressedAt = 0L;
        pressedX = 0.0;
        pressedY = 0.0;
        draggingItem = null;
        draggingItems = List.of();
        dragTargetFolder = null;
        lastDragAutoScrollAt = 0L;
    }

    private String currentFolder() {
        return currentFolders.getOrDefault(section, "");
    }

    private String immediateChild(String current, String candidate) {
        String normalizedCurrent = normalizeFolder(current);
        String normalizedCandidate = normalizeFolder(candidate);
        if (normalizedCandidate.equals(normalizedCurrent)) return null;
        String prefix = normalizedCurrent.isBlank() ? "" : normalizedCurrent + "/";
        if (!normalizedCandidate.startsWith(prefix)) return null;
        String remaining = normalizedCandidate.substring(prefix.length());
        if (remaining.isBlank()) return null;
        int slash = remaining.indexOf('/');
        String child = slash < 0 ? remaining : remaining.substring(0, slash);
        return prefix + child;
    }

    private String parent(String path) {
        String normalized = normalizeFolder(path);
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? "" : normalized.substring(0, slash);
    }

    private String leaf(String path) {
        String normalized = normalizeFolder(path);
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    private String normalizeFolder(String path) {
        if (path == null || path.isBlank()) return "";
        return path.replace('\\', '/').replaceAll("/+", "/")
                .replaceAll("^/+|/+$", "");
    }

    private void border(VRenderContext context, Bounds bounds, int color) {
        context.graphics().hLine(bounds.x(), bounds.right(), bounds.y(), color);
        context.graphics().hLine(bounds.x(), bounds.right(), bounds.bottom(), color);
        context.graphics().vLine(bounds.x(), bounds.y(), bounds.bottom(), color);
        context.graphics().vLine(bounds.right(), bounds.y(), bounds.bottom(), color);
    }

    private void drawScaledCenteredString(
            VRenderContext context,
            Font font,
            String text,
            int centerX,
            int centerY,
            float scale,
            int color
    ) {
        context.graphics().pose().pushPose();
        context.graphics().pose().scale(scale, scale, 1.0F);
        int x = Math.round(centerX / scale - font.width(text) / 2.0F);
        int y = Math.round(centerY / scale - font.lineHeight / 2.0F);
        context.graphics().drawString(font, text, x, y, color, false);
        context.graphics().pose().popPose();
    }

    private String trim(String value, int length) {
        if (value == null) return "";
        return value.length() <= length ? value : value.substring(0, length - 3) + "...";
    }

    public enum Section {
        SCENES("Scenes", SCENE_ICON),
        MAPS("Maps", MAP_ICON),
        TOKENS("Tokens", TOKEN_ICON);
        private final String label;
        private final ResourceLocation icon;
        Section(String label, ResourceLocation icon) {
            this.label = label;
            this.icon = icon;
        }
        private ResourceLocation icon() { return icon; }
    }

    public enum Action {
        NONE, SELECT, ADD, EDIT, DUPLICATE, DUPLICATE_FOLDER, DELETE,
        DELETE_SELECTION, REFRESH,
        CREATE_FOLDER, OPEN_FOLDER, BACK_FOLDER, RENAME_FOLDER,
        DELETE_FOLDER, MOVE_ITEM, MOVE_FOLDER, MOVE_SELECTION
    }

    public record Interaction(
            Action action,
            Section section,
            String id,
            String value,
            boolean consumed,
            List<MoveEntry> moveEntries
    ) {
        public Interaction(
                Action action, Section section, String id,
                String value, boolean consumed
        ) {
            this(action, section, id, value, consumed, List.of());
        }
        public Interaction(Action action, Section section, String id, boolean consumed) {
            this(action, section, id, null, consumed, List.of());
        }
        public Interaction {
            moveEntries = moveEntries == null ? List.of() : List.copyOf(moveEntries);
        }
        public static Interaction none() {
            return new Interaction(Action.NONE, null, null, null, false, List.of());
        }
        public static Interaction handled() {
            return new Interaction(Action.NONE, null, null, null, true, List.of());
        }
    }

    public record MoveEntry(boolean folder, String id, String path) {}

    private record Item(
            Section section, String id, String name, Object value,
            boolean editable, boolean deletable, boolean folder, String path
    ) {}
    private record BreadcrumbPart(String label, String path) {}
    private record Breadcrumb(String path, Bounds bounds) {}
    private record Bounds(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        boolean contains(double px, double py) {
            return px >= x && px <= right() && py >= y && py <= bottom();
        }
    }
    private record Grid(
            int x,
            int y,
            int columns,
            int firstIndex,
            int lastIndex,
            int height,
            int totalRows,
            int visibleRows,
            int maximumScrollRow
    ) {}
    private record Scrollbar(Bounds track, Bounds thumb) {}
    private record Texture(ResourceLocation location, int width, int height) {}
}
