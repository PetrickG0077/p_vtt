package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VttSceneSnapshotPayload(String tabletopJson, String sceneJson) implements CustomPacketPayload {

    public static final Type<VttSceneSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "scene_snapshot")
    );

    public static final StreamCodec<ByteBuf, VttSceneSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            VttSceneSnapshotPayload::tabletopJson,
            ByteBufCodecs.STRING_UTF8,
            VttSceneSnapshotPayload::sceneJson,
            VttSceneSnapshotPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
