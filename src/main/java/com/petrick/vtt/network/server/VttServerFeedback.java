package com.petrick.vtt.network.server;

import com.petrick.vtt.network.payload.VttEditorNoticePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends visible feedback for rejected master editor operations. */
public final class VttServerFeedback {
    private VttServerFeedback() {}

    public static void showLimit(ServerPlayer player, String message) {
        show(player, message);
    }

    public static void show(ServerPlayer player, String message) {
        if (player == null || message == null || message.isBlank()) return;
        String notice = "VTT: " + message;
        if (notice.length() > VttEditorNoticePayload.MAX_MESSAGE_LENGTH) {
            notice = notice.substring(0, VttEditorNoticePayload.MAX_MESSAGE_LENGTH);
        }
        PacketDistributor.sendToPlayer(player, new VttEditorNoticePayload(notice));
    }
}
