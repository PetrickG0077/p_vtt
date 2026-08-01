package com.petrick.vtt.network.client;

import com.petrick.vtt.network.payload.VttSceneCommandResultPayload;

import java.util.ArrayList;
import java.util.List;

/** One-shot scene-command acknowledgement consumed by the open VTT screen. */
public final class VttClientSceneCommandResultState {
    private static final List<VttSceneCommandResultPayload> pending = new ArrayList<>();

    private VttClientSceneCommandResultState() {}

    public static synchronized void accept(VttSceneCommandResultPayload payload) {
        if (payload == null) return;
        pending.removeIf(existing -> existing.requestId().equals(payload.requestId()));
        pending.add(payload);
        while (pending.size() > 16) pending.removeFirst();
    }

    public static synchronized VttSceneCommandResultPayload consume(String requestId) {
        if (requestId == null) return null;
        for (int index = 0; index < pending.size(); index++) {
            VttSceneCommandResultPayload result = pending.get(index);
            if (requestId.equals(result.requestId())) {
                pending.remove(index);
                return result;
            }
        }
        return null;
    }

    public static synchronized void reset() { pending.clear(); }
}
