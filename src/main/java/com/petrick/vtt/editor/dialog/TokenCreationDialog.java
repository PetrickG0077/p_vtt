package com.petrick.vtt.editor.dialog;

import com.petrick.vtt.editor.token.TokenCreationDraft;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;

/**
 * Janela visual para criação de tokens.
 *
 * Por enquanto é um esqueleto:
 * - permite digitar Name, Player e Notes;
 * - tem botão Choose Image;
 * - tem botão Create;
 * - tem botão Discard.
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
        NOTES
    }

    private static final int DIALOG_WIDTH = 620;

    private static final int DIALOG_HEIGHT = 420;

    private static final int PANEL_BACKGROUND = 0xEE000000;

    private static final int PANEL_BORDER = 0xFF8844DD;

    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private static final int MUTED_TEXT_COLOR = 0xFFAAAAAA;

    private static final int INPUT_BACKGROUND = 0xAA050505;

    private static final int INPUT_BORDER = 0xFF8844DD;

    private static final int INPUT_ACTIVE_BORDER = 0xFFFFAA33;

    private static final int BUTTON_BACKGROUND = 0x66000000;

    private static final int BUTTON_BORDER = 0xFF8844DD;

    private static final int BUTTON_HOVER_BORDER = 0xFFFFAA33;

    private static final int IMAGE_BOX_SIZE = 145;

    private static final int INPUT_WIDTH = 292;

    private static final int INPUT_HEIGHT = 32;

    private static final int BUTTON_WIDTH = 188;

    private static final int BUTTON_HEIGHT = 42;

    private Field activeField = Field.NONE;

    public void render(
            VRenderContext context,
            Font font,
            TokenCreationDraft draft
    ) {
        int x = getDialogX(context);
        int y = getDialogY(context);

        renderDimBackground(context);
        renderPanel(context, x, y);

        drawCenteredString(
                context,
                font,
                "CREATE TOKEN",
                x + DIALOG_WIDTH / 2,
                y + 18,
                TITLE_COLOR
        );

        renderImageButton(context, font, draft, x + 36, y + 84);

        renderInputField(
                context,
                font,
                "Name:",
                draft.getName(),
                x + 206,
                y + 100,
                Field.NAME
        );

        renderInputField(
                context,
                font,
                "Player:",
                draft.getPlayer(),
                x + 206,
                y + 146,
                Field.PLAYER
        );

        renderInputField(
                context,
                font,
                "Notes:",
                draft.getNotes(),
                x + 206,
                y + 192,
                Field.NOTES
        );

        drawCenteredString(
                context,
                font,
                "INFORMAÇÕES",
                x + DIALOG_WIDTH / 2,
                y + 276,
                0xFFFFFFFF
        );

        drawCenteredString(
                context,
                font,
                "FUTURAS",
                x + DIALOG_WIDTH / 2,
                y + 322,
                0xFFFFFFFF
        );

        renderButton(
                context,
                font,
                "Create",
                getCreateButtonX(context),
                getCreateButtonY(context),
                isMouseOverCreateButton(context)
        );

        renderButton(
                context,
                font,
                "Discard",
                getDiscardButtonX(context),
                getDiscardButtonY(context),
                isMouseOverDiscardButton(context)
        );
    }

    public Action mouseClicked(
            VRenderContext context,
            TokenCreationDraft draft,
            double mouseX,
            double mouseY,
            int button
    ) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return Action.NONE;
        }

        if (!containsPoint(context, mouseX, mouseY)) {
            return Action.NONE;
        }

        if (isMouseOverImageButton(context, mouseX, mouseY)) {
            activeField = Field.NONE;
            return Action.CHOOSE_IMAGE;
        }

        if (isMouseOverCreateButton(context, mouseX, mouseY)) {
            activeField = Field.NONE;
            return Action.CREATE;
        }

        if (isMouseOverDiscardButton(context, mouseX, mouseY)) {
            activeField = Field.NONE;
            return Action.DISCARD;
        }

        Field clickedField = findFieldAt(context, mouseX, mouseY);

        activeField = clickedField;

        return Action.NONE;
    }

    public boolean keyPressed(
            TokenCreationDraft draft,
            int keyCode
    ) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            activeField = Field.NONE;
            return true;
        }

        if (activeField == Field.NONE) {
            return false;
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

        if (!isAllowedTextCharacter(codePoint)) {
            return true;
        }

        appendToActiveField(draft, codePoint);

        return true;
    }

    public boolean containsPoint(
            VRenderContext context,
            double mouseX,
            double mouseY
    ) {
        int x = getDialogX(context);
        int y = getDialogY(context);

        return mouseX >= x
                && mouseX <= x + DIALOG_WIDTH
                && mouseY >= y
                && mouseY <= y + DIALOG_HEIGHT;
    }

    public Action mouseClicked(
            int screenWidth,
            int screenHeight,
            TokenCreationDraft draft,
            double mouseX,
            double mouseY,
            int button
    ) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return Action.NONE;
        }

        if (!containsPoint(screenWidth, screenHeight, mouseX, mouseY)) {
            return Action.NONE;
        }

        if (isMouseOverImageButton(screenWidth, screenHeight, mouseX, mouseY)) {
            activeField = Field.NONE;
            return Action.CHOOSE_IMAGE;
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

        return Action.NONE;
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
        int x = getDialogX(screenWidth) + 36;
        int y = getDialogY(screenHeight) + 84;

        return isPointInside(mouseX, mouseY, x, y, IMAGE_BOX_SIZE, IMAGE_BOX_SIZE);
    }

    private Field findFieldAt(
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        int x = getDialogX(screenWidth);
        int y = getDialogY(screenHeight);

        if (isPointInside(mouseX, mouseY, x + 302, y + 100, INPUT_WIDTH, INPUT_HEIGHT)) {
            return Field.NAME;
        }

        if (isPointInside(mouseX, mouseY, x + 302, y + 146, INPUT_WIDTH, INPUT_HEIGHT)) {
            return Field.PLAYER;
        }

        if (isPointInside(mouseX, mouseY, x + 302, y + 192, INPUT_WIDTH, INPUT_HEIGHT)) {
            return Field.NOTES;
        }

        return Field.NONE;
    }

    private int getDialogX(int screenWidth) {
        return screenWidth / 2 - DIALOG_WIDTH / 2;
    }

    private int getDialogY(int screenHeight) {
        return screenHeight / 2 - DIALOG_HEIGHT / 2;
    }

    private int getCreateButtonX(int screenWidth) {
        return getDialogX(screenWidth) + 32;
    }

    private int getCreateButtonY(int screenHeight) {
        return getDialogY(screenHeight) + DIALOG_HEIGHT - 58;
    }

    private int getDiscardButtonX(int screenWidth) {
        return getDialogX(screenWidth) + DIALOG_WIDTH - BUTTON_WIDTH - 32;
    }

    private int getDiscardButtonY(int screenHeight) {
        return getDialogY(screenHeight) + DIALOG_HEIGHT - 58;
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
                PANEL_BORDER
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
                context,
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
                hovered ? BUTTON_HOVER_BORDER : BUTTON_BORDER
        );

        drawCenteredString(
                context,
                font,
                "[ image ]",
                x + IMAGE_BOX_SIZE / 2,
                y + 46,
                MUTED_TEXT_COLOR
        );

        drawCenteredString(
                context,
                font,
                "Choose Image",
                x + IMAGE_BOX_SIZE / 2,
                y + 96,
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
                y + 8,
                TEXT_COLOR,
                false
        );

        int inputX = x + 96;

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
                activeField == field ? INPUT_ACTIVE_BORDER : INPUT_BORDER
        );

        String visibleValue = value;

        if (activeField == field) {
            visibleValue += "_";
        }

        context.graphics().drawString(
                font,
                truncateText(visibleValue, 34),
                inputX + 8,
                y + 10,
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
                hovered ? BUTTON_HOVER_BORDER : BUTTON_BORDER
        );

        drawCenteredString(
                context,
                font,
                text,
                x + BUTTON_WIDTH / 2,
                y + 15,
                TEXT_COLOR
        );
    }

    private Field findFieldAt(
            VRenderContext context,
            double mouseX,
            double mouseY
    ) {
        int x = getDialogX(context);
        int y = getDialogY(context);

        if (isPointInside(mouseX, mouseY, x + 302, y + 100, INPUT_WIDTH, INPUT_HEIGHT)) {
            return Field.NAME;
        }

        if (isPointInside(mouseX, mouseY, x + 302, y + 146, INPUT_WIDTH, INPUT_HEIGHT)) {
            return Field.PLAYER;
        }

        if (isPointInside(mouseX, mouseY, x + 302, y + 192, INPUT_WIDTH, INPUT_HEIGHT)) {
            return Field.NOTES;
        }

        return Field.NONE;
    }

    private void appendToActiveField(
            TokenCreationDraft draft,
            char character
    ) {
        if (activeField == Field.NAME && draft.getName().length() < 48) {
            draft.setName(draft.getName() + character);
            return;
        }

        if (activeField == Field.PLAYER && draft.getPlayer().length() < 48) {
            draft.setPlayer(draft.getPlayer() + character);
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
            draft.setPlayer(removeLastCharacter(draft.getPlayer()));
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
            case NOTES -> Field.NAME;
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

    private boolean isMouseOverImageButton(
            VRenderContext context,
            double mouseX,
            double mouseY
    ) {
        int x = getDialogX(context) + 36;
        int y = getDialogY(context) + 84;

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

    private boolean isMouseOverCreateButton(
            VRenderContext context,
            double mouseX,
            double mouseY
    ) {
        return isPointInside(
                mouseX,
                mouseY,
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

    private boolean isMouseOverDiscardButton(
            VRenderContext context,
            double mouseX,
            double mouseY
    ) {
        return isPointInside(
                mouseX,
                mouseY,
                getDiscardButtonX(context),
                getDiscardButtonY(context),
                BUTTON_WIDTH,
                BUTTON_HEIGHT
        );
    }

    private int getCreateButtonX(VRenderContext context) {
        return getDialogX(context) + 32;
    }

    private int getCreateButtonY(VRenderContext context) {
        return getDialogY(context) + DIALOG_HEIGHT - 58;
    }

    private int getDiscardButtonX(VRenderContext context) {
        return getDialogX(context) + DIALOG_WIDTH - BUTTON_WIDTH - 32;
    }

    private int getDiscardButtonY(VRenderContext context) {
        return getDialogY(context) + DIALOG_HEIGHT - 58;
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
        return context.screenWidth() / 2 - DIALOG_WIDTH / 2;
    }

    private int getDialogY(VRenderContext context) {
        return context.screenHeight() / 2 - DIALOG_HEIGHT / 2;
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
}