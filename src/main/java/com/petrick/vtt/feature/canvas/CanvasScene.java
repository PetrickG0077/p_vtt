package com.petrick.vtt.feature.canvas;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.transform.Transform2D;

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

    public static CanvasScene createDebugScene() {
        CanvasScene scene = new CanvasScene();

        scene.addObject(new CanvasObject(
                "object_1",
                new Transform2D(
                        new Vec2d(0, 0),
                        0.0,
                        new Vec2d(1.0, 1.0)
                ),
                new Vec2d(100, 100),
                0xFFFFFFFF
        ));

        scene.addObject(new CanvasObject(
                "object_2",
                new Transform2D(
                        new Vec2d(190, 0),
                        0.0,
                        new Vec2d(1.0, 1.0)
                ),
                new Vec2d(80, 80),
                0xFFFFAA55
        ));

        scene.addObject(new CanvasObject(
                "object_3",
                new Transform2D(
                        new Vec2d(-160, 135),
                        0.0,
                        new Vec2d(1.0, 1.0)
                ),
                new Vec2d(120, 70),
                0xFF55AAFF
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
}