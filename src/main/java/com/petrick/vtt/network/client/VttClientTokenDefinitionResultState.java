package com.petrick.vtt.network.client;

import com.petrick.vtt.network.payload.VttTokenDefinitionResultPayload;

/** One-shot token-definition acknowledgement consumed by the open VTT screen. */
public final class VttClientTokenDefinitionResultState {
    private static VttTokenDefinitionResultPayload pending;

    private VttClientTokenDefinitionResultState() {}

    public static synchronized void accept(VttTokenDefinitionResultPayload payload) {
        if (payload != null) pending = payload;
    }

    public static synchronized VttTokenDefinitionResultPayload consume() {
        VttTokenDefinitionResultPayload result = pending;
        pending = null;
        return result;
    }

    public static synchronized void reset() { pending = null; }
}
