package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Master request to change server-authoritative scene lifecycle or metadata. */
public record VttSceneCommandPayload(
        long authorityRevision, String operation, String targetId, String value)
        implements CustomPacketPayload {
    public static final String CREATE = "CREATE";
    public static final String SWITCH = "SWITCH";
    public static final String SET_BACKGROUND = "SET_BACKGROUND";
    public static final String RENAME = "RENAME";
    public static final String DELETE = "DELETE";

    public static final Type<VttSceneCommandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "scene_command"));

    public static final StreamCodec<ByteBuf, VttSceneCommandPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, VttSceneCommandPayload::authorityRevision,
            ByteBufCodecs.STRING_UTF8, VttSceneCommandPayload::operation,
            ByteBufCodecs.STRING_UTF8, VttSceneCommandPayload::targetId,
            ByteBufCodecs.STRING_UTF8, VttSceneCommandPayload::value,
            VttSceneCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
