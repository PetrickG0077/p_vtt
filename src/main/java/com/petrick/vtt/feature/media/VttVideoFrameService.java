package com.petrick.vtt.feature.media;

import com.mojang.blaze3d.platform.NativeImage;
import com.petrick.vtt.VTT;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-only MP4 frame decoder. It advances sequential frames at a modest fixed rate.
 */
public final class VttVideoFrameService {
    private static String loadedPath = "";
    private static ResourceLocation texture;
    private static int width;
    private static int height;
    private static String failure = "";
    private static FrameGrab grab;
    private static SeekableByteChannel channel;
    private static long lastFrameAt;
    private static boolean ended;

    private VttVideoFrameService() {}

    public static synchronized Frame frame(Path file, boolean playing) {
        if (file == null || !Files.isRegularFile(file)) return Frame.empty();
        String requested = file.toAbsolutePath().normalize().toString();
        if (!requested.equals(loadedPath)) load(file, requested);
        if (playing && texture != null && !ended
                && System.currentTimeMillis() - lastFrameAt >= 42L) advance();
        return texture == null ? Frame.empty() : new Frame(texture, width, height);
    }

    public static synchronized String failure() {
        return failure;
    }

    public static synchronized void clear() {
        closeDecoder();
        loadedPath = "";
        texture = null;
        width = 0;
        height = 0;
        failure = "";
    }

    private static void load(Path file, String requested) {
        loadedPath = requested;
        texture = null;
        width = 0;
        height = 0;
        failure = "";
        closeDecoder();
        try {
            channel = NIOUtils.readableChannel(file.toFile());
            grab = FrameGrab.createFrameGrab(channel);
            Picture picture = grab.getNativeFrame();
            if (picture == null) throw new IllegalStateException("MP4 contains no video frames");
            registerFrame(picture, requested);
        } catch (Exception exception) {
            failure = "Could not decode MP4 preview";
            VTT.LOGGER.warn("Failed to decode VTT MP4 show preview: {}", file, exception);
        }
    }

    private static void advance() {
        try {
            Picture picture = grab == null ? null : grab.getNativeFrame();
            if (picture == null) {
                ended = true;
                return;
            }
            registerFrame(picture, loadedPath);
        } catch (Exception exception) {
            ended = true;
            failure = "MP4 playback stopped";
            VTT.LOGGER.warn("Failed to advance VTT MP4 show: {}", loadedPath, exception);
        }
    }

    private static void registerFrame(Picture picture, String name) {
        BufferedImage buffered = AWTUtil.toBufferedImage(picture);
        NativeImage image = toNativeImage(buffered);
        width = image.getWidth();
        height = image.getHeight();
        texture = Minecraft.getInstance().getTextureManager().register(
                "vtt_show_video/" + Integer.toUnsignedString(name.hashCode()),
                new DynamicTexture(image));
        lastFrameAt = System.currentTimeMillis();
    }

    private static void closeDecoder() {
        grab = null;
        if (channel == null) return;
        try {
            channel.close();
        } catch (Exception ignored) {
        }
        channel = null;
        ended = false;
    }

    private static NativeImage toNativeImage(BufferedImage source) {
        NativeImage image = new NativeImage(source.getWidth(), source.getHeight(), false);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int abgr = ((argb >>> 24) & 0xFF) << 24
                        | (argb & 0xFF) << 16
                        | ((argb >>> 8) & 0xFF) << 8
                        | ((argb >>> 16) & 0xFF);
                image.setPixelRGBA(x, y, abgr);
            }
        }
        return image;
    }

    public record Frame(ResourceLocation texture, int width, int height) {
        private static Frame empty() { return new Frame(null, 0, 0); }
    }
}
