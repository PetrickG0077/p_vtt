package com.petrick.vtt.editor.dialog;

import com.petrick.vtt.editor.hud.EditorHudTheme;
import com.petrick.vtt.feature.attachment.AttachmentDefinition;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Small create/edit dialog for image-optional attachment definitions. */
public final class AttachmentDefinitionDialog {
    private String editingId;
    private String name = "";
    private String width = "32";
    private String height = "32";
    private String assetId;
    private String assetName;
    private ResourceLocation preview;
    private int previewWidth;
    private int previewHeight;
    private Field focused = Field.NAME;
    private boolean open;

    public void openNew() {
        editingId = null;
        name = "";
        width = "32";
        height = "32";
        clearImage();
        focused = Field.NAME;
        open = true;
    }

    public void openEdit(AttachmentDefinition definition, ResourceLocation texture,
                         int textureWidth, int textureHeight) {
        editingId = definition.id();
        name = definition.displayName();
        width = format(definition.defaultWidth());
        height = format(definition.defaultHeight());
        assetId = definition.assetId();
        assetName = definition.assetId();
        preview = texture;
        previewWidth = textureWidth;
        previewHeight = textureHeight;
        focused = Field.NAME;
        open = true;
    }

    public void setImage(String id, String displayName, ResourceLocation texture,
                         int textureWidth, int textureHeight) {
        assetId = id;
        assetName = displayName;
        preview = texture;
        previewWidth = textureWidth;
        previewHeight = textureHeight;
    }

    public void clearImage() {
        assetId = null;
        assetName = null;
        preview = null;
        previewWidth = 0;
        previewHeight = 0;
    }

    public void close() { open = false; }
    public boolean isOpen() { return open; }
    public String editingId() { return editingId; }
    public String name() { return name.trim(); }
    public String assetId() { return assetId; }

    public double parsedWidth() { return parseSize(width); }
    public double parsedHeight() { return parseSize(height); }
    public boolean valid() {
        double parsedWidth = parsedWidth();
        double parsedHeight = parsedHeight();
        return !name().isBlank() && parsedWidth > 0 && parsedWidth <= 16_000
                && parsedHeight > 0 && parsedHeight <= 16_000;
    }

    public void render(VRenderContext context, Font font, String targetFolder) {
        int dialogWidth = 390;
        int dialogHeight = 210;
        int x = (context.screenWidth() - dialogWidth) / 2;
        int y = (context.screenHeight() - dialogHeight) / 2;
        context.graphics().fill(0, 0, context.screenWidth(), context.screenHeight(), 0x99000000);
        context.graphics().fill(x, y, x + dialogWidth, y + dialogHeight, 0xF20D0D12);
        border(context, x, y, dialogWidth, dialogHeight, EditorHudTheme.outline());
        context.graphics().drawCenteredString(font,
                editingId == null ? "CREATE ATTACHMENT" : "EDIT ATTACHMENT",
                x + dialogWidth / 2, y + 10, 0xFFFFFFFF);
        if (editingId == null && targetFolder != null && !targetFolder.isBlank()) {
            context.graphics().drawString(font, "Create in: Assets/" + trim(targetFolder, 27),
                    x + 150, y + 26, 0xFFAAAAAA, false);
        }

        int previewX = x + 16;
        int previewY = y + 35;
        int previewSize = 82;
        context.graphics().fill(previewX, previewY, previewX + previewSize,
                previewY + previewSize, preview == null ? 0xFF2374C6 : 0xFF15151A);
        if (preview != null) {
            double scale = Math.min((previewSize - 4) / (double) previewWidth,
                    (previewSize - 4) / (double) previewHeight);
            int drawWidth = Math.max(1, (int) Math.round(previewWidth * scale));
            int drawHeight = Math.max(1, (int) Math.round(previewHeight * scale));
            context.graphics().blit(preview,
                    previewX + (previewSize - drawWidth) / 2,
                    previewY + (previewSize - drawHeight) / 2,
                    drawWidth, drawHeight, 0, 0,
                    previewWidth, previewHeight, previewWidth, previewHeight);
        } else {
            context.graphics().drawCenteredString(font, "No image",
                    previewX + previewSize / 2, previewY + 36, 0xFFFFFFFF);
        }
        border(context, previewX, previewY, previewSize, previewSize, 0xFF77777D);
        button(context, font, x + 16, y + 126, 82, 20, "Choose Image", true);
        button(context, font, x + 16, y + 151, 82, 20, "Remove", assetId != null);

        label(context, font, "Name:", x + 116, y + 47);
        field(context, font, x + 170, y + 39, 200, 22, name, Field.NAME);
        label(context, font, "Width:", x + 116, y + 80);
        field(context, font, x + 170, y + 72, 82, 22, width, Field.WIDTH);
        label(context, font, "Height:", x + 258, y + 80);
        field(context, font, x + 310, y + 72, 60, 22, height, Field.HEIGHT);
        context.graphics().drawString(font,
                assetName == null ? "Image: optional" : "Image: " + trim(assetName, 30),
                x + 116, y + 108, assetName == null ? 0xFFAAAAAA : 0xFFFFFFFF, false);
        context.graphics().drawString(font,
                "Without an image it uses a master-only blue marker.",
                x + 116, y + 124, 0xFF88888E, false);
        button(context, font, x + 116, y + 169, 105, 24,
                editingId == null ? "Create" : "Save", valid());
        button(context, font, x + 265, y + 169, 105, 24, "Cancel", true);
    }

    public Action mouseClicked(double mouseX, double mouseY, int button,
                               int screenWidth, int screenHeight) {
        if (!open || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return Action.NONE;
        int x = (screenWidth - 390) / 2;
        int y = (screenHeight - 210) / 2;
        if (inside(mouseX, mouseY, x + 16, y + 126, 82, 20)) return Action.CHOOSE_IMAGE;
        if (inside(mouseX, mouseY, x + 16, y + 151, 82, 20)) {
            if (assetId != null) clearImage();
            return Action.NONE;
        }
        if (inside(mouseX, mouseY, x + 170, y + 39, 200, 22)) focused = Field.NAME;
        else if (inside(mouseX, mouseY, x + 170, y + 72, 82, 22)) focused = Field.WIDTH;
        else if (inside(mouseX, mouseY, x + 310, y + 72, 60, 22)) focused = Field.HEIGHT;
        else if (inside(mouseX, mouseY, x + 116, y + 169, 105, 24) && valid()) return Action.SAVE;
        else if (inside(mouseX, mouseY, x + 265, y + 169, 105, 24)) return Action.CANCEL;
        return Action.NONE;
    }

    public Action keyPressed(int keyCode) {
        if (!open) return Action.NONE;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return Action.CANCEL;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            return valid() ? Action.SAVE : Action.NONE;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            switch (focused) {
                case NAME -> name = removeLast(name);
                case WIDTH -> width = removeLast(width);
                case HEIGHT -> height = removeLast(height);
            }
        }
        return Action.NONE;
    }

    public boolean charTyped(char character) {
        if (!open) return false;
        if (focused == Field.NAME) {
            if (!Character.isISOControl(character) && name.length() < 64) name += character;
            return true;
        }
        String current = focused == Field.WIDTH ? width : height;
        if ((Character.isDigit(character) || character == '.') && current.length() < 8
                && (character != '.' || !current.contains("."))) {
            if (focused == Field.WIDTH) width += character; else height += character;
        }
        return true;
    }

    private void field(VRenderContext context, Font font, int x, int y, int width,
                       int height, String value, Field field) {
        context.graphics().fill(x, y, x + width, y + height, 0xDD17171D);
        border(context, x, y, width, height,
                focused == field ? EditorHudTheme.opaqueSelection() : EditorHudTheme.outline());
        context.graphics().drawString(font, value + (focused == field ? "_" : ""),
                x + 6, y + 7, 0xFFFFFFFF, false);
    }

    private void label(VRenderContext context, Font font, String value, int x, int y) {
        context.graphics().drawString(font, value, x, y, 0xFFCCCCCC, false);
    }

    private void button(VRenderContext context, Font font, int x, int y, int width,
                        int height, String text, boolean enabled) {
        boolean hovered = enabled && inside(context.mouseX(), context.mouseY(), x, y, width, height);
        context.graphics().fill(x, y, x + width, y + height,
                enabled ? hovered ? 0xEE34343D : 0xDD18181E : 0xCC111114);
        border(context, x, y, width, height,
                enabled ? EditorHudTheme.outline() : 0xFF55555A);
        context.graphics().drawCenteredString(font, text, x + width / 2,
                y + (height - 8) / 2, enabled ? 0xFFFFFFFF : 0xFF77777D);
    }

    private void border(VRenderContext context, int x, int y, int width, int height, int color) {
        context.graphics().hLine(x, x + width, y, color);
        context.graphics().hLine(x, x + width, y + height, color);
        context.graphics().vLine(x, y, y + height, color);
        context.graphics().vLine(x + width, y, y + height, color);
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private double parseSize(String value) {
        try { return Double.parseDouble(value); }
        catch (RuntimeException ignored) { return -1; }
    }

    private String format(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value)
                : Double.toString(value);
    }

    private String removeLast(String value) {
        return value.isEmpty() ? value : value.substring(0, value.length() - 1);
    }

    private String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private enum Field { NAME, WIDTH, HEIGHT }
    public enum Action { NONE, CHOOSE_IMAGE, SAVE, CANCEL }
}
