package com.petrick.vtt.feature.selection;

import com.petrick.vtt.core.math.Rectd;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Controla quais objetos estão selecionados no canvas.
 */
public final class SelectionManager {

    private final Set<String> selectedObjectIds = new HashSet<>();

    public void clearSelection() {
        selectedObjectIds.clear();
    }

    public void select(String objectId) {
        selectedObjectIds.add(objectId);
    }

    public void selectOnly(String objectId) {
        clearSelection();
        select(objectId);
    }

    public void deselect(String objectId) {
        selectedObjectIds.remove(objectId);
    }

    public void toggle(String objectId) {
        if (isSelected(objectId)) {
            deselect(objectId);
        } else {
            select(objectId);
        }
    }

    public boolean isSelected(String objectId) {
        return selectedObjectIds.contains(objectId);
    }

    public Set<String> getSelectedObjectIds() {
        return Collections.unmodifiableSet(selectedObjectIds);
    }

    public void removeMissingObjects(CanvasScene scene) {
        if (scene == null) {
            clearSelection();
            return;
        }
        selectedObjectIds.removeIf(objectId -> scene.findObjectById(objectId) == null);
    }

    public void selectSingleAtPoint(CanvasScene scene, Vec2d worldPosition) {
        clearSelection();

        CanvasObject object = findTopmostObjectAtPoint(scene, worldPosition);

        if (object != null) {
            select(object.id());
        }
    }

    public void toggleAtPoint(CanvasScene scene, Vec2d worldPosition) {
        CanvasObject object = findTopmostObjectAtPoint(scene, worldPosition);

        if (object != null) {
            toggle(object.id());
        }
    }

    public void selectObjectsInside(CanvasScene scene, Rectd worldBounds) {
        selectObjectsInside(scene, worldBounds, false);
    }

    public void selectObjectsInside(CanvasScene scene, Rectd worldBounds, boolean includeHidden) {
        clearSelection();
        addObjectsInside(scene, worldBounds, includeHidden);
    }

    public void addObjectsInside(CanvasScene scene, Rectd worldBounds) {
        addObjectsInside(scene, worldBounds, false);
    }

    public void addObjectsInside(CanvasScene scene, Rectd worldBounds, boolean includeHidden) {
        for (CanvasObject object : scene.getObjects()) {
            if ((object.visible() || includeHidden) && object.bounds().intersects(worldBounds)) {
                select(object.id());
            }
        }
    }

    public CanvasObject findTopmostObjectAtPoint(CanvasScene scene, Vec2d worldPosition) {
        return findTopmostObjectAtPoint(scene, worldPosition, false);
    }

    public CanvasObject findTopmostObjectAtPoint(
            CanvasScene scene, Vec2d worldPosition, boolean includeHidden
    ) {
        List<CanvasObject> objects = scene.getObjects();

        for (int i = objects.size() - 1; i >= 0; i--) {
            CanvasObject object = objects.get(i);

            if (!object.visible() && !includeHidden) {
                continue;
            }

            if (object.containsWorldPoint(worldPosition)) {
                return object;
            }
        }

        return null;
    }
}
