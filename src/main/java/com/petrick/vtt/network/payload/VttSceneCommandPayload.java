package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Master request to create a scene or switch the server's active scene. */
public record VttSceneCommandPayload(String operation, String value) implements CustomPacketPayload {
    public static final String CREATE = "CREATE";
    public static final String SWITCH = "SWITCH";

    public static final Type<VttSceneCommandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "scene_command"));

    public static final StreamCodec<ByteBuf, VttSceneCommandPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, VttSceneCommandPayload::operation,
            ByteBufCodecs.STRING_UTF8, VttSceneCommandPayload::value,
            VttSceneCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
