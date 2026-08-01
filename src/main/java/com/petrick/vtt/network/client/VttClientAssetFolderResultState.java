package com.petrick.vtt.network.client;

import com.petrick.vtt.network.payload.VttAssetFolderResultPayload;

/** One-shot acknowledgement mailbox consumed by the open VTT screen. */
public final class VttClientAssetFolderResultState {
    private static VttAssetFolderResultPayload pending;

    private VttClientAssetFolderResultState() {}

    public static synchronized void accept(VttAssetFolderResultPayload payload) {
        if (payload != null) pending = payload;
    }

    public static synchronized VttAssetFolderResultPayload consume() {
        VttAssetFolderResultPayload result = pending;
        pending = null;
        return result;
    }

    public static synchronized void reset() {
        pending = null;
    }
}
