package com.petrick.vtt.editor.hud;

import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;

/** Reusable modal HSV color picker for editor color controls. */
public final class EditorColorPickerOverlay {
    private static final int WIDTH = 246;
    private static final int HEIGHT = 158;
    private static final int SV_WIDTH = 132;
    private static final int SV_HEIGHT = 116;
    private static final int PANEL = 0xFA18181E;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int MUTED = 0xFF99999F;
    private static final int ACCENT = 0xFF55CCFF;

    private boolean open;
    private float hue;
    private float saturation;
    private float value;
    private boolean draggingSaturationValue;
    private boolean draggingHue;
    private boolean focusedHex;
    private boolean replaceHexOnType;
    private String hexBuffer = "";
    private IntConsumer acceptAction;
    private boolean accepted;

    public void open(int rgb, IntConsumer acceptAction) {
        float[] hsv = rgbToHsv(rgb);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        hexBuffer = HexColorFormat.format(rgb);
        this.acceptAction = acceptAction;
        open = true;
        accepted = false;
        focusedHex = false;
        replaceHexOnType = false;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean consumeAccepted() {
        boolean result = accepted;
        accepted = false;
        return result;
    }

    public void cancel() {
        accepted = false;
        close();
    }

    public void render(VRenderContext context, Font font) {
        if (!open) return;
        Bounds panel = bounds(context.screenWidth(), context.screenHeight());
        context.graphics().fill(0, 0, context.screenWidth(), context.screenHeight(), 0x88000000);
        context.graphics().fill(panel.x, panel.y, panel.right(), panel.bottom(), PANEL);
        border(context, panel, EditorHudTheme.outline());
        context.graphics().drawString(font, "Color Picker", panel.x + 9, panel.y + 8, TEXT, false);

        Bounds sv = saturationValueBounds(panel);
        int hueColor = hsvToRgb(hue, 1.0F, 1.0F);
        for (int column = 0; column < sv.width; column++) {
            float amount = column / (float) Math.max(1, sv.width - 1);
            int top = mixRgb(0xFFFFFF, hueColor, amount);
            context.graphics().fillGradient(
                    sv.x + column, sv.y, sv.x + column + 1, sv.bottom(),
                    0xFF000000 | top, 0xFF000000);
        }
        int selectorX = sv.x + Math.round(saturation * (sv.width - 1));
        int selectorY = sv.y + Math.round((1.0F - value) * (sv.height - 1));
        selector(context, selectorX, selectorY);

        Bounds hueBar = hueBounds(panel);
        for (int row = 0; row < hueBar.height; row++) {
            float rowHue = row / (float) Math.max(1, hueBar.height - 1);
            context.graphics().fill(hueBar.x, hueBar.y + row,
                    hueBar.right(), hueBar.y + row + 1,
                    0xFF000000 | hsvToRgb(rowHue, 1.0F, 1.0F));
        }
        border(context, hueBar, 0xFFFFFFFF);
        int hueY = hueBar.y + Math.round(hue * (hueBar.height - 1));
        context.graphics().hLine(hueBar.x - 2, hueBar.right() + 2, hueY, 0xFFFFFFFF);

        Bounds preview = previewBounds(panel);
        context.graphics().fill(preview.x, preview.y, preview.right(), preview.bottom(),
                0xFF000000 | selectedRgb());
        border(context, preview, EditorHudTheme.outline());

        context.graphics().drawString(font, "#", panel.x + 174, panel.y + 29, TEXT, false);
        Bounds hex = hexBounds(panel);
        context.graphics().fill(hex.x, hex.y, hex.right(), hex.bottom(), 0xFF101014);
        border(context, hex, focusedHex ? ACCENT : EditorHudTheme.outline());
        String shown = focusedHex
                ? hexBuffer + (System.currentTimeMillis() / 500L % 2L == 0L ? "_" : "")
                : HexColorFormat.format(selectedRgb()).substring(1);
        if (shown.startsWith("#")) shown = shown.substring(1);
        context.graphics().drawString(font, shown, hex.x + 4, hex.y + 5,
                focusedHex ? TEXT : MUTED, false);

        button(context, font, acceptBounds(panel), "Accept", true);
        button(context, font, cancelBounds(panel), "Cancel", false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int screenWidth, int screenHeight) {
        if (!open) return false;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        Bounds panel = bounds(screenWidth, screenHeight);
        if (saturationValueBounds(panel).contains(mouseX, mouseY)) {
            focusedHex = false;
            draggingSaturationValue = true;
            updateSaturationValue(panel, mouseX, mouseY);
            return true;
        }
        if (hueBounds(panel).contains(mouseX, mouseY)) {
            focusedHex = false;
            draggingHue = true;
            updateHue(panel, mouseY);
            return true;
        }
        if (hexBounds(panel).contains(mouseX, mouseY)) {
            focusedHex = true;
            hexBuffer = HexColorFormat.format(selectedRgb());
            replaceHexOnType = true;
            return true;
        }
        if (acceptBounds(panel).contains(mouseX, mouseY)) {
            accept();
            return true;
        }
        if (cancelBounds(panel).contains(mouseX, mouseY)) {
            close();
            return true;
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY,
                                int screenWidth, int screenHeight) {
        if (!open) return false;
        Bounds panel = bounds(screenWidth, screenHeight);
        if (draggingSaturationValue) updateSaturationValue(panel, mouseX, mouseY);
        else if (draggingHue) updateHue(panel, mouseY);
        return true;
    }

    public boolean mouseReleased() {
        if (!open) return false;
        draggingSaturationValue = false;
        draggingHue = false;
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!open) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            applyHexBuffer();
            accept();
            return true;
        }
        if (focusedHex && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (replaceHexOnType) {
                hexBuffer = "";
                replaceHexOnType = false;
            } else if (!hexBuffer.isEmpty()) {
                hexBuffer = hexBuffer.substring(0, hexBuffer.length() - 1);
            }
            applyHexBuffer();
            return true;
        }
        return true;
    }

    public boolean charTyped(char character) {
        if (!open || !focusedHex) return open;
        if (!HexColorFormat.accepts(character)) return true;
        if (replaceHexOnType) {
            hexBuffer = "";
            replaceHexOnType = false;
        }
        hexBuffer = HexColorFormat.append(hexBuffer, character);
        applyHexBuffer();
        return true;
    }

    private void accept() {
        IntConsumer action = acceptAction;
        int rgb = selectedRgb();
        close();
        accepted = true;
        if (action != null) action.accept(rgb);
    }

    private void close() {
        open = false;
        draggingSaturationValue = false;
        draggingHue = false;
        focusedHex = false;
        replaceHexOnType = false;
        acceptAction = null;
    }

    private void applyHexBuffer() {
        var parsed = HexColorFormat.parse(hexBuffer);
        if (parsed.isEmpty()) return;
        float[] hsv = rgbToHsv(parsed.getAsInt());
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
    }

    private void updateSaturationValue(Bounds panel, double mouseX, double mouseY) {
        Bounds sv = saturationValueBounds(panel);
        saturation = clamp((float) ((mouseX - sv.x) / Math.max(1, sv.width - 1)));
        value = 1.0F - clamp((float) ((mouseY - sv.y) / Math.max(1, sv.height - 1)));
        hexBuffer = HexColorFormat.format(selectedRgb());
    }

    private void updateHue(Bounds panel, double mouseY) {
        Bounds bar = hueBounds(panel);
        hue = clamp((float) ((mouseY - bar.y) / Math.max(1, bar.height - 1)));
        hexBuffer = HexColorFormat.format(selectedRgb());
    }

    private int selectedRgb() {
        return hsvToRgb(hue, saturation, value);
    }

    private Bounds bounds(int screenWidth, int screenHeight) {
        return new Bounds((screenWidth - WIDTH) / 2, (screenHeight - HEIGHT) / 2,
                WIDTH, HEIGHT);
    }

    private Bounds saturationValueBounds(Bounds panel) {
        return new Bounds(panel.x + 9, panel.y + 27, SV_WIDTH, SV_HEIGHT);
    }

    private Bounds hueBounds(Bounds panel) {
        return new Bounds(panel.x + 149, panel.y + 27, 12, SV_HEIGHT);
    }

    private Bounds previewBounds(Bounds panel) {
        return new Bounds(panel.x + 174, panel.y + 91, 62, 22);
    }

    private Bounds hexBounds(Bounds panel) {
        return new Bounds(panel.x + 184, panel.y + 24, 52, 21);
    }

    private Bounds acceptBounds(Bounds panel) {
        return new Bounds(panel.x + 174, panel.y + 53, 62, 24);
    }

    private Bounds cancelBounds(Bounds panel) {
        return new Bounds(panel.x + 174, panel.y + 121, 62, 22);
    }

    private void button(VRenderContext context, Font font, Bounds bounds,
                        String label, boolean primary) {
        boolean hovered = bounds.contains(context.mouseX(), context.mouseY());
        context.graphics().fill(bounds.x, bounds.y, bounds.right(), bounds.bottom(),
                hovered ? 0xFF34343E : primary ? 0xFF24242C : 0xFF18181E);
        border(context, bounds, primary ? ACCENT : EditorHudTheme.outline());
        context.graphics().drawCenteredString(font, label,
                bounds.x + bounds.width / 2, bounds.y + 7, TEXT);
    }

    private void selector(VRenderContext context, int x, int y) {
        context.graphics().hLine(x - 3, x + 3, y - 3, 0xFFFFFFFF);
        context.graphics().hLine(x - 3, x + 3, y + 3, 0xFFFFFFFF);
        context.graphics().vLine(x - 3, y - 3, y + 3, 0xFFFFFFFF);
        context.graphics().vLine(x + 3, y - 3, y + 3, 0xFFFFFFFF);
        context.graphics().hLine(x - 2, x + 2, y - 2, 0xFF000000);
        context.graphics().hLine(x - 2, x + 2, y + 2, 0xFF000000);
        context.graphics().vLine(x - 2, y - 2, y + 2, 0xFF000000);
        context.graphics().vLine(x + 2, y - 2, y + 2, 0xFF000000);
    }

    private void border(VRenderContext context, Bounds bounds, int color) {
        context.graphics().hLine(bounds.x, bounds.right(), bounds.y, color);
        context.graphics().hLine(bounds.x, bounds.right(), bounds.bottom(), color);
        context.graphics().vLine(bounds.x, bounds.y, bounds.bottom(), color);
        context.graphics().vLine(bounds.right(), bounds.y, bounds.bottom(), color);
    }

    private static int mixRgb(int first, int second, float amount) {
        float t = clamp(amount);
        int red = Math.round(((first >> 16) & 0xFF) * (1.0F - t)
                + ((second >> 16) & 0xFF) * t);
        int green = Math.round(((first >> 8) & 0xFF) * (1.0F - t)
                + ((second >> 8) & 0xFF) * t);
        int blue = Math.round((first & 0xFF) * (1.0F - t) + (second & 0xFF) * t);
        return red << 16 | green << 8 | blue;
    }

    private static int hsvToRgb(float hue, float saturation, float value) {
        float h = (hue - (float) Math.floor(hue)) * 6.0F;
        int sector = (int) Math.floor(h);
        float fraction = h - sector;
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - saturation * fraction);
        float t = value * (1.0F - saturation * (1.0F - fraction));
        float red;
        float green;
        float blue;
        switch (sector) {
            case 0 -> { red = value; green = t; blue = p; }
            case 1 -> { red = q; green = value; blue = p; }
            case 2 -> { red = p; green = value; blue = t; }
            case 3 -> { red = p; green = q; blue = value; }
            case 4 -> { red = t; green = p; blue = value; }
            default -> { red = value; green = p; blue = q; }
        }
        return Math.round(red * 255.0F) << 16
                | Math.round(green * 255.0F) << 8
                | Math.round(blue * 255.0F);
    }

    private static float[] rgbToHsv(int rgb) {
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;
        float hue = 0.0F;
        if (delta > 0.00001F) {
            if (max == red) hue = ((green - blue) / delta) % 6.0F;
            else if (max == green) hue = (blue - red) / delta + 2.0F;
            else hue = (red - green) / delta + 4.0F;
            hue /= 6.0F;
            if (hue < 0.0F) hue += 1.0F;
        }
        float saturation = max <= 0.0F ? 0.0F : delta / max;
        return new float[]{hue, saturation, max};
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private record Bounds(int x, int y, int width, int height) {
        private int right() { return x + width; }
        private int bottom() { return y + height; }
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= right() && mouseY >= y && mouseY <= bottom();
        }
    }
}
