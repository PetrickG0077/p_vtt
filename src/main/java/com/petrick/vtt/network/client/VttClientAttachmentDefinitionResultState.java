package com.petrick.vtt.network.client;

import com.petrick.vtt.network.payload.VttAttachmentDefinitionResultPayload;

/** One-shot attachment-definition acknowledgement consumed by the open VTT screen. */
public final class VttClientAttachmentDefinitionResultState {
    private static VttAttachmentDefinitionResultPayload pending;

    private VttClientAttachmentDefinitionResultState() {}

    public static synchronized void accept(VttAttachmentDefinitionResultPayload payload) {
        if (payload != null) pending = payload;
    }

    public static synchronized VttAttachmentDefinitionResultPayload consume() {
        VttAttachmentDefinitionResultPayload result = pending;
        pending = null;
        return result;
    }

    public static synchronized void reset() { pending = null; }
}
