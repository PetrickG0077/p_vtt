package com.petrick.vtt.editor.hud;

import com.petrick.vtt.editor.token.VttPlayerOption;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/** Responsive editor HUD shell rendered above the tabletop and legacy panels. */
public final class EditorHudOverlay {
    private static final int MARGIN = 8;
    private static final int GAP = 3;
    private static final int PADDING = 4;
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

    public void render(VRenderContext context, Font font, State state) {
        List<Button> buttons = buttons(context.screenWidth(), context.screenHeight(), state);
        renderGroups(context, state);
        Button hovered = null;
        for (Button button : buttons) {
            renderButton(context, font, button);
            if (button.contains(context.mouseX(), context.mouseY())) hovered = button;
        }
        if (state.playersOpen()) renderPlayers(context, font, state);
        if (state.settingsOpen()) renderSettings(context, font, state);
        if (state.creationOpen() && state.master()) renderCreation(context, font, state);
        if (hovered != null) renderTooltip(context, font, hovered);
    }

    public Action actionAt(double mouseX, double mouseY, int screenWidth, int screenHeight, State state) {
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
        return state.playersOpen() && playersBounds(state).contains(mouseX, mouseY)
                || state.settingsOpen() && settingsBounds(screenWidth).contains(mouseX, mouseY)
                || state.creationOpen() && state.master()
                && creationBounds(screenWidth, screenHeight).contains(mouseX, mouseY);
    }

    private List<Button> buttons(int screenWidth, int screenHeight, State state) {
        int size = buttonSize(screenHeight);
        List<Button> result = new ArrayList<>();

        int topX = MARGIN + PADDING;
        int topY = MARGIN + PADDING;
        result.add(button(Action.CLOSE, topX, topY, size, "X", "Close VTT", true, false));
        topX += size + GAP;
        result.add(button(Action.PLAYERS, topX, topY, size, "P", "Players", true,
                state.playersOpen()));
        if (state.master()) {
            topX += size + GAP;
            result.add(button(Action.OUTLINER, topX, topY, size, "O", "Scene Outliner", true,
                    state.outlinerOpen()));
        }

        int settingsX = screenWidth / 2 - size / 2;
        result.add(button(Action.SETTINGS, settingsX, topY, size, "*", "Settings", true,
                state.settingsOpen()));

        List<Action> tools = new ArrayList<>(List.of(Action.HAND, Action.SELECT));
        if (state.master()) tools.addAll(List.of(
                Action.FOG, Action.WALL, Action.DOOR, Action.MEASURE));
        int rightCount = tools.size() + 2;
        int separator = 7;
        int rightHeight = PADDING * 2 + rightCount * size
                + (rightCount - 1) * GAP + separator;
        int rightX = screenWidth - MARGIN - PADDING - size;
        int rightY = Math.max(MARGIN + PADDING, (screenHeight - rightHeight) / 2 + PADDING);
        for (Action tool : tools) {
            boolean enabled = tool != Action.MEASURE;
            result.add(button(tool, rightX, rightY, size, toolLabel(tool), toolTooltip(tool),
                    enabled, toolId(tool).equals(state.activeToolId())));
            rightY += size + GAP;
        }
        rightY += separator;
        result.add(button(Action.UNDO, rightX, rightY, size, "UN", "Undo", false, false));
        rightY += size + GAP;
        result.add(button(Action.REDO, rightX, rightY, size, "RE", "Redo", false, false));

        if (state.master()) {
            int bottomWidth = PADDING * 2 + size * 3 + GAP * 2;
            int bottomX = (screenWidth - bottomWidth) / 2 + PADDING;
            int bottomY = screenHeight - MARGIN - PADDING - size;
            result.add(button(Action.SCENES, bottomX, bottomY, size, "SC", "Scenes", true,
                    state.scenesOpen()));
            bottomX += size + GAP;
            result.add(button(Action.TOKENS, bottomX, bottomY, size, "TK", "Tokens", true,
                    state.tokensOpen()));
            bottomX += size + GAP;
            result.add(button(Action.CREATION, bottomX, bottomY, size, "+", "Create & Manage",
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
        renderPanel(context, screenWidth / 2 - size / 2 - PADDING, MARGIN,
                size + PADDING * 2, size + PADDING * 2);

        int toolCount = state.master() ? 6 : 2;
        int rightCount = toolCount + 2;
        int rightHeight = PADDING * 2 + rightCount * size + (rightCount - 1) * GAP + 7;
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
        int color = button.enabled() ? TEXT : MUTED;
        context.graphics().drawCenteredString(font, button.label(),
                button.x() + button.size() / 2,
                button.y() + (button.size() - 8) / 2, color);
    }

    private void renderPlayers(VRenderContext context, Font font, State state) {
        Bounds bounds = playersBounds(state);
        renderPanel(context, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        context.graphics().drawString(font, "Players (" + state.players().size() + ")",
                bounds.x() + 7, bounds.y() + 7, TEXT, false);
        int y = bounds.y() + 23;
        if (state.players().isEmpty()) {
            context.graphics().drawString(font, "No connected players",
                    bounds.x() + 7, y, MUTED, false);
        } else {
            for (VttPlayerOption player : state.players().stream()
                    .limit(MAX_VISIBLE_PLAYERS).toList()) {
                boolean local = player.id().equals(state.localPlayerId());
                String name = trim(player.displayName(), 22) + (local ? " (you)" : "");
                context.graphics().drawString(font, name, bounds.x() + 7, y,
                        local ? ACTIVE_BORDER : TEXT, false);
                y += 12;
            }
            if (state.players().size() > MAX_VISIBLE_PLAYERS) {
                context.graphics().drawString(font,
                        "+" + (state.players().size() - MAX_VISIBLE_PLAYERS) + " more",
                        bounds.x() + 7, y, MUTED, false);
            }
        }
        if (state.master()) {
            context.graphics().drawString(font, "Player properties: coming later",
                    bounds.x() + 7, bounds.y() + bounds.height() - 14, MUTED, false);
        }
    }

    private void renderSettings(VRenderContext context, Font font, State state) {
        Bounds bounds = settingsBounds(context.screenWidth());
        renderPanel(context, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        context.graphics().drawString(font, "VTT Settings", bounds.x() + 8, bounds.y() + 8,
                TEXT, false);
        context.graphics().drawString(font, "Settings panel coming in a future step",
                bounds.x() + 8, bounds.y() + 25, MUTED, false);
        context.graphics().drawString(font, "Current role: " + (state.master() ? "MASTER" : "PLAYER"),
                bounds.x() + 8, bounds.y() + 39,
                state.master() ? OPEN_BORDER : ACTIVE_BORDER, false);
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

    private Bounds playersBounds(State state) {
        int rows = Math.max(1, Math.min(MAX_VISIBLE_PLAYERS, state.players().size()));
        if (state.players().size() > MAX_VISIBLE_PLAYERS) rows++;
        int footer = state.master() ? 20 : 5;
        return new Bounds(MARGIN, MARGIN + 45, 210, 29 + rows * 12 + footer);
    }

    private Bounds settingsBounds(int screenWidth) {
        return new Bounds(screenWidth / 2 - 120, MARGIN + 45, 240, 58);
    }

    private Bounds creationBounds(int screenWidth, int screenHeight) {
        int width = 230;
        int y = Math.max(MARGIN + 52, screenHeight - MARGIN - 32 - 126);
        return new Bounds((screenWidth - width) / 2, y,
                width, 116);
    }

    private void renderTooltip(VRenderContext context, Font font, Button button) {
        String suffix = button.enabled() ? shortcut(button.action()) : " (coming later)";
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
            default -> "";
        };
    }

    private String toolLabel(Action action) {
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

    private String toolId(Action action) {
        return switch (action) {
            case HAND -> "hand";
            case SELECT -> "select";
            case FOG -> "fog";
            case WALL -> "wall";
            case DOOR -> "door";
            default -> "";
        };
    }

    private Button button(
            Action action, int x, int y, int size, String label, String tooltip,
            boolean enabled, boolean active
    ) {
        return new Button(action, x, y, size, label, tooltip, enabled, active);
    }

    private int buttonSize(int screenHeight) {
        return screenHeight < 400 ? 24 : 28;
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
            String activeToolId,
            boolean playersOpen,
            boolean settingsOpen,
            boolean creationOpen,
            boolean scenesOpen,
            boolean tokensOpen,
            boolean outlinerOpen,
            List<VttPlayerOption> players,
            String localPlayerId,
            String activeSceneName,
            boolean canDeleteActiveScene,
            String selectedTokenName,
            boolean canDeleteSelectedToken
    ) {
        public State {
            activeToolId = activeToolId == null ? "" : activeToolId;
            players = players == null ? List.of() : List.copyOf(players);
            localPlayerId = localPlayerId == null ? "" : localPlayerId;
            activeSceneName = activeSceneName == null ? "" : activeSceneName;
            selectedTokenName = selectedTokenName == null ? "" : selectedTokenName;
        }
    }

    private record Button(
            Action action, int x, int y, int size, String label, String tooltip,
            boolean enabled, boolean active
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
