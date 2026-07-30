package com.petrick.vtt.editor.dialog;

import com.petrick.vtt.editor.hud.EditorHudTheme;
import com.petrick.vtt.editor.token.TokenCreationDraft;
import com.petrick.vtt.editor.token.TokenStateDraft;
import com.petrick.vtt.editor.token.VttPlayerOption;
import com.petrick.vtt.editor.overlay.EditorScrollbar;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Janela visual para criação e edição básica de tokens.
 */
public final class TokenCreationDialog {

    public enum Action {
        NONE,
        CHOOSE_IMAGE,
        CREATE,
        DISCARD
    }

    private enum Field {
        NONE,
        NAME,
        PLAYER,
        NOTES,
        STATE_NAME
    }

    private static final int DIALOG_WIDTH = 430;

    private static final int DIALOG_HEIGHT = 325;

    private static final int PANEL_BACKGROUND = 0xEE000000;

    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private static final int MUTED_TEXT_COLOR = 0xFFAAAAAA;

    private static final int INPUT_BACKGROUND = 0xAA050505;

    private static final int BUTTON_BACKGROUND = 0x66000000;

    private static final int IMAGE_BOX_SIZE = 92;

    private static final int INPUT_WIDTH = 210;

    private static final int INPUT_HEIGHT = 22;

    private static final int BUTTON_WIDTH = 118;

    private static final int BUTTON_HEIGHT = 30;

    private static final int STATE_PANEL_X_OFFSET = 26;

    private static final int STATE_PANEL_Y_OFFSET = 150;

    private static final int STATE_PANEL_WIDTH = 378;

    private static final int STATE_PANEL_HEIGHT = 88;

    private static final int STATE_ROW_HEIGHT = 15;

    private static final int STATE_ADD_BUTTON_SIZE = 16;

    private static final int STATE_DELETE_BUTTON_SIZE = 12;

    private static final int STATE_DUPLICATE_BUTTON_SIZE = 12;

    private static final int STATE_DEFAULT_BUTTON_SIZE = 12;

    private static final int STATE_ROW_BUTTON_GAP = 3;

    private static final int MAX_VISIBLE_STATES = 2;

    private static final int HOVERED_STATE_BACKGROUND = 0x33222222;

    private Field activeField = Field.NONE;

    private String stateNameEditBuffer = "";

    private int stateListScrollOffset = 0;
    private boolean draggingStateScrollbar;
    private String playerSearch = "";
    private static final int PLAYER_ROW_HEIGHT = 18;
    private static final int MAX_VISIBLE_PLAYERS = 5;

    public void render(
            VRenderContext context,
            Font font,
            TokenCreationDraft draft,
            List<VttPlayerOption> playerOptions,
            String creationFolder
    ) {
        int x = getDialogX(context);
        int y = getDialogY(context);

        renderDimBackground(context);
        renderPanel(context, x, y);

        drawCenteredString(
                context,
                font,
                draft.isEditing() ? "EDIT TOKEN" : "CREATE TOKEN",
                x + DIALOG_WIDTH / 2,
                y + 14,
                TITLE_COLOR
        );
        if (!draft.isEditing() && creationFolder != null
                && !creationFolder.isBlank()) {
            drawCenteredString(
                    context, font, "Create in: Assets/" + creationFolder,
                    x + DIALOG_WIDTH / 2, y + 35, MUTED_TEXT_COLOR);
        }

        renderImageButton(context, font, draft, x + 26, y + 52);

        renderInputField(
                context,
                font,
                "Name:",
                draft.getName(),
                x + 145,
                y + 56,
                Field.NAME
        );

        renderInputField(
                context,
                font,
                "Player:",
                playerFieldText(draft, playerOptions),
                x + 145,
                y + 88,
                Field.PLAYER
        );

        renderInputField(
                context,
                font,
                "Notes:",
                draft.getNotes(),
                x + 145,
                y + 120,
                Field.NOTES
        );

        renderStatesPanel(context, font, draft, x + STATE_PANEL_X_OFFSET, y + STATE_PANEL_Y_OFFSET);

        if (activeField == Field.PLAYER) {
            renderPlayerDropdown(context, font, draft, playerOptions);
        }

        if (draft.hasError()) {
            drawCenteredString(
                    context,
                    font,
                    draft.getErrorMessage(),
                    x + DIALOG_WIDTH / 2,
                    y + 224,
                    0xFFFF5555
            );
        }

        renderButton(
                context,
                font,
                draft.isEditing() ? "Save" : "Create",
                getCreateButtonX(context),
                getCreateButtonY(context),
                isMouseOverCreateButton(context)
        );

        renderButton(
                context,
                font,
                draft.isEditing() ? "Cancel" : "Discard",
                getDiscardButtonX(context),
                getDiscardButtonY(context),
                isMouseOverDiscardButton(context)
        );
    }

    public Action mouseClicked(
            int screenWidth,
            int screenHeight,
            TokenCreationDraft draft,
            double mouseX,
            double mouseY,
            int button,
            List<VttPlayerOption> playerOptions
    ) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return Action.NONE;
        }

        if (!containsPoint(screenWidth, screenHeight, mouseX, mouseY)) {
            return Action.NONE;
        }

        if (isStateScrollbarAt(screenWidth, screenHeight, draft, mouseX, mouseY)) {
            draggingStateScrollbar = true;
            updateStateScrollFromMouse(screenWidth, screenHeight, draft, mouseY);
            activeField = Field.NONE;
            return Action.NONE;
        }

        if (activeField == Field.PLAYER) {
            PlayerChoice choice = findPlayerChoiceAt(screenWidth, screenHeight, mouseX, mouseY, playerOptions);
            if (choice != null) {
                draft.setPlayer(choice.playerId());
                playerSearch = "";
                activeField = Field.NONE;
                return Action.NONE;
            }
        }

        if (isMouseOverImageButton(screenWidth, screenHeight, mouseX, mouseY)) {
            activeField = Field.NONE;
            return Action.CHOOSE_IMAGE;
        }

        if (isMouseOverAddStateButton(screenWidth, screenHeight, mouseX, mouseY)) {
            activeField = Field.NONE;
            draft.addState();
            stateListScrollOffset = getMaxStateListScrollOffset(draft);
            return Action.NONE;
        }

        TokenStateDraft stateToSetAsDefault = findStateDefaultButtonAt(
                screenWidth,
                screenHeight,
                draft,
                mouseX,
                mouseY
        );

        if (stateToSetAsDefault != null) {
            activeField = Field.NONE;
            draft.setDefaultState(stateToSetAsDefault.getId());
            draft.selectState(stateToSetAsDefault.getId());
            return Action.NONE;
        }

        TokenStateDraft stateToDuplicate = findStateDuplicateButtonAt(
                screenWidth,
                screenHeight,
                draft,
                mouseX,
                mouseY
        );

        if (stateToDuplicate != null) {
            activeField = Field.NONE;
            draft.duplicateState(stateToDuplicate.getId());
            stateListScrollOffset = getMaxStateListScrollOffset(draft);
            return Action.NONE;
        }

        TokenStateDraft stateToDelete = findStateDeleteButtonAt(
                screenWidth,
                screenHeight,
                draft,
                mouseX,
                mouseY
        );

        if (stateToDelete != null) {
            activeField = Field.NONE;
            draft.deleteState(stateToDelete.getId());
            clampStateListScrollOffset(draft);
            return Action.NONE;
        }

        TokenStateDraft clickedState = findStateAt(
                screenWidth,
                screenHeight,
                draft,
                mouseX,
                mouseY
        );

        if (clickedState != null) {
            activeField = Field.NONE;
            draft.selectState(clickedState.getId());
            return Action.NONE;
        }

        if (isMouseOverSelectedStateNameField(screenWidth, screenHeight, mouseX, mouseY)) {
            activeField = Field.STATE_NAME;

            TokenStateDraft selectedState = draft.getSelectedState();
            stateNameEditBuffer = selectedState == null
                    ? ""
                    : selectedState.getDisplayName();

            return Action.NONE;
        }

        if (isPointInside(
                mouseX,
                mouseY,
                getCreateButtonX(screenWidth),
                getCreateButtonY(screenHeight),
                BUTTON_WIDTH,
                BUTTON_HEIGHT
        )) {
            activeField = Field.NONE;
            return Action.CREATE;
        }

        if (isPointInside(
                mouseX,
                mouseY,
                getDiscardButtonX(screenWidth),
                getDiscardButtonY(screenHeight),
                BUTTON_WIDTH,
                BUTTON_HEIGHT
        )) {
            activeField = Field.NONE;
            return Action.DISCARD;
        }

        activeField = findFieldAt(screenWidth, screenHeight, mouseX, mouseY);
        if (activeField == Field.PLAYER) playerSearch = "";

        return Action.NONE;
    }

    public boolean keyPressed(
            TokenCreationDraft draft,
            int keyCode
    ) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (activeField == Field.STATE_NAME) {
                activeField = Field.NONE;
                stateNameEditBuffer = "";
                return true;
            }

            activeField = Field.NONE;
            return true;
        }

        if (activeField == Field.NONE && keyCode == GLFW.GLFW_KEY_N) {
            TokenStateDraft selectedState = draft.getSelectedState();

            if (selectedState != null) {
                activeField = Field.STATE_NAME;
                stateNameEditBuffer = selectedState.getDisplayName();
                return true;
            }
        }

        if (activeField == Field.NONE) {
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                return draft.deleteSelectedState();
            }

            return false;
        }

        if (activeField == Field.STATE_NAME) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                stateNameEditBuffer = removeLastCharacter(stateNameEditBuffer);
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmStateNameEdit(draft);
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_TAB) {
                confirmStateNameEdit(draft);
                activeField = Field.NAME;
                return true;
            }

            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            backspaceActiveField(draft);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            activeField = Field.NONE;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_TAB) {
            focusNextField();
            return true;
        }

        return true;
    }

    public boolean charTyped(
            TokenCreationDraft draft,
            char codePoint
    ) {
        if (activeField == Field.NONE) {
            return false;
        }

        if (activeField == Field.STATE_NAME) {
            if (!isAllowedTextCharacter(codePoint)) {
                return true;
            }

            if (stateNameEditBuffer.length() < 32) {
                stateNameEditBuffer += codePoint;
            }

            return true;
        }

        if (!isAllowedTextCharacter(codePoint)) {
            return true;
        }

        appendToActiveField(draft, codePoint);

        return true;
    }

    public boolean mouseScrolled(
            TokenCreationDraft draft,
            double mouseX,
            double mouseY,
            double scrollY,
            int screenWidth,
            int screenHeight
    ) {
        if (draft == null) {
            return false;
        }

        if (!isMouseOverStatesPanel(screenWidth, screenHeight, mouseX, mouseY)) {
            return false;
        }

        int maxOffset = getMaxStateListScrollOffset(draft);

        if (maxOffset <= 0) {
            return true;
        }

        if (scrollY < 0) {
            stateListScrollOffset++;
        } else if (scrollY > 0) {
            stateListScrollOffset--;
        }

        clampStateListScrollOffset(draft);

        return true;
    }

    public boolean mouseDragged(TokenCreationDraft draft, double mouseY,
                                int screenWidth, int screenHeight) {
        if (!draggingStateScrollbar || draft == null) return false;
        updateStateScrollFromMouse(screenWidth, screenHeight, draft, mouseY);
        return true;
    }

    public boolean mouseReleased() {
        boolean wasDragging = draggingStateScrollbar;
        draggingStateScrollbar = false;
        return wasDragging;
    }

    private boolean isStateScrollbarAt(int screenWidth, int screenHeight,
                                       TokenCreationDraft draft, double mouseX, double mouseY) {
        if (draft.getStates().size() <= MAX_VISIBLE_STATES) return false;
        int panelX = getDialogX(screenWidth) + STATE_PANEL_X_OFFSET;
        int panelY = getDialogY(screenHeight) + STATE_PANEL_Y_OFFSET;
        return EditorScrollbar.contains(mouseX, mouseY,
                panelX + STATE_PANEL_WIDTH - 15, panelY + 25,
                9, STATE_ROW_HEIGHT * MAX_VISIBLE_STATES);
    }

    private void updateStateScrollFromMouse(int screenWidth, int screenHeight,
                                            TokenCreationDraft draft, double mouseY) {
        int panelY = getDialogY(screenHeight) + STATE_PANEL_Y_OFFSET;
        stateListScrollOffset = EditorScrollbar.offsetForMouse(mouseY, panelY + 25,
                STATE_ROW_HEIGHT * MAX_VISIBLE_STATES, draft.getStates().size(), MAX_VISIBLE_STATES);
    }

    private void confirmStateNameEdit(TokenCreationDraft draft) {
        TokenStateDraft selectedState = draft.getSelectedState();

        if (selectedState == null) {
            activeField = Field.NONE;
            stateNameEditBuffer = "";
            return;
        }

        selectedState.setDisplayName(stateNameEditBuffer);

        activeField = Field.NONE;
        stateNameEditBuffer = "";
        draft.clearError();
    }

    private boolean isMouseOverSelectedStateNameField(
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        int x = getDialogX(screenWidth) + STATE_PANEL_X_OFFSET;
        int y = getDialogY(screenHeight) + STATE_PANEL_Y_OFFSET;

        int fieldX = x + 8 + 48;
        int fieldY = y + STATE_PANEL_HEIGHT - 28;
        int fieldWidth = 250;

        return isPointInside(
                mouseX,
                mouseY,
                fieldX,
                fieldY,
                fieldWidth,
                INPUT_HEIGHT
        );
    }

    private boolean isMouseOverStatesPanel(
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        int x = getDialogX(screenWidth) + STATE_PANEL_X_OFFSET;
        int y = getDialogY(screenHeight) + STATE_PANEL_Y_OFFSET;

        return isPointInside(
                mouseX,
                mouseY,
                x,
                y,
                STATE_PANEL_WIDTH,
                STATE_PANEL_HEIGHT
        );
    }

    private void clampStateListScrollOffset(TokenCreationDraft draft) {
        int maxOffset = getMaxStateListScrollOffset(draft);

        if (stateListScrollOffset < 0) {
            stateListScrollOffset = 0;
        }

        if (stateListScrollOffset > maxOffset) {
            stateListScrollOffset = maxOffset;
        }
    }

    private int getMaxStateListScrollOffset(TokenCreationDraft draft) {
        if (draft == null) {
            return 0;
        }

        return Math.max(0, draft.getStates().size() - MAX_VISIBLE_STATES);
    }

    private void renderDimBackground(VRenderContext context) {
        context.graphics().fill(
                0,
                0,
                context.screenWidth(),
                context.screenHeight(),
                0x99000000
        );
    }

    private void renderPanel(
            VRenderContext context,
            int x,
            int y
    ) {
        context.graphics().fill(
                x,
                y,
                x + DIALOG_WIDTH,
                y + DIALOG_HEIGHT,
                PANEL_BACKGROUND
        );

        drawBorder(
                context,
                x,
                y,
                DIALOG_WIDTH,
                DIALOG_HEIGHT,
                EditorHudTheme.outline()
        );
    }

    private void renderImageButton(
            VRenderContext context,
            Font font,
            TokenCreationDraft draft,
            int x,
            int y
    ) {
        boolean hovered = isMouseOverImageButton(
                context.screenWidth(),
                context.screenHeight(),
                context.mouseX(),
                context.mouseY()
        );

        context.graphics().fill(
                x,
                y,
                x + IMAGE_BOX_SIZE,
                y + IMAGE_BOX_SIZE,
                0xAA050505
        );

        drawBorder(
                context,
                x,
                y,
                IMAGE_BOX_SIZE,
                IMAGE_BOX_SIZE,
                hovered ? EditorHudTheme.opaqueSelection() : EditorHudTheme.outline()
        );

        if (draft.hasSelectedImage()) {
            context.graphics().blit(
                    draft.getSelectedImageTexture(),
                    x + 8,
                    y + 8,
                    IMAGE_BOX_SIZE - 16,
                    IMAGE_BOX_SIZE - 26,
                    0.0F,
                    0.0F,
                    draft.getSelectedImageWidth(),
                    draft.getSelectedImageHeight(),
                    draft.getSelectedImageWidth(),
                    draft.getSelectedImageHeight()
            );

            drawCenteredString(
                    context,
                    font,
                    truncateText(draft.getSelectedImageDisplayName(), 14),
                    x + IMAGE_BOX_SIZE / 2,
                    y + IMAGE_BOX_SIZE - 14,
                    TEXT_COLOR
            );

            return;
        }

        drawCenteredString(
                context,
                font,
                "[ image ]",
                x + IMAGE_BOX_SIZE / 2,
                y + 24,
                MUTED_TEXT_COLOR
        );

        drawCenteredString(
                context,
                font,
                "Choose",
                x + IMAGE_BOX_SIZE / 2,
                y + 56,
                TEXT_COLOR
        );

        drawCenteredString(
                context,
                font,
                "Image",
                x + IMAGE_BOX_SIZE / 2,
                y + 68,
                TEXT_COLOR
        );
    }

    private void renderInputField(
            VRenderContext context,
            Font font,
            String label,
            String value,
            int x,
            int y,
            Field field
    ) {
        context.graphics().drawString(
                font,
                label,
                x,
                y + 7,
                TEXT_COLOR,
                false
        );

        int inputX = x + 56;

        context.graphics().fill(
                inputX,
                y,
                inputX + INPUT_WIDTH,
                y + INPUT_HEIGHT,
                INPUT_BACKGROUND
        );

        drawBorder(
                context,
                inputX,
                y,
                INPUT_WIDTH,
                INPUT_HEIGHT,
                activeField == field
                        ? EditorHudTheme.opaqueSelection() : EditorHudTheme.outline()
        );

        String visibleValue = value;

        if (activeField == field) {
            visibleValue += "_";
        }

        context.graphics().drawString(
                font,
                truncateText(visibleValue, 25),
                inputX + 6,
                y + 7,
                TEXT_COLOR,
                false
        );
    }

    private String playerFieldText(TokenCreationDraft draft, List<VttPlayerOption> playerOptions) {
        if (activeField == Field.PLAYER) return playerSearch;
        if (draft.getPlayer() == null || draft.getPlayer().isBlank()) return "Unassigned";
        for (VttPlayerOption option : safePlayerOptions(playerOptions)) {
            if (draft.getPlayer().equals(option.id())) return option.displayName();
        }
        return "Offline: " + truncateText(draft.getPlayer(), 12);
    }

    private void renderPlayerDropdown(
            VRenderContext context, Font font, TokenCreationDraft draft,
            List<VttPlayerOption> playerOptions
    ) {
        int x = getDialogX(context.screenWidth()) + 201;
        int y = getDialogY(context.screenHeight()) + 110;
        List<PlayerChoice> choices = filteredPlayerChoices(playerOptions);
        int visibleCount = Math.min(MAX_VISIBLE_PLAYERS, choices.size());
        for (int index = 0; index < visibleCount; index++) {
            PlayerChoice choice = choices.get(index);
            int rowY = y + index * PLAYER_ROW_HEIGHT;
            boolean selected = choice.playerId().equals(draft.getPlayer());
            context.graphics().fill(x, rowY, x + INPUT_WIDTH, rowY + PLAYER_ROW_HEIGHT,
                    selected ? 0xEE332255 : 0xEE080808);
            drawBorder(context, x, rowY, INPUT_WIDTH, PLAYER_ROW_HEIGHT,
                    selected
                            ? EditorHudTheme.opaqueSelection()
                            : EditorHudTheme.outline());
            context.graphics().drawString(font, truncateText(choice.label(), 28),
                    x + 5, rowY + 5, TEXT_COLOR, false);
        }
        if (choices.isEmpty()) {
            context.graphics().fill(x, y, x + INPUT_WIDTH, y + PLAYER_ROW_HEIGHT, 0xEE080808);
            context.graphics().drawString(font, "No connected players", x + 5, y + 5,
                    MUTED_TEXT_COLOR, false);
        }
    }

    private PlayerChoice findPlayerChoiceAt(
            int screenWidth, int screenHeight, double mouseX, double mouseY,
            List<VttPlayerOption> playerOptions
    ) {
        int x = getDialogX(screenWidth) + 201;
        int y = getDialogY(screenHeight) + 110;
        List<PlayerChoice> choices = filteredPlayerChoices(playerOptions);
        int visibleCount = Math.min(MAX_VISIBLE_PLAYERS, choices.size());
        for (int index = 0; index < visibleCount; index++) {
            if (isPointInside(mouseX, mouseY, x, y + index * PLAYER_ROW_HEIGHT,
                    INPUT_WIDTH, PLAYER_ROW_HEIGHT)) return choices.get(index);
        }
        return null;
    }

    private List<PlayerChoice> filteredPlayerChoices(List<VttPlayerOption> playerOptions) {
        List<PlayerChoice> choices = new java.util.ArrayList<>();
        String query = playerSearch == null ? "" : playerSearch.trim().toLowerCase(java.util.Locale.ROOT);
        if (query.isEmpty() || "unassigned".contains(query)) choices.add(new PlayerChoice("", "Unassigned"));
        for (VttPlayerOption option : safePlayerOptions(playerOptions)) {
            if (query.isEmpty()
                    || option.displayName().toLowerCase(java.util.Locale.ROOT).contains(query)
                    || option.id().toLowerCase(java.util.Locale.ROOT).contains(query)) {
                choices.add(new PlayerChoice(option.id(), option.displayName()));
            }
        }
        return choices;
    }

    private List<VttPlayerOption> safePlayerOptions(List<VttPlayerOption> playerOptions) {
        return playerOptions == null ? List.of() : playerOptions;
    }

    private void renderStatesPanel(
            VRenderContext context,
            Font font,
            TokenCreationDraft draft,
            int x,
            int y
    ) {
        clampStateListScrollOffset(draft);

        context.graphics().fill(
                x,
                y,
                x + STATE_PANEL_WIDTH,
                y + STATE_PANEL_HEIGHT,
                0xAA050505
        );

        drawBorder(
                context,
                x,
                y,
                STATE_PANEL_WIDTH,
                STATE_PANEL_HEIGHT,
                EditorHudTheme.outline()
        );

        context.graphics().drawString(
                font,
                "States",
                x + 8,
                y + 7,
                TITLE_COLOR,
                false
        );

        int addButtonX = x + STATE_PANEL_WIDTH - STATE_ADD_BUTTON_SIZE - 7;
        int addButtonY = y + 5;

        renderSmallButton(
                context,
                font,
                "+",
                addButtonX,
                addButtonY,
                STATE_ADD_BUTTON_SIZE,
                STATE_ADD_BUTTON_SIZE,
                isMouseOverAddStateButton(
                        context.screenWidth(),
                        context.screenHeight(),
                        context.mouseX(),
                        context.mouseY()
                )
        );

        List<TokenStateDraft> states = draft.getStates();

        int rowX = x + 8;
        int rowY = y + 25;
        int rowWidth = STATE_PANEL_WIDTH - 28;

        int startIndex = stateListScrollOffset;
        int endIndex = Math.min(states.size(), startIndex + MAX_VISIBLE_STATES);

        for (int i = startIndex; i < endIndex; i++) {
            TokenStateDraft state = states.get(i);

            int visibleIndex = i - startIndex;

            renderStateRow(
                    context,
                    font,
                    draft,
                    state,
                    rowX,
                    rowY + visibleIndex * STATE_ROW_HEIGHT,
                    rowWidth
            );
        }

        if (states.size() > MAX_VISIBLE_STATES) {
            renderStateScrollIndicator(
                    context,
                    font,
                    x,
                    y,
                    states.size()
            );
        }

        TokenStateDraft selectedState = draft.getSelectedState();

        if (selectedState != null) {
            renderSelectedStateNameField(
                    context,
                    font,
                    selectedState,
                    x + 8,
                    y + STATE_PANEL_HEIGHT - 28,
                    STATE_PANEL_WIDTH - 74
            );
        }
    }

    private void renderStateScrollIndicator(
            VRenderContext context,
            Font font,
            int panelX,
            int panelY,
            int stateCount
    ) {
        String text = (stateListScrollOffset + 1)
                + "-"
                + Math.min(stateListScrollOffset + MAX_VISIBLE_STATES, stateCount)
                + " / "
                + stateCount;

        context.graphics().drawString(
                font,
                text,
                panelX + STATE_PANEL_WIDTH - 58,
                panelY + STATE_PANEL_HEIGHT - 12,
                MUTED_TEXT_COLOR,
                false
        );

        int barX = panelX + STATE_PANEL_WIDTH - 12;
        int barY = panelY + 25;
        int barHeight = STATE_ROW_HEIGHT * MAX_VISIBLE_STATES;

        EditorScrollbar.render(context, barX, barY, 3, barHeight,
                stateCount, MAX_VISIBLE_STATES, stateListScrollOffset,
                EditorHudTheme.outline());
    }

    private void renderStateRow(
            VRenderContext context,
            Font font,
            TokenCreationDraft draft,
            TokenStateDraft state,
            int x,
            int y,
            int width
    ) {
        boolean selected = draft.isStateSelected(state.getId());
        boolean hovered = isMouseOverStateRow(
                context.screenWidth(),
                context.screenHeight(),
                context.mouseX(),
                context.mouseY(),
                draft,
                state
        );

        if (selected || hovered) {
            context.graphics().fill(
                    x - 2,
                    y - 1,
                    x + width,
                    y + STATE_ROW_HEIGHT - 1,
                    selected
                            ? EditorHudTheme.selectionWithAlpha(0x55)
                            : HOVERED_STATE_BACKGROUND
            );
        }

        String imageInfo = state.hasImage()
                ? " - " + truncateText(state.getImageDisplayName(), 10)
                : " - no image";

        context.graphics().drawString(
                font,
                truncateText("[" + state.getId() + "] " + state.getDisplayName() + imageInfo, 27),
                x,
                y + 3,
                selected ? TITLE_COLOR : TEXT_COLOR,
                false
        );

        int deleteButtonX = x + width - STATE_DELETE_BUTTON_SIZE - 2;
        int deleteButtonY = y + 1;

        int duplicateButtonX = deleteButtonX - STATE_DUPLICATE_BUTTON_SIZE - STATE_ROW_BUTTON_GAP;
        int duplicateButtonY = y + 1;

        int defaultButtonX = duplicateButtonX - STATE_DEFAULT_BUTTON_SIZE - STATE_ROW_BUTTON_GAP;
        int defaultButtonY = y + 1;

        renderSmallButton(
                context,
                font,
                draft.isDefaultState(state.getId()) ? "*" : "o",
                defaultButtonX,
                defaultButtonY,
                STATE_DEFAULT_BUTTON_SIZE,
                STATE_DEFAULT_BUTTON_SIZE,
                isMouseOverStateDefaultButton(
                        context.screenWidth(),
                        context.screenHeight(),
                        context.mouseX(),
                        context.mouseY(),
                        draft,
                        state
                )
        );

        renderSmallButton(
                context,
                font,
                "D",
                duplicateButtonX,
                duplicateButtonY,
                STATE_DUPLICATE_BUTTON_SIZE,
                STATE_DUPLICATE_BUTTON_SIZE,
                isMouseOverStateDuplicateButton(
                        context.screenWidth(),
                        context.screenHeight(),
                        context.mouseX(),
                        context.mouseY(),
                        draft,
                        state
                )
        );

        renderSmallButton(
                context,
                font,
                "x",
                deleteButtonX,
                deleteButtonY,
                STATE_DELETE_BUTTON_SIZE,
                STATE_DELETE_BUTTON_SIZE,
                isMouseOverStateDeleteButton(
                        context.screenWidth(),
                        context.screenHeight(),
                        context.mouseX(),
                        context.mouseY(),
                        draft,
                        state
                )
        );
    }

    private TokenStateDraft findStateDefaultButtonAt(
            int screenWidth,
            int screenHeight,
            TokenCreationDraft draft,
            double mouseX,
            double mouseY
    ) {
        clampStateListScrollOffset(draft);

        for (TokenStateDraft state : draft.getStates()) {
            if (isMouseOverStateDefaultButton(
                    screenWidth,
                    screenHeight,
                    mouseX,
                    mouseY,
                    draft,
                    state
            )) {
                return state;
            }
        }

        return null;
    }

    private boolean isMouseOverStateDefaultButton(
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY,
            TokenCreationDraft draft,
            TokenStateDraft state
    ) {
        int x = getDialogX(screenWidth) + STATE_PANEL_X_OFFSET;
        int y = getDialogY(screenHeight) + STATE_PANEL_Y_OFFSET;

        int rowX = x + 8;
        int rowY = y + 25;
        int rowWidth = STATE_PANEL_WIDTH - 28;

        List<TokenStateDraft> states = draft.getStates();

        int startIndex = stateListScrollOffset;
        int endIndex = Math.min(states.size(), startIndex + MAX_VISIBLE_STATES);

        for (int i = startIndex; i < endIndex; i++) {
            TokenStateDraft currentState = states.get(i);

            if (!currentState.getId().equals(state.getId())) {
                continue;
            }

            int visibleIndex = i - startIndex;
            int currentY = rowY + visibleIndex * STATE_ROW_HEIGHT;

            int deleteButtonX = rowX + rowWidth - STATE_DELETE_BUTTON_SIZE - 2;
            int duplicateButtonX = deleteButtonX - STATE_DUPLICATE_BUTTON_SIZE - STATE_ROW_BUTTON_GAP;
            int defaultButtonX = duplicateButtonX - STATE_DEFAULT_BUTTON_SIZE - STATE_ROW_BUTTON_GAP;
            int defaultButtonY = currentY + 1;

            return isPointInside(
                    mouseX,
                    mouseY,
                    defaultButtonX,
                    defaultButtonY,
                    STATE_DEFAULT_BUTTON_SIZE,
                    STATE_DEFAULT_BUTTON_SIZE
            );
        }

        return false;
    }

    private boolean isMouseOverStateDuplicateButton(
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY,
            TokenCreationDraft draft,
            TokenStateDraft state
    ) {
        int x = getDialogX(screenWidth) + STATE_PANEL_X_OFFSET;
        int y = getDialogY(screenHeight) + STATE_PANEL_Y_OFFSET;

        int rowX = x + 8;
        int rowY = y + 25;
        int rowWidth = STATE_PANEL_WIDTH - 28;

        List<TokenStateDraft> states = draft.getStates();

        int startIndex = stateListScrollOffset;
        int endIndex = Math.min(states.size(), startIndex + MAX_VISIBLE_STATES);

        for (int i = startIndex; i < endIndex; i++) {
            TokenStateDraft currentState = states.get(i);

            if (!currentState.getId().equals(state.getId())) {
                continue;
            }

            int visibleIndex = i - startIndex;
            int currentY = rowY + visibleIndex * STATE_ROW_HEIGHT;

            int deleteButtonX = rowX + rowWidth - STATE_DELETE_BUTTON_SIZE - 2;
            int duplicateButtonX = deleteButtonX - STATE_DUPLICATE_BUTTON_SIZE - STATE_ROW_BUTTON_GAP;
            int duplicateButtonY = currentY + 1;

            return isPointInside(
                    mouseX,
                    mouseY,
                    duplicateButtonX,
                    duplicateButtonY,
                    STATE_DUPLICATE_BUTTON_SIZE,
                    STATE_DUPLICATE_BUTTON_SIZE
            );
        }

        return false;
    }

    private TokenStateDraft findStateDuplicateButtonAt(
            int screenWidth,
            int screenHeight,
            TokenCreationDraft draft,
            double mouseX,
            double mouseY
    ) {
        clampStateListScrollOffset(draft);

        for (TokenStateDraft state : draft.getStates()) {
            if (isMouseOverStateDuplicateButton(
                    screenWidth,
                    screenHeight,
                    mouseX,
                    mouseY,
                    draft,
                    state
            )) {
                return state;
            }
        }

        return null;
    }

    private boolean isMouseOverStateDeleteButton(
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY,
            TokenCreationDraft draft,
            TokenStateDraft state
    ) {
        int x = getDialogX(screenWidth) + STATE_PANEL_X_OFFSET;
        int y = getDialogY(screenHeight) + STATE_PANEL_Y_OFFSET;

        int rowX = x + 8;
        int rowY = y + 25;
        int rowWidth = STATE_PANEL_WIDTH - 28;

        List<TokenStateDraft> states = draft.getStates();

        int startIndex = stateListScrollOffset;
        int endIndex = Math.min(states.size(), startIndex + MAX_VISIBLE_STATES);

        for (int i = startIndex; i < endIndex; i++) {
            TokenStateDraft currentState = states.get(i);

            if (!currentState.getId().equals(state.getId())) {
                continue;
            }

            int visibleIndex = i - startIndex;
            int currentY = rowY + visibleIndex * STATE_ROW_HEIGHT;

            int buttonX = rowX + rowWidth - STATE_DELETE_BUTTON_SIZE - 2;
            int buttonY = currentY + 1;

            return isPointInside(
                    mouseX,
                    mouseY,
                    buttonX,
                    buttonY,
                    STATE_DELETE_BUTTON_SIZE,
                    STATE_DELETE_BUTTON_SIZE
            );
        }

        return false;
    }

    private TokenStateDraft findStateDeleteButtonAt(
            int screenWidth,
            int screenHeight,
            TokenCreationDraft draft,
            double mouseX,
            double mouseY
    ) {
        clampStateListScrollOffset(draft);

        for (TokenStateDraft state : draft.getStates()) {
            if (isMouseOverStateDeleteButton(
                    screenWidth,
                    screenHeight,
                    mouseX,
                    mouseY,
                    draft,
                    state
            )) {
                return state;
            }
        }

        return null;
    }

    private void renderSelectedStateNameField(
            VRenderContext context,
            Font font,
            TokenStateDraft selectedState,
            int x,
            int y,
            int width
    ) {
        String label = "Name:";

        context.graphics().drawString(
                font,
                label,
                x,
                y + 7,
                MUTED_TEXT_COLOR,
                false
        );

        int inputX = x + 48;
        int inputWidth = 250;

        context.graphics().fill(
                inputX,
                y,
                inputX + inputWidth,
                y + INPUT_HEIGHT,
                INPUT_BACKGROUND
        );

        drawBorder(
                context,
                inputX,
                y,
                inputWidth,
                INPUT_HEIGHT,
                activeField == Field.STATE_NAME
                        ? EditorHudTheme.opaqueSelection() : EditorHudTheme.outline()
        );

        String visibleText = activeField == Field.STATE_NAME
                ? stateNameEditBuffer + "_"
                : selectedState.getDisplayName();

        context.graphics().drawString(
                font,
                truncateText(visibleText, 28),
                inputX + 6,
                y + 7,
                TEXT_COLOR,
                false
        );
    }

    private void renderButton(
            VRenderContext context,
            Font font,
            String text,
            int x,
            int y,
            boolean hovered
    ) {
        context.graphics().fill(
                x,
                y,
                x + BUTTON_WIDTH,
                y + BUTTON_HEIGHT,
                BUTTON_BACKGROUND
        );

        drawBorder(
                context,
                x,
                y,
                BUTTON_WIDTH,
                BUTTON_HEIGHT,
                hovered ? EditorHudTheme.opaqueSelection() : EditorHudTheme.outline()
        );

        drawCenteredString(
                context,
                font,
                text,
                x + BUTTON_WIDTH / 2,
                y + 10,
                TEXT_COLOR
        );
    }

    private void renderSmallButton(
            VRenderContext context,
            Font font,
            String text,
            int x,
            int y,
            int width,
            int height,
            boolean hovered
    ) {
        context.graphics().fill(
                x,
                y,
                x + width,
                y + height,
                BUTTON_BACKGROUND
        );

        drawBorder(
                context,
                x,
                y,
                width,
                height,
                hovered ? EditorHudTheme.opaqueSelection() : EditorHudTheme.outline()
        );

        drawCenteredString(
                context,
                font,
                text,
                x + width / 2 +1,
                y + height / 2 - 4,
                TEXT_COLOR
        );
    }

    private Field findFieldAt(
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        int x = getDialogX(screenWidth);
        int y = getDialogY(screenHeight);

        if (isPointInside(mouseX, mouseY, x + 201, y + 56, INPUT_WIDTH, INPUT_HEIGHT)) {
            return Field.NAME;
        }

        if (isPointInside(mouseX, mouseY, x + 201, y + 88, INPUT_WIDTH, INPUT_HEIGHT)) {
            return Field.PLAYER;
        }

        if (isPointInside(mouseX, mouseY, x + 201, y + 120, INPUT_WIDTH, INPUT_HEIGHT)) {
            return Field.NOTES;
        }

        return Field.NONE;
    }

    private TokenStateDraft findStateAt(
            int screenWidth,
            int screenHeight,
            TokenCreationDraft draft,
            double mouseX,
            double mouseY
    ) {
        clampStateListScrollOffset(draft);

        int x = getDialogX(screenWidth) + STATE_PANEL_X_OFFSET;
        int y = getDialogY(screenHeight) + STATE_PANEL_Y_OFFSET;

        int rowX = x + 8;
        int rowY = y + 25;
        int rowWidth = STATE_PANEL_WIDTH - 28;

        List<TokenStateDraft> states = draft.getStates();

        int startIndex = stateListScrollOffset;
        int endIndex = Math.min(states.size(), startIndex + MAX_VISIBLE_STATES);

        for (int i = startIndex; i < endIndex; i++) {
            TokenStateDraft state = states.get(i);

            int visibleIndex = i - startIndex;
            int currentY = rowY + visibleIndex * STATE_ROW_HEIGHT;

            if (isPointInside(mouseX, mouseY, rowX - 2, currentY - 1, rowWidth + 2, STATE_ROW_HEIGHT)) {
                return state;
            }
        }

        return null;
    }

    private boolean isMouseOverStateRow(
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY,
            TokenCreationDraft draft,
            TokenStateDraft state
    ) {
        clampStateListScrollOffset(draft);

        int x = getDialogX(screenWidth) + STATE_PANEL_X_OFFSET;
        int y = getDialogY(screenHeight) + STATE_PANEL_Y_OFFSET;

        int rowX = x + 8;
        int rowY = y + 25;
        int rowWidth = STATE_PANEL_WIDTH - 28;

        List<TokenStateDraft> states = draft.getStates();

        int startIndex = stateListScrollOffset;
        int endIndex = Math.min(states.size(), startIndex + MAX_VISIBLE_STATES);

        for (int i = startIndex; i < endIndex; i++) {
            TokenStateDraft currentState = states.get(i);

            if (!currentState.getId().equals(state.getId())) {
                continue;
            }

            int visibleIndex = i - startIndex;
            int currentY = rowY + visibleIndex * STATE_ROW_HEIGHT;

            return isPointInside(mouseX, mouseY, rowX - 2, currentY - 1, rowWidth + 2, STATE_ROW_HEIGHT);
        }

        return false;
    }

    private boolean isMouseOverAddStateButton(
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        int x = getDialogX(screenWidth) + STATE_PANEL_X_OFFSET;
        int y = getDialogY(screenHeight) + STATE_PANEL_Y_OFFSET;

        int addButtonX = x + STATE_PANEL_WIDTH - STATE_ADD_BUTTON_SIZE - 7;
        int addButtonY = y + 5;

        return isPointInside(
                mouseX,
                mouseY,
                addButtonX,
                addButtonY,
                STATE_ADD_BUTTON_SIZE,
                STATE_ADD_BUTTON_SIZE
        );
    }

    private void appendToActiveField(
            TokenCreationDraft draft,
            char character
    ) {
        if (activeField == Field.NAME && draft.getName().length() < 48) {
            draft.setName(draft.getName() + character);
            return;
        }

        if (activeField == Field.PLAYER && playerSearch.length() < 48) {
            playerSearch += character;
            return;
        }

        if (activeField == Field.NOTES && draft.getNotes().length() < 64) {
            draft.setNotes(draft.getNotes() + character);
        }
    }

    private void backspaceActiveField(TokenCreationDraft draft) {
        if (activeField == Field.NAME) {
            draft.setName(removeLastCharacter(draft.getName()));
            return;
        }

        if (activeField == Field.PLAYER) {
            playerSearch = removeLastCharacter(playerSearch);
            return;
        }

        if (activeField == Field.NOTES) {
            draft.setNotes(removeLastCharacter(draft.getNotes()));
        }
    }

    private void focusNextField() {
        activeField = switch (activeField) {
            case NONE -> Field.NAME;
            case NAME -> Field.PLAYER;
            case PLAYER -> Field.NOTES;
            case NOTES -> Field.STATE_NAME;
            case STATE_NAME -> Field.NAME;
        };
    }

    private String removeLastCharacter(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        return text.substring(0, text.length() - 1);
    }

    private boolean isAllowedTextCharacter(char character) {
        return character >= 32 && character != 127;
    }

    private boolean containsPoint(
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        int x = getDialogX(screenWidth);
        int y = getDialogY(screenHeight);

        return mouseX >= x
                && mouseX <= x + DIALOG_WIDTH
                && mouseY >= y
                && mouseY <= y + DIALOG_HEIGHT;
    }

    private boolean isMouseOverImageButton(
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        int x = getDialogX(screenWidth) + 26;
        int y = getDialogY(screenHeight) + 52;

        return isPointInside(mouseX, mouseY, x, y, IMAGE_BOX_SIZE, IMAGE_BOX_SIZE);
    }

    private boolean isMouseOverCreateButton(VRenderContext context) {
        return isPointInside(
                context.mouseX(),
                context.mouseY(),
                getCreateButtonX(context),
                getCreateButtonY(context),
                BUTTON_WIDTH,
                BUTTON_HEIGHT
        );
    }

    private boolean isMouseOverDiscardButton(VRenderContext context) {
        return isPointInside(
                context.mouseX(),
                context.mouseY(),
                getDiscardButtonX(context),
                getDiscardButtonY(context),
                BUTTON_WIDTH,
                BUTTON_HEIGHT
        );
    }

    private int getCreateButtonX(VRenderContext context) {
        return getCreateButtonX(context.screenWidth());
    }

    private int getCreateButtonY(VRenderContext context) {
        return getCreateButtonY(context.screenHeight());
    }

    private int getDiscardButtonX(VRenderContext context) {
        return getDiscardButtonX(context.screenWidth());
    }

    private int getDiscardButtonY(VRenderContext context) {
        return getDiscardButtonY(context.screenHeight());
    }

    private int getCreateButtonX(int screenWidth) {
        return getDialogX(screenWidth) + 28;
    }

    private int getCreateButtonY(int screenHeight) {
        return getDialogY(screenHeight) + DIALOG_HEIGHT - 46;
    }

    private int getDiscardButtonX(int screenWidth) {
        return getDialogX(screenWidth) + DIALOG_WIDTH - BUTTON_WIDTH - 28;
    }

    private int getDiscardButtonY(int screenHeight) {
        return getDialogY(screenHeight) + DIALOG_HEIGHT - 46;
    }

    private boolean isPointInside(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int width,
            int height
    ) {
        return mouseX >= x
                && mouseX <= x + width
                && mouseY >= y
                && mouseY <= y + height;
    }

    private int getDialogX(VRenderContext context) {
        return getDialogX(context.screenWidth());
    }

    private int getDialogY(VRenderContext context) {
        return getDialogY(context.screenHeight());
    }

    private int getDialogX(int screenWidth) {
        return screenWidth / 2 - DIALOG_WIDTH / 2;
    }

    private int getDialogY(int screenHeight) {
        return screenHeight / 2 - DIALOG_HEIGHT / 2;
    }

    private void drawBorder(
            VRenderContext context,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        context.graphics().hLine(x, x + width, y, color);
        context.graphics().hLine(x, x + width, y + height, color);
        context.graphics().vLine(x, y, y + height, color);
        context.graphics().vLine(x + width, y, y + height, color);
    }

    private void drawCenteredString(
            VRenderContext context,
            Font font,
            String text,
            int centerX,
            int y,
            int color
    ) {
        context.graphics().drawString(
                font,
                text,
                centerX - font.width(text) / 2,
                y,
                color,
                false
        );
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return "";
        }

        if (text.length() <= maxLength) {
            return text;
        }

        if (maxLength <= 3) {
            return text.substring(0, maxLength);
        }

        return text.substring(0, maxLength - 3) + "...";
    }

    private record PlayerChoice(String playerId, String label) {}
}
