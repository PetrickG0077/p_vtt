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
import com.petrick.vtt.feature.tabletop.VttTabletop;
import com.petrick.vtt.feature.token.TokenDefinition;
import com.petrick.vtt.feature.token.TokenDefinitionRegistry;
import com.petrick.vtt.feature.token.persistence.CreatedTokenStorage;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Central searchable manager for scene, map and token definitions. */
public final class AssetManagerOverlay {
    private static final int CARD_WIDTH = 112;
    private static final int CARD_HEIGHT = 116;
    private static final int CARD_GAP = 10;
    private static final int HEADER_HEIGHT = 54;
    private static final int TABS_HEIGHT = 34;
    private static final ResourceLocation SCENE_ICON = ResourceLocation.fromNamespaceAndPath(
            VTT.MOD_ID, "textures/gui/editor_hud/scenes.png");
    private final CanvasVisualRenderer visualRenderer =
            new CanvasVisualRenderer(new AnimatedTextureService());

    private Section section = Section.SCENES;
    private String search = "";
    private String selectedId;
    private int scrollRow;
    private String lastClickedKey;
    private long lastClickedAt;

    public void render(
            VRenderContext context,
            Font font,
            VttTabletop tabletop,
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
                0xFF202024);
        border(context, searchBox, 0xFFFFFFFF);
        context.graphics().drawString(
                font, search.isBlank() ? "Search" : search + "_",
                searchBox.x() + 10, searchBox.y() + 8,
                search.isBlank() ? 0xFF77777D : 0xFFFFFFFF, false);

        Bounds add = addBounds(panel);
        context.graphics().fill(
                add.x(), add.y(), add.right(), add.bottom(),
                add.contains(context.mouseX(), context.mouseY())
                        ? 0xFFFFFFFF : 0xFFE5E5E5);
        context.graphics().drawCenteredString(
                font, "ADD +", add.x() + add.width() / 2,
                add.y() + 9, 0xFF111111);

        for (Section candidate : Section.values()) {
            Bounds tab = tabBounds(panel, candidate);
            boolean active = candidate == section;
            context.graphics().fill(
                    tab.x(), tab.y(), tab.right(), tab.bottom(),
                    active ? 0xFF4A4A4A : 0xFF121214);
            context.graphics().drawString(
                    font, candidate.label, tab.x() + 18, tab.y() + 12,
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
                    items.get(index), assets, thumbnails);
        }
        if (items.isEmpty()) {
            context.graphics().drawCenteredString(
                    font, search.isBlank() ? "No assets in this section" : "No search results",
                    panel.x() + panel.width() / 2, panel.y() + HEADER_HEIGHT
                            + TABS_HEIGHT + 30, 0xFF88888E);
        }
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
        if (!panel.contains(mouseX, mouseY)) return Interaction.none();
        if (button != 0) return Interaction.handled();
        if (addBounds(panel).contains(mouseX, mouseY)) {
            return new Interaction(Action.ADD, section, null, true);
        }
        for (Section candidate : Section.values()) {
            if (tabBounds(panel, candidate).contains(mouseX, mouseY)) {
                section = candidate;
                selectedId = null;
                scrollRow = 0;
                return Interaction.handled();
            }
        }
        List<Item> items = visibleItems(tabletop, maps, tokens);
        Grid grid = grid(panel, items.size());
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
        int columns = Math.max(1, (panel.width() - 32 + CARD_GAP)
                / (CARD_WIDTH + CARD_GAP));
        int rows = (visibleItems(tabletop, maps, tokens).size() + columns - 1) / columns;
        int visibleRows = Math.max(1, (panel.height() - HEADER_HEIGHT - TABS_HEIGHT - 24)
                / (CARD_HEIGHT + CARD_GAP));
        int maximum = Math.max(0, rows - visibleRows);
        scrollRow = Math.max(0, Math.min(maximum,
                scrollRow + (scrollY < 0 ? 1 : scrollY > 0 ? -1 : 0)));
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
        this.scrollRow = 0;
    }

    private void selectFirstGlobalMatch(
            VttTabletop tabletop,
            MapDefinitionRegistry maps,
            TokenDefinitionRegistry tokens
    ) {
        if (search.isBlank()) {
            selectedId = null;
            scrollRow = 0;
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
            scrollRow = 0;
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
        renderPreview(context, item, preview, assets, thumbnails);
        context.graphics().drawString(
                font, trim(item.name(), 16), card.x() + 8, card.bottom() - 18,
                0xFFFFFFFF, false);
        if (item.editable()) {
            Bounds edit = editBounds(card);
            context.graphics().fill(
                    edit.x(), edit.y(), edit.right(), edit.bottom(), 0xCC101014);
            context.graphics().drawString(font, "/", edit.x() + 6, edit.y() + 4,
                    0xFFFFFFFF, false);
        }
        if (item.deletable()) {
            Bounds delete = deleteBounds(card);
            context.graphics().fill(
                    delete.x(), delete.y(), delete.right(), delete.bottom(), 0xCC101014);
            context.graphics().drawString(font, "X", delete.x() + 5, delete.y() + 4,
                    0xFFFF7777, false);
        }
    }

    private void renderPreview(
            VRenderContext context, Item item, Bounds bounds,
            AssetRegistry assets, AssetThumbnailRegistry thumbnails
    ) {
        if (item.value() instanceof MapDefinition map) {
            Texture texture = texture(map.assetId(), assets, thumbnails);
            if (texture != null) {
                context.graphics().blit(
                        texture.location(), bounds.x(), bounds.y(),
                        bounds.width(), bounds.height(), 0.0F, 0.0F,
                        texture.width(), texture.height(), texture.width(), texture.height());
                return;
            }
        }
        if (item.value() instanceof TokenDefinition token) {
            CanvasObjectState state = token.states().get(token.defaultStateId());
            CanvasVisual visual = state == null ? null : state.visual();
            if (visual != null) {
                visualRenderer.render(
                        context, visual, bounds.x(), bounds.y(),
                        bounds.right(), bounds.bottom());
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
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, rows - visibleRows)));
        int first = scrollRow * columns;
        return new Grid(x, y, columns, first,
                Math.min(itemCount, first + visibleRows * columns));
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
        return new Bounds(panel.x() + section.ordinal() * 140,
                panel.y() + HEADER_HEIGHT, 140, TABS_HEIGHT);
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

    private String trim(String value, int length) {
        if (value == null) return "";
        return value.length() <= length ? value : value.substring(0, length - 3) + "...";
    }

    public enum Section {
        SCENES("Scenes"), MAPS("Maps"), TOKENS("Tokens");
        private final String label;
        Section(String label) { this.label = label; }
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
    private record Grid(int x, int y, int columns, int firstIndex, int lastIndex) {}
    private record Texture(ResourceLocation location, int width, int height) {}
}
