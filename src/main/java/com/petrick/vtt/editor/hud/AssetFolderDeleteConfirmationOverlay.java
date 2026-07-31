package com.petrick.vtt.editor.hud;

import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

import java.util.List;

/** Safe confirmation for deleting physical Asset Manager folders. */
public final class AssetFolderDeleteConfirmationOverlay {
    private static final int WIDTH = 430;
    private static final int HEIGHT = 172;

    public void render(VRenderContext context, Font font, Request request) {
        if (request == null) return;
        context.graphics().fill(
                0, 0, context.screenWidth(), context.screenHeight(), 0x99000000);
        Bounds panel = panel(context.screenWidth(), context.screenHeight());
        context.graphics().fill(
                panel.x(), panel.y(), panel.right(), panel.bottom(), 0xFA151519);
        border(context, panel, 0xFFFF6666);

        context.graphics().drawCenteredString(
                font, "Delete Folder",
                panel.x() + panel.width() / 2, panel.y() + 13, 0xFFFF7777);
        context.graphics().drawCenteredString(
                font, trim("Assets/" + request.folder(), 52),
                panel.x() + panel.width() / 2, panel.y() + 34, 0xFFFFFFFF);

        int infoY = panel.y() + 57;
        if (!request.exists()) {
            context.graphics().drawCenteredString(
                    font, "This folder no longer exists.",
                    panel.x() + panel.width() / 2, infoY, 0xFFFFAA55);
        } else if (request.empty()) {
            context.graphics().drawCenteredString(
                    font, "The folder is empty. This action cannot be undone.",
                    panel.x() + panel.width() / 2, infoY, 0xFFCCCCCC);
        } else {
            context.graphics().drawCenteredString(
                    font,
                    request.itemCount() + " file(s), "
                            + request.folderCount() + " subfolder(s)",
                    panel.x() + panel.width() / 2, infoY, 0xFFCCCCCC);
            context.graphics().drawCenteredString(
                    font,
                    "Contents will be moved to "
                            + (request.parentFolder().isBlank()
                            ? "Assets" : "Assets/" + request.parentFolder()),
                    panel.x() + panel.width() / 2, infoY + 15, 0xFFCCCCCC);
            if (request.hasConflicts()) {
                context.graphics().drawCenteredString(
                        font, "Blocked by name conflict: "
                                + trim(String.join(", ", request.conflicts()), 34),
                        panel.x() + panel.width() / 2, infoY + 34, 0xFFFFAA55);
            }
        }

        Bounds confirm = confirmBounds(panel);
        Bounds cancel = cancelBounds(panel);
        boolean blocked = request.blocked();
        context.graphics().fill(
                confirm.x(), confirm.y(), confirm.right(), confirm.bottom(),
                blocked ? 0xFF333337
                        : confirm.contains(context.mouseX(), context.mouseY())
                        ? 0xFFFF7777 : 0xFFAA3333);
        border(context, confirm, blocked ? 0xFF66666A : 0xFFFFAAAA);
        context.graphics().drawCenteredString(
                font,
                request.empty() ? "Delete" : "Move contents + delete",
                confirm.x() + confirm.width() / 2, confirm.y() + 7,
                blocked ? 0xFF77777D : 0xFFFFFFFF);

        context.graphics().fill(
                cancel.x(), cancel.y(), cancel.right(), cancel.bottom(),
                cancel.contains(context.mouseX(), context.mouseY())
                        ? 0xFF55555B : 0xFF333337);
        border(context, cancel, 0xFFCCCCCC);
        context.graphics().drawCenteredString(
                font, blocked ? "Close" : "Cancel",
                cancel.x() + cancel.width() / 2, cancel.y() + 7, 0xFFFFFFFF);

        context.graphics().drawCenteredString(
                font, blocked ? "Esc: close" : "Enter: confirm   Esc: cancel",
                panel.x() + panel.width() / 2, panel.bottom() - 15, 0xFF88888E);
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
        Bounds panel = panel(screenWidth, screenHeight);
        if (!request.blocked() && confirmBounds(panel).contains(mouseX, mouseY)) {
            return request.empty() ? Action.DELETE : Action.MOVE_AND_DELETE;
        }
        if (cancelBounds(panel).contains(mouseX, mouseY)) return Action.CANCEL;
        return Action.HANDLED;
    }

    private Bounds panel(int screenWidth, int screenHeight) {
        return new Bounds(
                (screenWidth - WIDTH) / 2,
                (screenHeight - HEIGHT) / 2,
                WIDTH, HEIGHT);
    }

    private Bounds confirmBounds(Bounds panel) {
        return new Bounds(panel.x() + 30, panel.bottom() - 54, 210, 24);
    }

    private Bounds cancelBounds(Bounds panel) {
        return new Bounds(panel.right() - 150, panel.bottom() - 54, 120, 24);
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
        MOVE_AND_DELETE,
        CANCEL,
        HANDLED
    }

    public record Request(
            boolean exists,
            String folder,
            String parentFolder,
            int folderCount,
            int itemCount,
            List<String> conflicts
    ) {
        public Request {
            folder = folder == null ? "" : folder;
            parentFolder = parentFolder == null ? "" : parentFolder;
            conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        }

        public boolean empty() {
            return folderCount == 0 && itemCount == 0;
        }

        public boolean hasConflicts() {
            return !conflicts.isEmpty();
        }

        public boolean blocked() {
            return !exists || hasConflicts();
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
