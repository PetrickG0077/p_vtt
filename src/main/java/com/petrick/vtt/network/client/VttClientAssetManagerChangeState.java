package com.petrick.vtt.network.client;

import com.petrick.vtt.network.payload.VttAssetManagerChangePayload;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Ordered remote Asset Manager changes waiting for their matching snapshot. */
public final class VttClientAssetManagerChangeState {
    private static final int MAX_PENDING_CHANGES = 64;
    private static final ArrayDeque<VttAssetManagerChangePayload> PENDING =
            new ArrayDeque<>();

    private VttClientAssetManagerChangeState() {}

    public static synchronized void accept(VttAssetManagerChangePayload payload) {
        if (payload == null) return;
        while (PENDING.size() >= MAX_PENDING_CHANGES) PENDING.removeFirst();
        PENDING.addLast(payload);
    }

    public static synchronized List<VttAssetManagerChangePayload> consumeThrough(
            long authorityRevision
    ) {
        List<VttAssetManagerChangePayload> ready = new ArrayList<>();
        while (!PENDING.isEmpty()
                && PENDING.peekFirst().authorityRevision() <= authorityRevision) {
            ready.add(PENDING.removeFirst());
        }
        return List.copyOf(ready);
    }

    public static synchronized void reset() {
        PENDING.clear();
    }
}
