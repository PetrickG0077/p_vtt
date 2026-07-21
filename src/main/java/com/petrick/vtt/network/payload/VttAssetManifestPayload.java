package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Server inventory of assets required by one player scope. */
public record VttAssetManifestPayload(
        String syncId, String serverId, boolean replaceScope, List<Entry> entries
) implements CustomPacketPayload {
    public static final int MAX_FILES = 4_096;
    public static final int MAX_CATEGORY_LENGTH = 16;
    public static final int MAX_PATH_LENGTH = 1_024;
    public static final long MAX_FILE_BYTES = 256L * 1024L * 1024L;
    public static final long MAX_MANIFEST_BYTES = 512L * 1024L * 1024L;

    public static final Type<VttAssetManifestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "asset_manifest"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VttAssetManifestPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public VttAssetManifestPayload decode(RegistryFriendlyByteBuf buffer) {
                    String syncId = buffer.readUtf(36);
                    String serverId = buffer.readUtf(36);
                    boolean replaceScope = buffer.readBoolean();
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_FILES) {
                        throw new IllegalArgumentException("Invalid VTT asset manifest size: " + count);
                    }
                    List<Entry> entries = new ArrayList<>(count);
                    long totalBytes = 0L;
                    for (int index = 0; index < count; index++) {
                        Entry entry = new Entry(buffer.readUtf(MAX_CATEGORY_LENGTH),
                                buffer.readUtf(MAX_PATH_LENGTH), buffer.readUtf(64),
                                buffer.readVarLong());
                        totalBytes += entry.size();
                        if (entry.size() < 0L || entry.size() > MAX_FILE_BYTES
                                || totalBytes > MAX_MANIFEST_BYTES) {
                            throw new IllegalArgumentException("Invalid VTT asset manifest bytes");
                        }
                        entries.add(entry);
                    }
                    return new VttAssetManifestPayload(
                            syncId, serverId, replaceScope, entries);
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer, VttAssetManifestPayload payload
                ) {
                    List<Entry> entries = payload.entries() == null ? List.of() : payload.entries();
                    if (entries.size() > MAX_FILES) {
                        throw new IllegalArgumentException("Too many VTT asset manifest entries");
                    }
                    buffer.writeUtf(payload.syncId(), 36);
                    buffer.writeUtf(payload.serverId(), 36);
                    buffer.writeBoolean(payload.replaceScope());
                    buffer.writeVarInt(entries.size());
                    long totalBytes = 0L;
                    for (Entry entry : entries) {
                        totalBytes += entry.size();
                        if (entry.size() < 0L || entry.size() > MAX_FILE_BYTES
                                || totalBytes > MAX_MANIFEST_BYTES) {
                            throw new IllegalArgumentException("Invalid VTT asset manifest bytes");
                        }
                        buffer.writeUtf(entry.category(), MAX_CATEGORY_LENGTH);
                        buffer.writeUtf(entry.relativePath(), MAX_PATH_LENGTH);
                        buffer.writeUtf(entry.sha256(), 64);
                        buffer.writeVarLong(entry.size());
                    }
                }
            };

    public VttAssetManifestPayload {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public record Entry(String category, String relativePath, String sha256, long size) {}

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
