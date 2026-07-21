package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Client request containing only missing manifest indices. */
public record VttAssetRequestPayload(
        String syncId, List<Integer> missingIndices
) implements CustomPacketPayload {
    public static final Type<VttAssetRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "asset_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VttAssetRequestPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public VttAssetRequestPayload decode(RegistryFriendlyByteBuf buffer) {
                    String syncId = buffer.readUtf(36);
                    int count = buffer.readVarInt();
                    if (count < 0 || count > VttAssetManifestPayload.MAX_FILES) {
                        throw new IllegalArgumentException("Invalid VTT asset request size: " + count);
                    }
                    List<Integer> indices = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) indices.add(buffer.readVarInt());
                    return new VttAssetRequestPayload(syncId, indices);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, VttAssetRequestPayload payload) {
                    List<Integer> indices = payload.missingIndices() == null
                            ? List.of() : payload.missingIndices();
                    if (indices.size() > VttAssetManifestPayload.MAX_FILES) {
                        throw new IllegalArgumentException("Too many requested VTT assets");
                    }
                    buffer.writeUtf(payload.syncId(), 36);
                    buffer.writeVarInt(indices.size());
                    for (int index : indices) buffer.writeVarInt(index);
                }
            };

    public VttAssetRequestPayload {
        missingIndices = missingIndices == null ? List.of() : List.copyOf(missingIndices);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
