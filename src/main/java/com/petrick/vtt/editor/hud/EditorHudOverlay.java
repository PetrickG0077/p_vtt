package com.petrick.vtt.editor.hud;

import com.petrick.vtt.VTT;
import com.petrick.vtt.editor.token.VttOwnedTokenOption;
import com.petrick.vtt.editor.token.VttPlayerOption;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Responsive editor HUD shell rendered above the tabletop and legacy panels. */
public final class EditorHudOverlay {
    private static final int MARGIN = 6;
    private static final int GAP = 2;
    private static final int PADDING = 3;
    private static final int PANEL_BACKGROUND = 0xD8101014;
    private static final int PANEL_BORDER = 0xFFE8E8E8;
    private static final int BUTTON_BACKGROUND = 0xE018181E;
    private static final int BUTTON_HOVER = 0xE032323C;
    private static final int BUTTON_ACTIVE = 0xE0245266;
    private static final int BUTTON_DISABLED = 0xD0181818;
    private static final int ACTIVE_BORDER = 0xFF66DDEE;
    private static final int OPEN_BORDER = 0xFFFFC45A;
    private static final int DISABLED_BORDER = 0xFF55555A;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int MUTED = 0xFF99999F;
    private static final int DANGER = 0xFFFF7777;
    private static final int MAX_VISIBLE_PLAYERS = 12;
    private static final int MAX_VISIBLE_OWNED_TOKENS = 4;
    private static final int ICON_TEXTURE_SIZE = 32;
    private static final int ICON_RENDER_SIZE = 16;

    private String selectedPlayerId;
    private String selectedOwnedTokenId;

    public void render(VRenderContext context, Font font, State state) {
        List<Button> buttons = buttons(context.screenWidth(), context.screenHeight(), state);
        renderGroups(context, state);
        Button hovered = null;
        for (Button button : buttons) {
            renderButton(context, font, button);
            if (button.contains(context.mouseX(), context.mouseY())) hovered = button;
        }
        if (state.playersOpen()) renderPlayers(context, font, state);
        if (state.creationOpen() && state.master()) renderCreation(context, font, state);
        if (hovered != null) renderTooltip(context, font, hovered);
    }

    public Action actionAt(double mouseX, double mouseY, int screenWidth, int screenHeight, State state) {
        if (state.playersOpen()) {
            Action playerAction = playerActionAt(mouseX, mouseY, screenHeight, state);
            if (playerAction != Action.NONE) return playerAction;
        }
        if (state.playersOpen() && selectPlayerAt(
                mouseX, mouseY, screenHeight, state)) return Action.NONE;
        if (state.creationOpen() && state.master()) {
            Action creationAction = creationActionAt(
                    mouseX, mouseY, screenWidth, screenHeight, state);
            if (creationAction != Action.NONE) return creationAction;
        }
        for (Button button : buttons(screenWidth, screenHeight, state)) {
            if (button.enabled() && button.contains(mouseX, mouseY)) return button.action();
        }
        return Action.NONE;
    }

    public boolean containsHud(
            double mouseX, double mouseY, int screenWidth, int screenHeight, State state
    ) {
        for (Button button : buttons(screenWidth, screenHeight, state)) {
            if (button.contains(mouseX, mouseY)) return true;
        }
        return state.playersOpen()
                && playersBounds(screenHeight, state).contains(mouseX, mouseY)
                || state.creationOpen() && state.master()
                && creationBounds(screenWidth, screenHeight).contains(mouseX, mouseY);
    }

    private List<Button> buttons(int screenWidth, int screenHeight, State state) {
        int size = buttonSize(screenHeight);
        List<Button> result = new ArrayList<>();

        int topX = MARGIN + PADDING;
        int topY = MARGIN + PADDING;
        result.add(button(Action.CLOSE, topX, topY, size, "", "Close VTT", true, false));
        topX += size + GAP;
        result.add(button(Action.PLAYERS, topX, topY, size, "", "Players", true,
                state.playersOpen()));
        if (state.master()) {
            topX += size + GAP;
            result.add(button(Action.OUTLINER, topX, topY, size, "", "Scene Outliner", true,
                    state.outlinerOpen()));
        }
        if (state.spectator()) return result;

        int settingsX = screenWidth / 2 - size / 2;
        result.add(button(Action.SETTINGS, settingsX, topY, size, "", "Settings", true,
                state.settingsOpen()));

        List<Action> tools = new ArrayList<>(List.of(Action.HAND, Action.SELECT));
        if (state.master()) tools.addAll(List.of(
                Action.FOG, Action.WALL, Action.DOOR, Action.MEASURE));
        int rightCount = tools.size() + 2;
        int separator = 5;
        int rightHeight = PADDING * 2 + rightCount * size
                + (rightCount - 1) * GAP + separator;
        int rightX = screenWidth - MARGIN - PADDING - size;
        int rightY = Math.max(MARGIN + PADDING, (screenHeight - rightHeight) / 2 + PADDING);
        for (Action tool : tools) {
            result.add(button(tool, rightX, rightY, size, toolShortcut(tool), toolTooltip(tool),
                    true, toolId(tool).equals(state.activeToolId())));
            rightY += size + GAP;
        }
        rightY += separator;
        result.add(button(Action.UNDO, rightX, rightY, size, "",
                historyTooltip("Undo", state.undoDescription()),
                state.canUndo(), false));
        rightY += size + GAP;
        result.add(button(Action.REDO, rightX, rightY, size, "",
                historyTooltip("Redo", state.redoDescription()),
                state.canRedo(), false));

        if (state.master()) {
            int bottomWidth = PADDING * 2 + size * 3 + GAP * 2;
            int bottomX = (screenWidth - bottomWidth) / 2 + PADDING;
            int bottomY = screenHeight - MARGIN - PADDING - size;
            result.add(button(Action.SCENES, bottomX, bottomY, size, "", "Scenes", true,
                    state.scenesOpen()));
            bottomX += size + GAP;
            result.add(button(Action.TOKENS, bottomX, bottomY, size, "", "Tokens", true,
                    state.tokensOpen()));
            bottomX += size + GAP;
            result.add(button(Action.CREATION, bottomX, bottomY, size, "", "Create & Manage",
                    true, state.creationOpen()));
        }
        return result;
    }

    private void renderGroups(VRenderContext context, State state) {
        int screenWidth = context.screenWidth();
        int screenHeight = context.screenHeight();
        int size = buttonSize(screenHeight);
        int topCount = state.master() ? 3 : 2;
        renderPanel(context, MARGIN, MARGIN,
                PADDING * 2 + topCount * size + (topCount - 1) * GAP, PADDING * 2 + size);
        if (state.spectator()) return;
        renderPanel(context, screenWidth / 2 - size / 2 - PADDING, MARGIN,
                size + PADDING * 2, size + PADDING * 2);

        int toolCount = state.master() ? 6 : 2;
        int rightCount = toolCount + 2;
        int rightHeight = PADDING * 2 + rightCount * size + (rightCount - 1) * GAP + 5;
        renderPanel(context, screenWidth - MARGIN - PADDING * 2 - size,
                Math.max(MARGIN, (screenHeight - rightHeight) / 2),
                size + PADDING * 2, rightHeight);

        if (state.master()) {
            int bottomWidth = PADDING * 2 + size * 3 + GAP * 2;
            renderPanel(context, (screenWidth - bottomWidth) / 2,
                    screenHeight - MARGIN - PADDING * 2 - size,
                    bottomWidth, size + PADDING * 2);
        }
    }

    private void renderPanel(VRenderContext context, int x, int y, int width, int height) {
        context.graphics().fill(x, y, x + width, y + height, PANEL_BACKGROUND);
        border(context, x, y, width, height, PANEL_BORDER);
    }

    private void renderButton(VRenderContext context, Font font, Button button) {
        boolean hovered = button.contains(context.mouseX(), context.mouseY());
        int background = !button.enabled() ? BUTTON_DISABLED
                : button.active() ? BUTTON_ACTIVE : hovered ? BUTTON_HOVER : BUTTON_BACKGROUND;
        int border = !button.enabled() ? DISABLED_BORDER
                : button.active() ? (button.action().isPopup() ? OPEN_BORDER : ACTIVE_BORDER)
                : PANEL_BORDER;
        context.graphics().fill(button.x(), button.y(), button.x() + button.size(),
                button.y() + button.size(), background);
        border(context, button.x(), button.y(), button.size(), button.size(), border);
        int iconSize = Math.min(ICON_RENDER_SIZE, button.size() - 4);
        int iconX = button.x() + (button.size() - iconSize) / 2;
        int iconY = button.y() + (button.size() - iconSize) / 2;
        context.graphics().blit(
                button.icon(), iconX, iconY, iconSize, iconSize,
                0.0F, 0.0F, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE,
                ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE);
        if (!button.enabled()) {
            context.graphics().fill(button.x() + 1, button.y() + 1,
                    button.x() + button.size(), button.y() + button.size(), 0x88000000);
        }
        if (!button.shortcutLabel().isBlank()) {
            int shortcutX = button.x() + button.size()
                    - font.width(button.shortcutLabel()) - 2;
            int shortcutY = button.y() + button.size() - 9;
            context.graphics().drawString(font, button.shortcutLabel(),
                    shortcutX + 1, shortcutY + 1, 0xFF000000, false);
            context.graphics().drawString(font, button.shortcutLabel(),
                    shortcutX, shortcutY, button.enabled() ? TEXT : MUTED, false);
        }
    }

    private void renderPlayers(VRenderContext context, Font font, State state) {
        Bounds bounds = playersBounds(context.screenHeight(), state);
        renderPanel(context, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        context.graphics().drawString(font, "Players (" + state.players().size() + ")",
                bounds.x() + 7, bounds.y() + 7, TEXT, false);
        int y = bounds.y() + 23;
        if (state.players().isEmpty()) {
            selectedPlayerId = null;
            context.graphics().drawString(font, "No connected players",
                    bounds.x() + 7, y, MUTED, false);
        } else {
            VttPlayerOption selected = resolveSelectedPlayer(state);
            List<VttPlayerOption> visiblePlayers = state.players().stream()
                    .limit(MAX_VISIBLE_PLAYERS).toList();
            for (int index = 0; index < visiblePlayers.size(); index++) {
                VttPlayerOption player = visiblePlayers.get(index);
                boolean local = player.id().equals(state.localPlayerId());
                Bounds row = playerRow(bounds, index);
                boolean selectedRow = selected != null && player.id().equals(selected.id());
                boolean hovered = row.contains(context.mouseX(), context.mouseY());
                if (selectedRow || hovered) {
                    context.graphics().fill(row.x(), row.y(), row.x() + row.width(),
                            row.y() + row.height(),
                            selectedRow ? BUTTON_ACTIVE : BUTTON_HOVER);
                }
                boolean ownsSelectedToken = player.id().equals(state.selectedSceneTokenOwnerId());
                String markers = (local ? " (you)" : "")
                        + (ownsSelectedToken ? " [owner]" : "");
                String name = trim(player.displayName(),
                        Math.max(8, 22 - markers.length())) + markers;
                context.graphics().drawString(font, name, row.x() + 3, y,
                        local ? ACTIVE_BORDER : TEXT, false);
                String role = player.spectator() ? "SPECTATOR" : player.role().name();
                context.graphics().drawString(font, role,
                        row.x() + row.width() - font.width(role) - 3, y,
                        player.role() == com.petrick.vtt.core.session.VttRole.MASTER
                                ? 0xFFFFCC55 : MUTED, false);
                y += 12;
            }
            if (state.players().size() > MAX_VISIBLE_PLAYERS) {
                context.graphics().drawString(font,
                        "+" + (state.players().size() - MAX_VISIBLE_PLAYERS) + " more",
                        bounds.x() + 7, y, MUTED, false);
            }
            if (selected != null) {
                String tokenLabel = selected.ownedTokenCount() == 1 ? " token" : " tokens";
                context.graphics().hLine(bounds.x() + 6, bounds.x() + bounds.width() - 6,
                        playerDetailsTop(bounds, state), 0xFF55555A);
                String selectedMode = selected.spectator()
                        ? selected.role().name() + " / SPECTATOR" : selected.role().name();
                context.graphics().drawString(font,
                        selectedMode + "  |  " + selected.ownedTokenCount() + tokenLabel,
                        bounds.x() + 7, playerDetailsTop(bounds, state) + 7,
                        selected.role() == com.petrick.vtt.core.session.VttRole.MASTER
                                ? 0xFFFFCC55 : MUTED, false);
                renderOwnedTokens(context, font, bounds, state, selected);
                if (state.master()) {
                    String token = state.selectedSceneTokenName().isBlank()
                            ? "Token: none selected"
                            : "Token: " + trim(state.selectedSceneTokenName(), 25);
                    context.graphics().drawString(font, token, bounds.x() + 7,
                            playerDetailsTop(bounds, state) + 19 + ownedTokenBlockHeight(state),
                            state.selectedSceneTokenName().isBlank() ? MUTED : TEXT, false);
                    renderPlayerActionRow(context, font, assignOwnerRow(bounds, state),
                            "Assign to " + trim(selected.displayName(), 18),
                            canAssignOwner(state, selected));
                    renderPlayerActionRow(context, font, clearOwnerRow(bounds, state),
                            "Clear token owner", canClearOwner(state));
                    renderPlayerActionRow(context, font, spectatorRow(bounds, state),
                            selected.spectator() ? "Disable spectator" : "Enable spectator",
                            selected.role() != com.petrick.vtt.core.session.VttRole.MASTER);
                }
            }
        }
    }

    private void renderOwnedTokens(
            VRenderContext context, Font font, Bounds bounds, State state,
            VttPlayerOption selectedPlayer
    ) {
        int top = playerDetailsTop(bounds, state) + 19;
        context.graphics().drawString(font, "Tokens - active scene",
                bounds.x() + 7, top, MUTED, false);
        List<VttOwnedTokenOption> ownedTokens = ownedTokens(state, selectedPlayer.id());
        if (ownedTokens.isEmpty()) {
            context.graphics().drawString(font, "No tokens in this scene",
                    bounds.x() + 10, top + 13, MUTED, false);
            return;
        }
        List<VttOwnedTokenOption> visibleTokens = ownedTokens.stream()
                .limit(MAX_VISIBLE_OWNED_TOKENS).toList();
        for (int index = 0; index < visibleTokens.size(); index++) {
            VttOwnedTokenOption token = visibleTokens.get(index);
            Bounds row = ownedTokenRow(bounds, state, index);
            boolean selected = token.id().equals(selectedOwnedTokenId);
            boolean hovered = row.contains(context.mouseX(), context.mouseY());
            if (selected || hovered) {
                context.graphics().fill(row.x(), row.y(), row.x() + row.width(),
                        row.y() + row.height(), selected ? BUTTON_ACTIVE : BUTTON_HOVER);
            }
            String status = (!token.visible() ? " [hidden]" : "")
                    + (!token.visionEnabled() ? " [no vision]" : "");
            context.graphics().drawString(font,
                    trim(token.displayName(), Math.max(8, 29 - status.length())) + status,
                    row.x() + 3, row.y() + 3,
                    token.visible() ? TEXT : MUTED, false);
        }
        if (ownedTokens.size() > MAX_VISIBLE_OWNED_TOKENS) {
            int more = ownedTokens.size() - MAX_VISIBLE_OWNED_TOKENS;
            context.graphics().drawString(font, "+" + more + " more",
                    bounds.x() + 10,
                    top + 13 + MAX_VISIBLE_OWNED_TOKENS * 14, MUTED, false);
        }
    }

    private void renderPlayerActionRow(
            VRenderContext context, Font font, Bounds row, String label, boolean enabled
    ) {
        boolean hovered = enabled && row.contains(context.mouseX(), context.mouseY());
        context.graphics().fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(),
                hovered ? BUTTON_HOVER : BUTTON_BACKGROUND);
        border(context, row.x(), row.y(), row.width(), row.height(),
                enabled ? PANEL_BORDER : DISABLED_BORDER);
        context.graphics().drawString(font, label, row.x() + 5, row.y() + 4,
                enabled ? TEXT : MUTED, false);
    }

    private VttPlayerOption resolveSelectedPlayer(State state) {
        VttPlayerOption selected = state.players().stream()
                .filter(player -> player.id().equals(selectedPlayerId))
                .findFirst().orElse(null);
        if (selected == null) {
            selected = state.players().stream()
                    .filter(player -> player.id().equals(state.localPlayerId()))
                    .findFirst().orElse(state.players().getFirst());
            selectedPlayerId = selected.id();
        }
        return selected;
    }

    private boolean selectPlayerAt(
            double mouseX, double mouseY, int screenHeight, State state
    ) {
        Bounds bounds = playersBounds(screenHeight, state);
        List<VttPlayerOption> visiblePlayers = state.players().stream()
                .limit(MAX_VISIBLE_PLAYERS).toList();
        for (int index = 0; index < visiblePlayers.size(); index++) {
            if (playerRow(bounds, index).contains(mouseX, mouseY)) {
                selectedPlayerId = visiblePlayers.get(index).id();
                selectedOwnedTokenId = null;
                return true;
            }
        }
        return false;
    }

    private Action playerActionAt(
            double mouseX, double mouseY, int screenHeight, State state
    ) {
        VttPlayerOption selected = state.players().stream()
                .filter(player -> player.id().equals(selectedPlayerId))
                .findFirst().orElse(null);
        if (selected == null) return Action.NONE;
        Bounds bounds = playersBounds(screenHeight, state);
        List<VttOwnedTokenOption> tokens = ownedTokens(state, selected.id()).stream()
                .limit(MAX_VISIBLE_OWNED_TOKENS).toList();
        for (int index = 0; index < tokens.size(); index++) {
            if (ownedTokenRow(bounds, state, index).contains(mouseX, mouseY)) {
                selectedOwnedTokenId = tokens.get(index).id();
                return Action.FOCUS_SELECTED_PLAYER_TOKEN;
            }
        }
        if (!state.master()) return Action.NONE;
        if (assignOwnerRow(bounds, state).contains(mouseX, mouseY)
                && canAssignOwner(state, selected)) {
            return Action.ASSIGN_SELECTED_TOKEN_OWNER;
        }
        if (clearOwnerRow(bounds, state).contains(mouseX, mouseY)
                && canClearOwner(state)) {
            return Action.CLEAR_SELECTED_TOKEN_OWNER;
        }
        if (spectatorRow(bounds, state).contains(mouseX, mouseY)
                && selected.role() != com.petrick.vtt.core.session.VttRole.MASTER) {
            return Action.TOGGLE_SELECTED_PLAYER_SPECTATOR;
        }
        return Action.NONE;
    }

    private boolean canAssignOwner(State state, VttPlayerOption player) {
        return !state.selectedSceneTokenId().isBlank()
                && !player.spectator()
                && !player.id().equals(state.selectedSceneTokenOwnerId());
    }

    private boolean canClearOwner(State state) {
        return !state.selectedSceneTokenId().isBlank()
                && !state.selectedSceneTokenOwnerId().isBlank();
    }

    public String getSelectedPlayerId() {
        return selectedPlayerId;
    }

    public String getSelectedOwnedTokenId() {
        return selectedOwnedTokenId;
    }

    private Bounds playerRow(Bounds bounds, int index) {
        return new Bounds(bounds.x() + 4, bounds.y() + 20 + index * 12,
                bounds.width() - 8, 12);
    }

    private int playerDetailsTop(Bounds bounds, State state) {
        return bounds.y() + bounds.height() - playerFooterHeight(state) + 6;
    }

    private Bounds ownedTokenRow(Bounds bounds, State state, int index) {
        return new Bounds(bounds.x() + 7,
                playerDetailsTop(bounds, state) + 29 + index * 14,
                bounds.width() - 14, 14);
    }

    private Bounds assignOwnerRow(Bounds bounds, State state) {
        return new Bounds(bounds.x() + 6,
                playerDetailsTop(bounds, state) + 31 + ownedTokenBlockHeight(state),
                bounds.width() - 12, 16);
    }

    private Bounds clearOwnerRow(Bounds bounds, State state) {
        return new Bounds(bounds.x() + 6,
                playerDetailsTop(bounds, state) + 50 + ownedTokenBlockHeight(state),
                bounds.width() - 12, 16);
    }

    private Bounds spectatorRow(Bounds bounds, State state) {
        return new Bounds(bounds.x() + 6,
                playerDetailsTop(bounds, state) + 69 + ownedTokenBlockHeight(state),
                bounds.width() - 12, 16);
    }

    private List<VttOwnedTokenOption> ownedTokens(State state, String playerId) {
        if (playerId == null || playerId.isBlank()) return List.of();
        return state.ownedTokens().stream()
                .filter(token -> playerId.equals(token.ownerId()))
                .toList();
    }

    private String selectedPlayerIdForLayout(State state) {
        boolean selectedStillConnected = state.players().stream()
                .anyMatch(player -> player.id().equals(selectedPlayerId));
        if (selectedStillConnected) return selectedPlayerId;
        return state.players().stream()
                .filter(player -> player.id().equals(state.localPlayerId()))
                .map(VttPlayerOption::id)
                .findFirst()
                .orElse(state.players().isEmpty() ? "" : state.players().getFirst().id());
    }

    private int ownedTokenBlockHeight(State state) {
        int count = ownedTokens(state, selectedPlayerIdForLayout(state)).size();
        int rows = Math.max(1, Math.min(MAX_VISIBLE_OWNED_TOKENS, count));
        int overflow = count > MAX_VISIBLE_OWNED_TOKENS ? 12 : 0;
        return 14 + rows * 14 + overflow;
    }

    private int playerFooterHeight(State state) {
        if (state.players().isEmpty()) return 5;
        return (state.master() ? 97 : 32) + ownedTokenBlockHeight(state);
    }

    private void renderCreation(VRenderContext context, Font font, State state) {
        Bounds bounds = creationBounds(context.screenWidth(), context.screenHeight());
        renderPanel(context, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        context.graphics().drawString(font, "Create & Manage", bounds.x() + 8, bounds.y() + 8,
                TEXT, false);
        renderMenuRow(context, font, bounds, 0, "Create Scene", true, false);
        renderMenuRow(context, font, bounds, 1, "Create Token", true, false);
        renderMenuRow(context, font, bounds, 2,
                state.activeSceneName().isBlank() ? "Delete Active Scene"
                        : "Delete Scene: " + trim(state.activeSceneName(), 18),
                state.canDeleteActiveScene(), true);
        renderMenuRow(context, font, bounds, 3,
                state.selectedTokenName().isBlank() ? "Delete Selected Token"
                        : "Delete Token: " + trim(state.selectedTokenName(), 18),
                state.canDeleteSelectedToken(), true);
    }

    private void renderMenuRow(
            VRenderContext context, Font font, Bounds bounds, int index,
            String label, boolean enabled, boolean danger
    ) {
        Bounds row = creationRow(bounds, index);
        boolean hovered = row.contains(context.mouseX(), context.mouseY());
        context.graphics().fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(),
                enabled && hovered ? BUTTON_HOVER : BUTTON_BACKGROUND);
        border(context, row.x(), row.y(), row.width(), row.height(),
                enabled ? PANEL_BORDER : DISABLED_BORDER);
        context.graphics().drawString(font, label, row.x() + 6, row.y() + 5,
                enabled ? danger ? DANGER : TEXT : MUTED, false);
    }

    private Action creationActionAt(
            double mouseX, double mouseY, int screenWidth, int screenHeight, State state
    ) {
        Bounds bounds = creationBounds(screenWidth, screenHeight);
        for (int index = 0; index < 4; index++) {
            if (creationRow(bounds, index).contains(mouseX, mouseY)) {
                return switch (index) {
                    case 0 -> Action.CREATE_SCENE;
                    case 1 -> Action.CREATE_TOKEN;
                    case 2 -> state.canDeleteActiveScene()
                            ? Action.DELETE_ACTIVE_SCENE : Action.NONE;
                    case 3 -> state.canDeleteSelectedToken()
                            ? Action.DELETE_SELECTED_TOKEN : Action.NONE;
                    default -> Action.NONE;
                };
            }
        }
        return Action.NONE;
    }

    private Bounds creationRow(Bounds bounds, int index) {
        return new Bounds(bounds.x() + 7, bounds.y() + 24 + index * 23,
                bounds.width() - 14, 19);
    }

    private Bounds playersBounds(int screenHeight, State state) {
        int rows = Math.max(1, Math.min(MAX_VISIBLE_PLAYERS, state.players().size()));
        if (state.players().size() > MAX_VISIBLE_PLAYERS) rows++;
        int footer = playerFooterHeight(state);
        return new Bounds(MARGIN, topPopupY(screenHeight),
                240, 29 + rows * 12 + footer);
    }

    private Bounds creationBounds(int screenWidth, int screenHeight) {
        int width = 230;
        int bottomPanelY = screenHeight - MARGIN - PADDING * 2 - buttonSize(screenHeight);
        int y = Math.max(topPopupY(screenHeight), bottomPanelY - GAP - 116);
        return new Bounds((screenWidth - width) / 2, y,
                width, 116);
    }

    private int topPopupY(int screenHeight) {
        return MARGIN + PADDING * 2 + buttonSize(screenHeight) + GAP;
    }

    private void renderTooltip(VRenderContext context, Font font, Button button) {
        boolean unavailableHistory = !button.enabled()
                && (button.action() == Action.UNDO || button.action() == Action.REDO);
        String suffix = button.enabled() || unavailableHistory
                ? shortcut(button.action()) : " (coming later)";
        String text = button.tooltip() + suffix;
        int width = font.width(text) + 8;
        int x = Math.max(3, Math.min(context.screenWidth() - width - 3, context.mouseX() + 10));
        int y = Math.max(3, Math.min(context.screenHeight() - 17, context.mouseY() + 10));
        context.graphics().fill(x, y, x + width, y + 15, 0xEE08080A);
        border(context, x, y, width, 15, PANEL_BORDER);
        context.graphics().drawString(font, text, x + 4, y + 4,
                button.enabled() ? TEXT : MUTED, false);
    }

    private String shortcut(Action action) {
        return switch (action) {
            case HAND -> " [H]";
            case SELECT -> " [S]";
            case FOG -> " [F]";
            case WALL -> " [W]";
            case DOOR -> " [D]";
            case MEASURE -> " [M]";
            case UNDO -> " [Ctrl+Z]";
            case REDO -> " [Ctrl+Y]";
            default -> "";
        };
    }

    private String toolShortcut(Action action) {
        return switch (action) {
            case HAND -> "H";
            case SELECT -> "S";
            case FOG -> "F";
            case WALL -> "W";
            case DOOR -> "D";
            case MEASURE -> "M";
            default -> "?";
        };
    }

    private String toolTooltip(Action action) {
        return switch (action) {
            case HAND -> "Hand Tool";
            case SELECT -> "Select Tool";
            case FOG -> "Fog Tool";
            case WALL -> "Wall Tool";
            case DOOR -> "Door Tool";
            case MEASURE -> "Measure Tool";
            default -> action.name();
        };
    }

    private String historyTooltip(String action, String description) {
        return description == null || description.isBlank()
                ? action : action + ": " + description;
    }

    private String toolId(Action action) {
        return switch (action) {
            case HAND -> "hand";
            case SELECT -> "select";
            case FOG -> "fog";
            case WALL -> "wall";
            case DOOR -> "door";
            case MEASURE -> "measure";
            default -> "";
        };
    }

    private Button button(
            Action action, int x, int y, int size, String shortcutLabel, String tooltip,
            boolean enabled, boolean active
    ) {
        return new Button(action, icon(action), x, y, size, shortcutLabel, tooltip,
                enabled, active);
    }

    private int buttonSize(int screenHeight) {
        return screenHeight < 400 ? 22 : 26;
    }

    private ResourceLocation icon(Action action) {
        String fileName = action.name().toLowerCase(Locale.ROOT) + ".png";
        return ResourceLocation.fromNamespaceAndPath(
                VTT.MOD_ID, "textures/gui/editor_hud/" + fileName);
    }

    private void border(VRenderContext context, int x, int y, int width, int height, int color) {
        context.graphics().hLine(x, x + width, y, color);
        context.graphics().hLine(x, x + width, y + height, color);
        context.graphics().vLine(x, y, y + height, color);
        context.graphics().vLine(x + width, y, y + height, color);
    }

    private String trim(String value, int maximumLength) {
        if (value == null) return "";
        return value.length() <= maximumLength
                ? value : value.substring(0, Math.max(0, maximumLength - 3)) + "...";
    }

    public enum Action {
        NONE,
        CLOSE,
        PLAYERS,
        ASSIGN_SELECTED_TOKEN_OWNER,
        CLEAR_SELECTED_TOKEN_OWNER,
        TOGGLE_SELECTED_PLAYER_SPECTATOR,
        FOCUS_SELECTED_PLAYER_TOKEN,
        OUTLINER,
        SETTINGS,
        HAND,
        SELECT,
        FOG,
        WALL,
        DOOR,
        MEASURE,
        UNDO,
        REDO,
        SCENES,
        TOKENS,
        CREATION,
        CREATE_SCENE,
        CREATE_TOKEN,
        DELETE_ACTIVE_SCENE,
        DELETE_SELECTED_TOKEN;

        private boolean isPopup() {
            return this == PLAYERS || this == SETTINGS || this == CREATION;
        }
    }

    public record State(
            boolean master,
            boolean spectator,
            String activeToolId,
            boolean canUndo,
            boolean canRedo,
            String undoDescription,
            String redoDescription,
            boolean playersOpen,
            boolean settingsOpen,
            boolean creationOpen,
            boolean scenesOpen,
            boolean tokensOpen,
            boolean outlinerOpen,
            List<VttPlayerOption> players,
            List<VttOwnedTokenOption> ownedTokens,
            String localPlayerId,
            String selectedSceneTokenId,
            String selectedSceneTokenName,
            String selectedSceneTokenOwnerId,
            String activeSceneName,
            boolean canDeleteActiveScene,
            String selectedTokenName,
            boolean canDeleteSelectedToken
    ) {
        public State {
            activeToolId = activeToolId == null ? "" : activeToolId;
            undoDescription = undoDescription == null ? "" : undoDescription;
            redoDescription = redoDescription == null ? "" : redoDescription;
            players = players == null ? List.of() : List.copyOf(players);
            ownedTokens = ownedTokens == null ? List.of() : List.copyOf(ownedTokens);
            localPlayerId = localPlayerId == null ? "" : localPlayerId;
            selectedSceneTokenId = selectedSceneTokenId == null ? "" : selectedSceneTokenId;
            selectedSceneTokenName = selectedSceneTokenName == null ? "" : selectedSceneTokenName;
            selectedSceneTokenOwnerId =
                    selectedSceneTokenOwnerId == null ? "" : selectedSceneTokenOwnerId;
            activeSceneName = activeSceneName == null ? "" : activeSceneName;
            selectedTokenName = selectedTokenName == null ? "" : selectedTokenName;
        }
    }

    private record Button(
            Action action, ResourceLocation icon, int x, int y, int size,
            String shortcutLabel, String tooltip, boolean enabled, boolean active
    ) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
        }
    }

    private record Bounds(int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width
                    && mouseY >= y && mouseY <= y + height;
        }
    }
}
