package com.petrick.vtt.network.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.session.VTTSession;
import com.petrick.vtt.feature.attachment.AttachmentDefinition;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.feature.tabletop.VttSceneSize;
import com.petrick.vtt.feature.tabletop.VttSceneState;
import com.petrick.vtt.feature.tabletop.VttSceneTransform;
import com.petrick.vtt.network.payload.VttTokenLifecycleRequestPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends explicit placement requests for reusable attachments on an authoritative server. */
public final class VttClientAttachmentLifecycleSync {
    private static final Gson GSON = new GsonBuilder().create();

    private VttClientAttachmentLifecycleSync() {}

    public static boolean sendCreate(
            VTTSession session, AttachmentDefinition definition,
            String requestedObjectId, Vec2d worldPosition
    ) {
        if (session == null || definition == null || requestedObjectId == null
                || requestedObjectId.isBlank() || worldPosition == null
                || !Double.isFinite(worldPosition.x()) || !Double.isFinite(worldPosition.y())) {
            return false;
        }
        VttSceneObject object = new VttSceneObject();
        object.setId(requestedObjectId);
        object.setDisplayName(definition.displayName());
        object.setSourceAttachmentDefinitionId(definition.id());
        object.setTransform(new VttSceneTransform(
                worldPosition.x(), worldPosition.y(), 1.0, 1.0, 0.0));
        object.setSize(new VttSceneSize(definition.defaultWidth(), definition.defaultHeight()));
        var defaultState = definition.states().get(definition.defaultStateId());
        VttSceneState state = new VttSceneState(definition.defaultStateId(),
                defaultState == null || defaultState.visible(), false);
        if (defaultState != null) state.setTintColorRgb(defaultState.tintColorRgb());
        object.setState(state);
        object.setLayerIndex(session.getActiveScene() == null
                ? 0 : session.getActiveScene().getObjects().size());
        PacketDistributor.sendToServer(new VttTokenLifecycleRequestPayload(
                VttClientTokenLifecycleSync.CREATE, requestedObjectId, GSON.toJson(object)));
        return true;
    }

    public static boolean sendCreateObject(VttSceneObject object) {
        if (object == null || object.getId() == null || object.getId().isBlank()) return false;
        PacketDistributor.sendToServer(new VttTokenLifecycleRequestPayload(
                VttClientTokenLifecycleSync.CREATE, object.getId(), GSON.toJson(object)));
        return true;
    }
}
