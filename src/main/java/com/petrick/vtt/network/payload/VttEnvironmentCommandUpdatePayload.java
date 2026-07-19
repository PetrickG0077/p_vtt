package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-confirmed environment mutation broadcast to every client. */
public record VttEnvironmentCommandUpdatePayload(
        String operation, String sceneId, String entityType, String entityId, String entityJson)
        implements CustomPacketPayload {
    public static final Type<VttEnvironmentCommandUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "environment_command_update")
    );
    public static final StreamCodec<ByteBuf, VttEnvironmentCommandUpdatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(16), VttEnvironmentCommandUpdatePayload::operation,
                    ByteBufCodecs.stringUtf8(128), VttEnvironmentCommandUpdatePayload::sceneId,
                    ByteBufCodecs.stringUtf8(24), VttEnvironmentCommandUpdatePayload::entityType,
                    ByteBufCodecs.stringUtf8(128), VttEnvironmentCommandUpdatePayload::entityId,
                    ByteBufCodecs.stringUtf8(VttEnvironmentCommandPayload.MAX_JSON_LENGTH),
                    VttEnvironmentCommandUpdatePayload::entityJson,
                    VttEnvironmentCommandUpdatePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
