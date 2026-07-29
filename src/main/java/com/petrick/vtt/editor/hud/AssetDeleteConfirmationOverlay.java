package com.petrick.vtt.editor.hud;

import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

import java.util.List;

/** Modal confirmation shown before deleting an Asset Manager item. */
public final class AssetDeleteConfirmationOverlay {
    private static final int WIDTH = 390;
    private static final int BASE_HEIGHT = 126;
    private static final int MAX_VISIBLE_USAGES = 4;

    public void render(VRenderContext context, Font font, Request request) {
        if (request == null) return;
        context.graphics().fill(
                0, 0, context.screenWidth(), context.screenHeight(), 0x99000000);
        Bounds panel = panel(context.screenWidth(), context.screenHeight(), request);
        context.graphics().fill(
                panel.x(), panel.y(), panel.right(), panel.bottom(), 0xFA151519);
        border(context, panel, 0xFFFF6666);

        context.graphics().drawCenteredString(
                font, "Delete " + request.typeLabel(),
                panel.x() + panel.width() / 2, panel.y() + 13, 0xFFFF7777);
        context.graphics().drawCenteredString(
                font, "'" + trim(request.displayName(), 42) + "'",
                panel.x() + panel.width() / 2, panel.y() + 34, 0xFFFFFFFF);

        int textY = panel.y() + 55;
        if (request.blocked()) {
            context.graphics().drawString(
                    font, request.blockedMessage(),
                    panel.x() + 16, textY, 0xFFFFAA55, false);
            textY += 15;
            if (!request.usages().isEmpty()) {
                context.graphics().drawString(
                        font, "Used in:",
                        panel.x() + 16, textY, 0xFFCCCCCC, false);
                textY += 13;
                int visible = Math.min(MAX_VISIBLE_USAGES, request.usages().size());
                for (int index = 0; index < visible; index++) {
                    context.graphics().drawString(
                            font, "- " + trim(request.usages().get(index), 48),
                            panel.x() + 24, textY, 0xFFFFFFFF, false);
                    textY += 12;
                }
                if (request.usages().size() > visible) {
                    context.graphics().drawString(
                            font, "+ " + (request.usages().size() - visible) + " more scenes",
                            panel.x() + 24, textY, 0xFFAAAAAA, false);
                }
            }
        } else {
            context.graphics().drawCenteredString(
                    font, "This action cannot be undone.",
                    panel.x() + panel.width() / 2, textY + 5, 0xFFCCCCCC);
        }

        Bounds delete = deleteBounds(panel);
        Bounds cancel = cancelBounds(panel);
        context.graphics().fill(
                delete.x(), delete.y(), delete.right(), delete.bottom(),
                request.blocked() ? 0xFF333337
                        : delete.contains(context.mouseX(), context.mouseY())
                        ? 0xFFFF7777 : 0xFFAA3333);
        border(context, delete, request.blocked() ? 0xFF66666A : 0xFFFFAAAA);
        context.graphics().drawCenteredString(
                font, "Delete", delete.x() + delete.width() / 2,
                delete.y() + 7, request.blocked() ? 0xFF77777D : 0xFFFFFFFF);

        context.graphics().fill(
                cancel.x(), cancel.y(), cancel.right(), cancel.bottom(),
                cancel.contains(context.mouseX(), context.mouseY())
                        ? 0xFF55555B : 0xFF333337);
        border(context, cancel, 0xFFCCCCCC);
        context.graphics().drawCenteredString(
                font, request.blocked() ? "Close" : "Cancel",
                cancel.x() + cancel.width() / 2, cancel.y() + 7, 0xFFFFFFFF);

        context.graphics().drawCenteredString(
                font, request.blocked()
                        ? "Esc: close"
                        : "Enter: delete   Esc: cancel",
                panel.x() + panel.width() / 2,
                panel.bottom() - 16, 0xFF88888E);
    }

    public Action mouseClicked(
            double mouseX,
            double mouseY,
            int button,
            int screenWidth,
            int screenHeight,
            Request request
    ) {
        if (request == null) return Action.NONE;
        if (button != 0) return Action.HANDLED;
        Bounds panel = panel(screenWidth, screenHeight, request);
        if (!request.blocked() && deleteBounds(panel).contains(mouseX, mouseY)) {
            return Action.DELETE;
        }
        if (cancelBounds(panel).contains(mouseX, mouseY)) {
            return Action.CANCEL;
        }
        return Action.HANDLED;
    }

    private Bounds panel(int screenWidth, int screenHeight, Request request) {
        int usageLines = request.blocked() && !request.usages().isEmpty()
                ? Math.min(MAX_VISIBLE_USAGES, request.usages().size())
                        + (request.usages().size() > MAX_VISIBLE_USAGES ? 1 : 0)
                : 0;
        int height = BASE_HEIGHT + usageLines * 12
                + (request.blocked() && !request.usages().isEmpty() ? 20 : 0);
        return new Bounds(
                (screenWidth - WIDTH) / 2,
                (screenHeight - height) / 2,
                WIDTH, height);
    }

    private Bounds deleteBounds(Bounds panel) {
        return new Bounds(
                panel.x() + 54, panel.bottom() - 48, 120, 24);
    }

    private Bounds cancelBounds(Bounds panel) {
        return new Bounds(
                panel.right() - 174,
                panel.bottom() - 48, 120, 24);
    }

    private void border(VRenderContext context, Bounds bounds, int color) {
        context.graphics().hLine(bounds.x(), bounds.right(), bounds.y(), color);
        context.graphics().hLine(bounds.x(), bounds.right(), bounds.bottom(), color);
        context.graphics().vLine(bounds.x(), bounds.y(), bounds.bottom(), color);
        context.graphics().vLine(bounds.right(), bounds.y(), bounds.bottom(), color);
    }

    private String trim(String value, int maximumLength) {
        if (value == null) return "";
        return value.length() <= maximumLength
                ? value : value.substring(0, maximumLength - 3) + "...";
    }

    public enum Action {
        NONE,
        DELETE,
        CANCEL,
        HANDLED
    }

    public record Request(
            String typeLabel,
            String displayName,
            List<String> usages,
            String blockedMessage
    ) {
        public Request {
            typeLabel = typeLabel == null ? "Asset" : typeLabel;
            displayName = displayName == null ? "" : displayName;
            usages = usages == null ? List.of() : List.copyOf(usages);
            blockedMessage = blockedMessage == null ? "" : blockedMessage;
        }

        public boolean blocked() {
            return !blockedMessage.isBlank();
        }
    }

    private record Bounds(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(double px, double py) {
            return px >= x && px <= right() && py >= y && py <= bottom();
        }
    }
}
