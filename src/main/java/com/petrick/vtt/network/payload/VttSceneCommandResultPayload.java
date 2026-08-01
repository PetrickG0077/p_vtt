package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Explicit server acknowledgement for a scene command. */
public record VttSceneCommandResultPayload(
        String requestId, boolean success, String code, String message,
        long authorityRevision, String sceneId
) implements CustomPacketPayload {
    public static final String OK = "OK";
    public static final String STALE_REVISION = "STALE_REVISION";
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String LIMIT_REACHED = "LIMIT_REACHED";
    public static final String REJECTED = "REJECTED";

    public static final Type<VttSceneCommandResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "scene_command_result"));
    public static final StreamCodec<ByteBuf, VttSceneCommandResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64), VttSceneCommandResultPayload::requestId,
                    ByteBufCodecs.BOOL, VttSceneCommandResultPayload::success,
                    ByteBufCodecs.stringUtf8(32), VttSceneCommandResultPayload::code,
                    ByteBufCodecs.stringUtf8(256), VttSceneCommandResultPayload::message,
                    ByteBufCodecs.VAR_LONG, VttSceneCommandResultPayload::authorityRevision,
                    ByteBufCodecs.stringUtf8(128), VttSceneCommandResultPayload::sceneId,
                    VttSceneCommandResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
