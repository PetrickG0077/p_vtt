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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

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
    private final CanvasVisualRenderer visualRenderer =
            new CanvasVisualRenderer(new AnimatedTextureService());
    private final SceneThumbnailRenderer sceneThumbnailRenderer;

    private Section section = Section.SCENES;
    private String search = "";
    private String selectedId;
    private final EnumMap<Section, Integer> scrollRows = new EnumMap<>(Section.class);
    private String lastClickedKey;
    private long lastClickedAt;
    private boolean searchFocused;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;

    public AssetManagerOverlay(TabletopStorage tabletopStorage) {
        this.sceneThumbnailRenderer = new SceneThumbnailRenderer(tabletopStorage);
        for (Section candidate : Section.values()) scrollRows.put(candidate, 0);
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
        context.graphics().fill(
                panel.x(), panel.y(), panel.right(), panel.bottom(), 0xF21B1B1F);
        border(context, panel, 0xFFE8E8E8);
        context.graphics().fill(
                panel.x() + 1, panel.y() + 1, panel.right() - 1,
                panel.y() + HEADER_HEIGHT, 0xFF4A4A4A);
        context.graphics().drawString(
                font, "Asset Manager", panel.x() + 14, panel.y() + 18,
                0xFFFFFFFF, false);

        Bounds searchBox = searchBounds(panel);
        context.graphics().fill(
                searchBox.x(), searchBox.y(), searchBox.right(), searchBox.bottom(),
                searchFocused ? 0xFF242D33 : 0xFF202024);
        border(context, searchBox, searchFocused ? 0xFF66DDEE : 0xFFFFFFFF);
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

        List<Item> items = visibleItems(tabletop, maps, tokens);
        Grid grid = grid(panel, items.size());
        for (int index = grid.firstIndex(); index < grid.lastIndex(); index++) {
            int visibleIndex = index - grid.firstIndex();
            int column = visibleIndex % grid.columns();
            int row = visibleIndex / grid.columns();
            int x = grid.x() + column * (CARD_WIDTH + CARD_GAP);
            int y = grid.y() + row * (CARD_HEIGHT + CARD_GAP);
            renderCard(context, font, new Bounds(x, y, CARD_WIDTH, CARD_HEIGHT),
                    items.get(index), tabletop, activeScene, assets, thumbnails);
        }
        renderScrollbar(context, panel, grid);
        if (items.isEmpty()) {
            context.graphics().drawCenteredString(
                    font, search.isBlank() ? "No assets in this section" : "No search results",
                    panel.x() + panel.width() / 2, panel.y() + HEADER_HEIGHT
                            + TABS_HEIGHT + 30, 0xFF88888E);
        }
        border(context, panel, 0xFFE8E8E8);
    }

    public Interaction mouseClicked(
            double mouseX,
            double mouseY,
            int button,
            int screenWidth,
            int screenHeight,
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
            return Interaction.handled();
        }
        if (searchBounds(panel).contains(mouseX, mouseY)) {
            searchFocused = true;
            return Interaction.handled();
        }
        searchFocused = false;
        if (addBounds(panel).contains(mouseX, mouseY)) {
            return new Interaction(Action.ADD, section, null, true);
        }
        for (Section candidate : Section.values()) {
            if (tabBounds(panel, candidate).contains(mouseX, mouseY)) {
                section = candidate;
                selectedId = null;
                draggingScrollbar = false;
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
            selectedId = item.id();
            if (editBounds(card).contains(mouseX, mouseY) && item.editable()) {
                return new Interaction(Action.EDIT, item.section(), item.id(), true);
            }
            if (deleteBounds(card).contains(mouseX, mouseY) && item.deletable()) {
                return new Interaction(Action.DELETE, item.section(), item.id(), true);
            }
            long now = System.currentTimeMillis();
            String key = item.section().name() + ":" + item.id();
            boolean doubleClick = key.equals(lastClickedKey) && now - lastClickedAt <= 350L;
            lastClickedKey = key;
            lastClickedAt = now;
            return new Interaction(
                    doubleClick && item.editable() ? Action.EDIT : Action.SELECT,
                    item.section(), item.id(), true);
        }
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
            double mouseY,
            int button,
            int screenWidth,
            int screenHeight,
            VttTabletop tabletop,
            MapDefinitionRegistry maps,
            TokenDefinitionRegistry tokens
    ) {
        if (!draggingScrollbar || button != 0) return false;
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

    public boolean mouseReleased(int button) {
        if (button != 0 || !draggingScrollbar) return false;
        draggingScrollbar = false;
        return true;
    }

    public boolean contains(double x, double y, int screenWidth, int screenHeight) {
        return panel(screenWidth, screenHeight).contains(x, y);
    }

    public Section section() { return section; }
    public String selectedId() { return selectedId; }

    public void select(Section section, String id) {
        this.section = section == null ? Section.SCENES : section;
        this.selectedId = id;
    }

    private void selectFirstGlobalMatch(
            VttTabletop tabletop,
            MapDefinitionRegistry maps,
            TokenDefinitionRegistry tokens
    ) {
        if (search.isBlank()) {
            selectedId = null;
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
            selectedId = match.id();
            setScrollRow(0);
        }
    }

    private List<Item> visibleItems(
            VttTabletop tabletop,
            MapDefinitionRegistry maps,
            TokenDefinitionRegistry tokens
    ) {
        String needle = search.toLowerCase(Locale.ROOT);
        return allItems(tabletop, maps, tokens).stream()
                .filter(item -> item.section() == section)
                .filter(item -> needle.isBlank()
                        || item.name().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
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
                    null, true, canDeleteScene)));
        }
        maps.getAll().stream()
                .sorted(Comparator.comparing(MapDefinition::displayName))
                .forEach(map -> result.add(new Item(
                        Section.MAPS, map.id(), map.displayName(), map,
                        CreatedMapStorage.isUserCreatedMap(map),
                        CreatedMapStorage.isUserCreatedMap(map))));
        tokens.getAll().stream()
                .sorted(Comparator.comparing(TokenDefinition::displayName))
                .forEach(token -> result.add(new Item(
                        Section.TOKENS, token.id(), token.displayName(), token,
                        CreatedTokenStorage.isUserCreatedToken(token),
                        CreatedTokenStorage.isUserCreatedToken(token))));
        return result;
    }

    private void renderCard(
            VRenderContext context, Font font, Bounds card, Item item,
            VttTabletop tabletop, VttScene activeScene,
            AssetRegistry assets, AssetThumbnailRegistry thumbnails
    ) {
        boolean selected = item.id().equals(selectedId);
        boolean hovered = card.contains(context.mouseX(), context.mouseY());
        context.graphics().fill(
                card.x(), card.y(), card.right(), card.bottom(),
                selected ? 0xFF304F60 : hovered ? 0xFF303034 : 0xFF202024);
        border(context, card, selected ? 0xFF66DDEE : 0xFFE8E8E8);
        Bounds preview = new Bounds(card.x() + 8, card.y() + 8,
                card.width() - 16, card.height() - 34);
        renderPreview(
                context, item, preview, tabletop, activeScene, assets, thumbnails);
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
                draggingScrollbar ? 0xFF66DDEE
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
        return new Bounds(panel.right() - 390, panel.y() + 12, 250, 30);
    }

    private Bounds addBounds(Bounds panel) {
        return new Bounds(panel.right() - 120, panel.y() + 12, 96, 30);
    }

    private Bounds tabBounds(Bounds panel, Section section) {
        return new Bounds(panel.x() + 1 + section.ordinal() * 140,
                panel.y() + HEADER_HEIGHT, 140, TABS_HEIGHT);
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

    private Bounds deleteBounds(Bounds card) {
        return new Bounds(card.right() - 22, card.bottom() - 24, 18, 18);
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

    public enum Action { NONE, SELECT, ADD, EDIT, DELETE }

    public record Interaction(Action action, Section section, String id, boolean consumed) {
        public static Interaction none() {
            return new Interaction(Action.NONE, null, null, false);
        }
        public static Interaction handled() {
            return new Interaction(Action.NONE, null, null, true);
        }
    }

    private record Item(
            Section section, String id, String name, Object value,
            boolean editable, boolean deletable
    ) {}
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
