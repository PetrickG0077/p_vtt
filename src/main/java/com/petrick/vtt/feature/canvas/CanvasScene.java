package com.petrick.vtt.feature.canvas;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.transform.Transform2D;

import com.petrick.vtt.feature.canvas.visual.ColorVisual;
import com.petrick.vtt.feature.canvas.visual.TextureVisual;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.DebugAssets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Cena temporária do canvas.
 *
 * Por enquanto ela guarda objetos simples de teste.
 * Futuramente será substituída por uma cena baseada no ECS.
 */
public final class CanvasScene {

    private final List<CanvasObject> objects = new ArrayList<>();

    public static CanvasScene createDebugScene(AssetRegistry assetRegistry) {
        CanvasScene scene = new CanvasScene();

        scene.addObject(new CanvasObject(
                "object_1",
                new Transform2D(
                        new Vec2d(0.0, 0.0),
                        0.0,
                        new Vec2d(1.0, 1.0)
                ),
                new Vec2d(100.0, 100.0),
                new ColorVisual(0xFFFFFFFF),
                true
        ));

        scene.addObject(new CanvasObject(
                "object_2",
                new Transform2D(
                        new Vec2d(190.0, 0.0),
                        0.0,
                        new Vec2d(1.0, 1.0)
                ),
                new Vec2d(80.0, 80.0),
                new ColorVisual(0xFFFFAA55),
                true
        ));

        scene.addObject(new CanvasObject(
                "object_3",
                new Transform2D(
                        new Vec2d(-160.0, 135.0),
                        0.0,
                        new Vec2d(1.0, 1.0)
                ),
                new Vec2d(120.0, 70.0),
                new ColorVisual(0xFF55AAFF),
                true
        ));

        scene.addObject(new CanvasObject(
                "texture_test",
                new Transform2D(
                        new Vec2d(0.0, -180.0),
                        0.0,
                        new Vec2d(1.0, 1.0)
                ),
                new Vec2d(96.0, 96.0),
                new TextureVisual(
                        assetRegistry.getRequired(DebugAssets.TEST_TOKEN_ID)
                ),
                true
        ));

        return scene;
    }

    public void addObject(CanvasObject object) {
        objects.add(object);
    }

    public List<CanvasObject> getObjects() {
        return Collections.unmodifiableList(objects);
    }

    public CanvasObject findObjectById(String objectId) {
        for (CanvasObject object : objects) {
            if (object.id().equals(objectId)) {
                return object;
            }
        }

        return null;
    }

    public void removeObjectById(String objectId) {
        objects.removeIf(object -> object.id().equals(objectId));
    }

    public void removeObjects(Set<String> objectIds) {
        if (objectIds == null || objectIds.isEmpty()) {
            return;
        }

        objects.removeIf(object -> objectIds.contains(object.id()));
    }

    public String createUniqueObjectId(String prefix) {
        int index = 1;

        while (findObjectById(prefix + "_" + index) != null) {
            index++;
        }

        return prefix + "_" + index;
    }

    public void replaceObject(CanvasObject replacement) {
        for (int i = 0; i < objects.size(); i++) {
            CanvasObject object = objects.get(i);

            if (object.id().equals(replacement.id())) {
                objects.set(i, replacement);
                return;
            }
        }
    }

    public void replaceObjectById(String objectId, CanvasObject replacement) {
        for (int i = 0; i < objects.size(); i++) {
            CanvasObject object = objects.get(i);

            if (object.id().equals(objectId)) {
                objects.set(i, replacement);
                return;
            }
        }
    }

    public void moveObjects(Set<String> objectIds, Vec2d worldDelta) {
        for (String objectId : objectIds) {
            CanvasObject object = findObjectById(objectId);

            if (object != null) {
                replaceObject(object.movedBy(worldDelta));
            }
        }
    }

    public void scaleObjects(Set<String> objectIds, double factor) {
        for (String objectId : objectIds) {
            CanvasObject object = findObjectById(objectId);

            if (object != null) {
                replaceObject(object.scaledBy(factor));
            }
        }
    }

    public void rotateObjects(Set<String> objectIds, double deltaDegrees) {
        for (String objectId : objectIds) {
            CanvasObject object = findObjectById(objectId);

            if (object != null) {
                replaceObject(object.rotatedBy(deltaDegrees));
            }
        }
    }

    public void toggleObjectsVisibility(Set<String> objectIds) {
        if (objectIds == null || objectIds.isEmpty()) {
            return;
        }

        for (String objectId : objectIds) {
            CanvasObject object = findObjectById(objectId);

            if (object != null) {
                replaceObject(object.toggledVisibility());
            }
        }
    }

    public void resetObjectsScaleAndRotation(Set<String> objectIds) {
        if (objectIds == null || objectIds.isEmpty()) {
            return;
        }

        for (String objectId : objectIds) {
            CanvasObject object = findObjectById(objectId);

            if (object != null) {
                replaceObject(object.resetScaleAndRotation());
            }
        }
    }
}