package com.petrick.vtt.editor.hud;

import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

import java.util.List;

/** One confirmation for a validated same-section Asset Manager selection. */
public final class AssetBatchDeleteConfirmationOverlay {
    private static final int WIDTH = 440;
    private static final int HEIGHT = 190;

    public void render(VRenderContext context, Font font, Request request) {
        if (request == null) return;
        context.graphics().fill(0, 0, context.screenWidth(), context.screenHeight(), 0x99000000);
        Bounds panel = panel(context.screenWidth(), context.screenHeight());
        context.graphics().fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xFA151519);
        border(context, panel, 0xFFFF6666);
        context.graphics().drawCenteredString(
                font, "Delete selected " + request.sectionLabel(),
                panel.x() + panel.width() / 2, panel.y() + 13, 0xFFFF7777);
        context.graphics().drawCenteredString(
                font, request.itemCount() + " asset(s), "
                        + request.folderCount() + " folder(s)",
                panel.x() + panel.width() / 2, panel.y() + 37, 0xFFFFFFFF);
        context.graphics().drawCenteredString(
                font, "Contents of selected folders will be moved to their parent.",
                panel.x() + panel.width() / 2, panel.y() + 58, 0xFFCCCCCC);

        int y = panel.y() + 80;
        if (request.blocked()) {
            context.graphics().drawCenteredString(
                    font, "Deletion blocked:", panel.x() + panel.width() / 2,
                    y, 0xFFFFAA55);
            y += 14;
            for (int index = 0; index < Math.min(3, request.blockedReasons().size()); index++) {
                context.graphics().drawCenteredString(
                        font, trim(request.blockedReasons().get(index), 58),
                        panel.x() + panel.width() / 2, y, 0xFFFFAA55);
                y += 12;
            }
        } else {
            context.graphics().drawCenteredString(
                    font, "This action cannot be undone.",
                    panel.x() + panel.width() / 2, y + 8, 0xFFCCCCCC);
        }

        Bounds delete = deleteBounds(panel);
        Bounds cancel = cancelBounds(panel);
        boolean blocked = request.blocked();
        context.graphics().fill(
                delete.x(), delete.y(), delete.right(), delete.bottom(),
                blocked ? 0xFF333337 : delete.contains(context.mouseX(), context.mouseY())
                        ? 0xFFFF7777 : 0xFFAA3333);
        border(context, delete, blocked ? 0xFF66666A : 0xFFFFAAAA);
        context.graphics().drawCenteredString(
                font, "Delete selection", delete.x() + delete.width() / 2,
                delete.y() + 7, blocked ? 0xFF77777D : 0xFFFFFFFF);
        context.graphics().fill(
                cancel.x(), cancel.y(), cancel.right(), cancel.bottom(),
                cancel.contains(context.mouseX(), context.mouseY())
                        ? 0xFF55555B : 0xFF333337);
        border(context, cancel, 0xFFCCCCCC);
        context.graphics().drawCenteredString(
                font, blocked ? "Close" : "Cancel",
                cancel.x() + cancel.width() / 2, cancel.y() + 7, 0xFFFFFFFF);
    }

    public Action mouseClicked(
            double mouseX, double mouseY, int button,
            int screenWidth, int screenHeight, Request request
    ) {
        if (request == null) return Action.NONE;
        if (button != 0) return Action.HANDLED;
        Bounds panel = panel(screenWidth, screenHeight);
        if (!request.blocked() && deleteBounds(panel).contains(mouseX, mouseY)) {
            return Action.DELETE;
        }
        if (cancelBounds(panel).contains(mouseX, mouseY)) return Action.CANCEL;
        return Action.HANDLED;
    }

    private Bounds panel(int width, int height) {
        return new Bounds((width - WIDTH) / 2, (height - HEIGHT) / 2, WIDTH, HEIGHT);
    }

    private Bounds deleteBounds(Bounds panel) {
        return new Bounds(panel.x() + 42, panel.bottom() - 48, 170, 24);
    }

    private Bounds cancelBounds(Bounds panel) {
        return new Bounds(panel.right() - 162, panel.bottom() - 48, 120, 24);
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

    public enum Action { NONE, DELETE, CANCEL, HANDLED }

    public record Request(
            String sectionLabel,
            int itemCount,
            int folderCount,
            List<String> blockedReasons
    ) {
        public Request {
            sectionLabel = sectionLabel == null ? "assets" : sectionLabel;
            blockedReasons = blockedReasons == null ? List.of() : List.copyOf(blockedReasons);
        }

        public boolean blocked() { return !blockedReasons.isEmpty(); }
    }

    private record Bounds(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        boolean contains(double px, double py) {
            return px >= x && px <= right() && py >= y && py <= bottom();
        }
    }
}
