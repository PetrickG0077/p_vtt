package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Master request to change server-authoritative scene lifecycle or metadata. */
public record VttSceneCommandPayload(
        String requestId, long authorityRevision, String operation, String targetId,
        String value, String backgroundAssetId, String mapTextureMode)
        implements CustomPacketPayload {
    public static final String CREATE = "CREATE";
    public static final String SWITCH = "SWITCH";
    public static final String SET_BACKGROUND = "SET_BACKGROUND";
    public static final String RENAME = "RENAME";
    public static final String DELETE = "DELETE";
    public static final String DUPLICATE = "DUPLICATE";

    public static final Type<VttSceneCommandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "scene_command"));

    private static final StreamCodec<ByteBuf, RequestHeader> HEADER_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64), RequestHeader::requestId,
                    ByteBufCodecs.VAR_LONG, RequestHeader::authorityRevision,
                    RequestHeader::new);
    private static final StreamCodec<ByteBuf, CommandBody> BODY_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(16), CommandBody::operation,
                    ByteBufCodecs.stringUtf8(128), CommandBody::targetId,
                    ByteBufCodecs.stringUtf8(512), CommandBody::value,
                    ByteBufCodecs.stringUtf8(512), CommandBody::backgroundAssetId,
                    ByteBufCodecs.stringUtf8(16), CommandBody::mapTextureMode,
                    CommandBody::new);
    public static final StreamCodec<ByteBuf, VttSceneCommandPayload> STREAM_CODEC =
            StreamCodec.composite(
                    HEADER_CODEC, payload -> new RequestHeader(
                            payload.requestId(), payload.authorityRevision()),
                    BODY_CODEC, payload -> new CommandBody(
                            payload.operation(), payload.targetId(), payload.value(),
                            payload.backgroundAssetId(), payload.mapTextureMode()),
                    (header, body) -> new VttSceneCommandPayload(
                            header.requestId(), header.authorityRevision(), body.operation(),
                            body.targetId(), body.value(), body.backgroundAssetId(),
                            body.mapTextureMode()));

    private record RequestHeader(String requestId, long authorityRevision) {}
    private record CommandBody(
            String operation, String targetId, String value,
            String backgroundAssetId, String mapTextureMode) {}

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
