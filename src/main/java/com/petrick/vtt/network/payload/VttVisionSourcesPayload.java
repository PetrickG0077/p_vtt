package com.petrick.vtt.network.payload;

import com.petrick.vtt.VTT;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.vision.AuthoritativeVisionRegion;
import com.petrick.vtt.feature.tabletop.VttSceneLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Server-selected vision sources for one player in the active scene. */
public record VttVisionSourcesPayload(
        long authorityRevision, long visionRevision, String sceneId,
        boolean maskWhenEmpty, List<AuthoritativeVisionRegion> regions
) implements CustomPacketPayload {
    public static final Type<VttVisionSourcesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VTT.MOD_ID, "vision_sources"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VttVisionSourcesPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public VttVisionSourcesPayload decode(RegistryFriendlyByteBuf buffer) {
                    long authorityRevision = buffer.readVarLong();
                    long visionRevision = buffer.readVarLong();
                    String sceneId = buffer.readUtf(128);
                    boolean maskWhenEmpty = buffer.readBoolean();
                    int regionCount = buffer.readVarInt();
                    if (regionCount < 0 || regionCount > VttSceneLimits.MAX_VISION_SOURCES) {
                        throw new IllegalArgumentException("Invalid VTT vision region count: " + regionCount);
                    }
                    List<AuthoritativeVisionRegion> regions = new ArrayList<>(regionCount);
                    int totalPoints = 0;
                    for (int index = 0; index < regionCount; index++) {
                        String sourceId = buffer.readUtf(128);
                        Vec2d origin = new Vec2d(buffer.readDouble(), buffer.readDouble());
                        double innerRadius = buffer.readDouble();
                        double outerRadius = buffer.readDouble();
                        boolean ownLightEnabled = buffer.readBoolean();
                        int pointCount = buffer.readVarInt();
                        totalPoints += pointCount;
                        if (pointCount < 0
                                || pointCount > VttSceneLimits.MAX_POINTS_PER_VISION_REGION
                                || totalPoints > VttSceneLimits.MAX_TOTAL_VISION_POINTS) {
                            throw new IllegalArgumentException("Invalid VTT visibility polygon size");
                        }
                        List<Vec2d> polygon = new ArrayList<>(pointCount);
                        for (int point = 0; point < pointCount; point++) {
                            polygon.add(new Vec2d(buffer.readDouble(), buffer.readDouble()));
                        }
                        regions.add(new AuthoritativeVisionRegion(
                                sourceId, origin, innerRadius, outerRadius,
                                ownLightEnabled, polygon));
                    }
                    return new VttVisionSourcesPayload(
                            authorityRevision, visionRevision, sceneId,
                            maskWhenEmpty, regions);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, VttVisionSourcesPayload payload) {
                    List<AuthoritativeVisionRegion> regions = payload.regions() == null
                            ? List.of() : payload.regions();
                    if (regions.size() > VttSceneLimits.MAX_VISION_SOURCES) {
                        throw new IllegalArgumentException("Too many VTT vision regions");
                    }
                    buffer.writeVarLong(payload.authorityRevision());
                    buffer.writeVarLong(payload.visionRevision());
                    buffer.writeUtf(payload.sceneId(), 128);
                    buffer.writeBoolean(payload.maskWhenEmpty());
                    buffer.writeVarInt(regions.size());
                    int totalPoints = 0;
                    for (AuthoritativeVisionRegion region : regions) {
                        List<Vec2d> polygon = region.outerPolygon();
                        totalPoints += polygon.size();
                        if (polygon.size() > VttSceneLimits.MAX_POINTS_PER_VISION_REGION
                                || totalPoints > VttSceneLimits.MAX_TOTAL_VISION_POINTS) {
                            throw new IllegalArgumentException("VTT visibility polygons are too large");
                        }
                        buffer.writeUtf(region.sourceObjectId(), 128);
                        buffer.writeDouble(region.origin().x());
                        buffer.writeDouble(region.origin().y());
                        buffer.writeDouble(region.innerRadius());
                        buffer.writeDouble(region.outerRadius());
                        buffer.writeBoolean(region.ownLightEnabled());
                        buffer.writeVarInt(polygon.size());
                        for (Vec2d point : polygon) {
                            buffer.writeDouble(point.x());
                            buffer.writeDouble(point.y());
                        }
                    }
                }
            };

    public VttVisionSourcesPayload {
        regions = regions == null ? List.of() : List.copyOf(regions);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
