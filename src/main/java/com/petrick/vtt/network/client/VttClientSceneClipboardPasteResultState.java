package com.petrick.vtt.network.client;

import com.petrick.vtt.network.payload.VttSceneClipboardPasteResultPayload;

import java.util.ArrayList;
import java.util.List;

/** One-shot completion markers consumed by the open editor screen. */
public final class VttClientSceneClipboardPasteResultState {
    private static final List<VttSceneClipboardPasteResultPayload> pending =
            new ArrayList<>();

    private VttClientSceneClipboardPasteResultState() {}

    public static synchronized void accept(VttSceneClipboardPasteResultPayload payload) {
        if (payload == null) return;
        pending.removeIf(existing -> existing.requestId().equals(payload.requestId()));
        pending.add(payload);
        while (pending.size() > 16) pending.removeFirst();
    }

    public static synchronized VttSceneClipboardPasteResultPayload consume(
            String requestId
    ) {
        if (requestId == null) return null;
        for (int index = 0; index < pending.size(); index++) {
            VttSceneClipboardPasteResultPayload result = pending.get(index);
            if (requestId.equals(result.requestId())) {
                pending.remove(index);
                return result;
            }
        }
        return null;
    }

    public static synchronized void reset() { pending.clear(); }
}
