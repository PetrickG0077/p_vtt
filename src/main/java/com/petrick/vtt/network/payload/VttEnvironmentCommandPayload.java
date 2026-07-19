package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A single master-authored environment mutation sent to the server. */
public record VttEnvironmentCommandPayload(
        String operation, String sceneId, String entityType, String entityId, String entityJson)
        implements CustomPacketPayload {
    public static final String UPSERT = "UPSERT";
    public static final String DELETE = "DELETE";
    public static final String WALL = "WALL";
    public static final String DOOR = "DOOR";
    public static final String FOG_HIDDEN = "FOG_HIDDEN";
    public static final String FOG_REVEALED = "FOG_REVEALED";
    public static final String FOG_CONFIG = "FOG_CONFIG";
    public static final String VISION = "VISION";
    public static final int MAX_JSON_LENGTH = 65_536;

    public static final Type<VttEnvironmentCommandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "environment_command")
    );
    public static final StreamCodec<ByteBuf, VttEnvironmentCommandPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(16), VttEnvironmentCommandPayload::operation,
                    ByteBufCodecs.stringUtf8(128), VttEnvironmentCommandPayload::sceneId,
                    ByteBufCodecs.stringUtf8(24), VttEnvironmentCommandPayload::entityType,
                    ByteBufCodecs.stringUtf8(128), VttEnvironmentCommandPayload::entityId,
                    ByteBufCodecs.stringUtf8(MAX_JSON_LENGTH), VttEnvironmentCommandPayload::entityJson,
                    VttEnvironmentCommandPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
