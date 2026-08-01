package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded master request to atomically restore one editor-history scene state. */
public record VttSceneHistoryCommandPayload(
        String requestId, long authorityRevision, String operation, String sceneId,
        String expectedFingerprint, String targetSceneJson
) implements CustomPacketPayload {
    public static final String UNDO = "UNDO";
    public static final String REDO = "REDO";
    public static final int MAX_SCENE_JSON_LENGTH = 4_000_000;

    public static final Type<VttSceneHistoryCommandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "scene_history_command"));

    private static final StreamCodec<ByteBuf, Header> HEADER_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), Header::requestId,
            ByteBufCodecs.VAR_LONG, Header::authorityRevision,
            ByteBufCodecs.stringUtf8(8), Header::operation,
            Header::new);
    private static final StreamCodec<ByteBuf, Body> BODY_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), Body::sceneId,
            ByteBufCodecs.stringUtf8(64), Body::expectedFingerprint,
            ByteBufCodecs.stringUtf8(MAX_SCENE_JSON_LENGTH), Body::targetSceneJson,
            Body::new);
    public static final StreamCodec<ByteBuf, VttSceneHistoryCommandPayload> STREAM_CODEC =
            StreamCodec.composite(
                    HEADER_CODEC, payload -> new Header(payload.requestId(),
                            payload.authorityRevision(), payload.operation()),
                    BODY_CODEC, payload -> new Body(payload.sceneId(),
                            payload.expectedFingerprint(), payload.targetSceneJson()),
                    (header, body) -> new VttSceneHistoryCommandPayload(
                            header.requestId(), header.authorityRevision(), header.operation(),
                            body.sceneId(), body.expectedFingerprint(), body.targetSceneJson()));

    private record Header(String requestId, long authorityRevision, String operation) {}
    private record Body(String sceneId, String expectedFingerprint, String targetSceneJson) {}

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
