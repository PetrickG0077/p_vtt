package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Commits a fully received scene snapshot transfer. */
public record VttSceneSnapshotCompletePayload(String transferId) implements CustomPacketPayload {
    public static final Type<VttSceneSnapshotCompletePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "scene_snapshot_complete"));

    public static final StreamCodec<ByteBuf, VttSceneSnapshotCompletePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(VttSceneSnapshotStartPayload.MAX_TRANSFER_ID_LENGTH),
                    VttSceneSnapshotCompletePayload::transferId,
                    VttSceneSnapshotCompletePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
