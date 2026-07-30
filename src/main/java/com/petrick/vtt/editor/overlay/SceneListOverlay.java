package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.VTT;
import com.petrick.vtt.editor.catalog.CatalogFolderBrowser;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttTabletop;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Optional;

/** Simple scene selector for the active tabletop. */
public final class SceneListOverlay {
    private static final int WIDTH = 220;
    private static final int PADDING = 8;
    private static final int LINE_HEIGHT = 12;
    private static final int OFFSET_FROM_BOTTOM = 40;
    private static final int FOLDER_ROW_BACKGROUND = 0xAA4B236E;
    private static final ResourceLocation FOLDER_ICON =
            ResourceLocation.fromNamespaceAndPath(
                    VTT.MOD_ID, "textures/gui/editor_hud/folder.png");
    private final CatalogFolderBrowser<String> folderBrowser =
            new CatalogFolderBrowser<>();

    public SceneListOverlay() {
    }

    public void render(VRenderContext context, Font font, VttTabletop tabletop,
                       VttScene activeScene) {
        if (tabletop == null) return;
        List<CatalogFolderBrowser.Row<String>> rows = rows(tabletop);
        int height = PADDING * 2 + 26 + Math.max(1, rows.size()) * LINE_HEIGHT;
        int x = panelX(context.screenWidth());
        int y = panelY(context.screenHeight(), height);
        context.graphics().fill(x, y, x + WIDTH, y + height, 0xDD000000);
        context.graphics().hLine(x, x + WIDTH, y, 0xFFFFAA44);
        context.graphics().hLine(x, x + WIDTH, y + height, 0xFFFFAA44);
        context.graphics().vLine(x, y, y + height, 0xFFFFAA44);
        context.graphics().vLine(x + WIDTH, y, y + height, 0xFFFFAA44);
        context.graphics().drawString(
                font, folderBrowser.breadcrumb("Scenes"),
                x + PADDING, y + PADDING, 0xFFFFFFFF, false);
        int rowY = y + PADDING + 18;
        if (rows.isEmpty()) {
            context.graphics().drawString(
                    font, "No scenes", x + PADDING, rowY, 0xFF888888, false);
            rowY += LINE_HEIGHT;
        } else {
            for (CatalogFolderBrowser.Row<String> row : rows) {
                if (row.folder()) {
                    context.graphics().fill(
                            x + 4, rowY - 1, x + WIDTH - 4,
                            rowY + LINE_HEIGHT - 1, FOLDER_ROW_BACKGROUND);
                    context.graphics().blit(
                            FOLDER_ICON, x + PADDING, rowY,
                            10, 10, 0.0F, 0.0F, 32, 32, 32, 32);
                    context.graphics().drawString(
                            font, row.displayName(),
                            x + PADDING + 15, rowY, 0xFFFFFFFF, false);
                    rowY += LINE_HEIGHT;
                    continue;
                }
                String id = row.item();
                boolean active = activeScene != null && id.equals(activeScene.getId());
                String name = active ? activeScene.getDisplayName() : tabletop.getSceneDisplayName(id);
                context.graphics().drawString(font, (active ? "> " : "  ") + name,
                        x + PADDING, rowY, active ? 0xFFFFCC66 : 0xFFDDDDDD, false);
                rowY += LINE_HEIGHT;
            }
        }
    }

    public Optional<String> findSceneIdAt(
            VttTabletop tabletop,
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        if (tabletop == null) return Optional.empty();
        int x = panelX(screenWidth);
        List<CatalogFolderBrowser.Row<String>> rows = rows(tabletop);
        int height = PADDING * 2 + 26 + Math.max(1, rows.size()) * LINE_HEIGHT;
        int firstY = panelY(screenHeight, height) + PADDING + 18;
        if (mouseX < x || mouseX > x + WIDTH) return Optional.empty();
        for (int i = 0; i < rows.size(); i++) {
            int top = firstY + i * LINE_HEIGHT;
            if (mouseY >= top && mouseY <= top + LINE_HEIGHT) {
                return Optional.ofNullable(rows.get(i).item());
            }
        }
        return Optional.empty();
    }

    public boolean openFolderAt(
            VttTabletop tabletop,
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        if (tabletop == null) return false;
        List<CatalogFolderBrowser.Row<String>> rows = rows(tabletop);
        int x = panelX(screenWidth);
        int height = PADDING * 2 + 26 + Math.max(1, rows.size()) * LINE_HEIGHT;
        int firstY = panelY(screenHeight, height) + PADDING + 18;
        if (mouseX < x || mouseX > x + WIDTH) return false;
        for (int index = 0; index < rows.size(); index++) {
            int top = firstY + index * LINE_HEIGHT;
            if (mouseY >= top && mouseY <= top + LINE_HEIGHT) {
                return folderBrowser.open(rows.get(index));
            }
        }
        return false;
    }

    private List<CatalogFolderBrowser.Row<String>> rows(VttTabletop tabletop) {
        if (tabletop == null) return List.of();
        return folderBrowser.rows(
                tabletop.getSceneIds(),
                tabletop::getSceneFolder,
                tabletop::getSceneDisplayName);
    }

    private int panelX(int screenWidth) {
        return (screenWidth - WIDTH) / 2;
    }

    private int panelY(int screenHeight, int height) {
        return screenHeight - height - OFFSET_FROM_BOTTOM;
    }

}
