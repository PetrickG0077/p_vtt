package com.petrick.vtt.network.client;

import com.petrick.vtt.network.payload.VttMapDefinitionResultPayload;

/** One-shot map-definition acknowledgement consumed by the open VTT screen. */
public final class VttClientMapDefinitionResultState {
    private static VttMapDefinitionResultPayload pending;

    private VttClientMapDefinitionResultState() {}

    public static synchronized void accept(VttMapDefinitionResultPayload payload) {
        if (payload != null) pending = payload;
    }

    public static synchronized VttMapDefinitionResultPayload consume() {
        VttMapDefinitionResultPayload result = pending;
        pending = null;
        return result;
    }

    public static synchronized void reset() { pending = null; }
}
