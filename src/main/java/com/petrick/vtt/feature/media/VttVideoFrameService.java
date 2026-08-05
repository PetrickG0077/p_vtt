package com.petrick.vtt.feature.media;

import com.mojang.blaze3d.platform.NativeImage;
import com.petrick.vtt.VTT;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.ColorUtil;
import org.jcodec.scale.Transform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client-only MP4 decoder. Decoding and color conversion run off the render thread;
 * the render thread only uploads the newest already-decoded frame.
 */
public final class VttVideoFrameService {
    private static final int TARGET_FPS = 15;
    private static final int MAX_WIDTH = 960;
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "VTT MP4 decoder");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile int generation;
    private static volatile boolean requestedPlaying;
    private static volatile DecodedFrame pendingFrame;
    private static volatile SeekableByteChannel activeChannel;
    private static String loadedPath = "";
    private static ResourceLocation texture;
    private static int width;
    private static int height;
    private static String failure = "";

    private VttVideoFrameService() {}

    public static Frame frame(Path file, boolean playing) {
        if (file == null || !Files.isRegularFile(file)) return Frame.empty();
        String requested = file.toAbsolutePath().normalize().toString();
        if (!requested.equals(loadedPath)) start(file, requested);
        requestedPlaying = playing;
        uploadNewestFrame();
        return texture == null ? Frame.empty() : new Frame(texture, width, height);
    }

    public static String failure() { return failure; }

    public static void clear() {
        generation++;
        requestedPlaying = false;
        pendingFrame = null;
        closeActiveChannel();
        loadedPath = "";
        texture = null;
        width = 0;
        height = 0;
        failure = "";
    }

    private static void start(Path file, String requested) {
        clear();
        loadedPath = requested;
        int taskGeneration = ++generation;
        DECODER.execute(() -> decode(file, requested, taskGeneration));
    }

    private static void decode(Path file, String requested, int taskGeneration) {
        try (SeekableByteChannel channel = NIOUtils.readableChannel(file.toFile())) {
            activeChannel = channel;
            FrameGrab grab = FrameGrab.createFrameGrab(channel);
            publish(grab.getNativeFrame(), taskGeneration);
            long frameMillis = 1_000L / TARGET_FPS;
            while (taskGeneration == generation) {
                if (!requestedPlaying) {
                    Thread.sleep(10L);
                    continue;
                }
                long started = System.currentTimeMillis();
                Picture picture = grab.getNativeFrame();
                if (picture == null) return;
                publish(picture, taskGeneration);
                long remaining = frameMillis - (System.currentTimeMillis() - started);
                if (remaining > 0L) Thread.sleep(remaining);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            if (taskGeneration == generation) {
                failure = "MP4 playback could not be decoded";
                VTT.LOGGER.warn("Failed to decode VTT MP4 show: {}", file, exception);
            }
        } finally {
            activeChannel = null;
        }
    }

    private static void publish(Picture picture, int taskGeneration) {
        if (picture == null || taskGeneration != generation) return;
        pendingFrame = convert(picture);
    }

    private static void uploadNewestFrame() {
        DecodedFrame frame = pendingFrame;
        if (frame == null) return;
        pendingFrame = null;
        NativeImage image = new NativeImage(frame.width(), frame.height(), false);
        int index = 0;
        for (int y = 0; y < frame.height(); y++) {
            for (int x = 0; x < frame.width(); x++) image.setPixelRGBA(x, y, frame.abgr()[index++]);
        }
        width = frame.width();
        height = frame.height();
        texture = Minecraft.getInstance().getTextureManager().register(
                "vtt_show_video/" + Integer.toUnsignedString(loadedPath.hashCode()),
                new DynamicTexture(image));
    }

    private static DecodedFrame convert(Picture source) {
        Transform transform = ColorUtil.getTransform(source.getColor(), ColorSpace.RGB);
        if (transform == null) throw new IllegalArgumentException("Unsupported MP4 color space: " + source.getColor());
        Picture rgb = Picture.create(source.getWidth(), source.getHeight(), ColorSpace.RGB);
        transform.transform(source, rgb);
        int targetWidth = Math.min(source.getWidth(), MAX_WIDTH);
        int targetHeight = Math.max(1, Math.round(source.getHeight() * (targetWidth / (float) source.getWidth())));
        int[] abgr = new int[targetWidth * targetHeight];
        byte[] pixels = rgb.getPlaneData(0);
        for (int y = 0; y < targetHeight; y++) {
            int sourceY = y * source.getHeight() / targetHeight;
            for (int x = 0; x < targetWidth; x++) {
                int sourceX = x * source.getWidth() / targetWidth;
                int offset = (sourceY * source.getWidth() + sourceX) * 3;
                int red = (pixels[offset] + 128) & 0xFF;
                int green = (pixels[offset + 1] + 128) & 0xFF;
                int blue = (pixels[offset + 2] + 128) & 0xFF;
                abgr[y * targetWidth + x] = 0xFF000000 | blue << 16 | green << 8 | red;
            }
        }
        return new DecodedFrame(targetWidth, targetHeight, abgr);
    }

    private static void closeActiveChannel() {
        SeekableByteChannel channel = activeChannel;
        if (channel == null) return;
        try { channel.close(); } catch (Exception ignored) {}
    }

    private record DecodedFrame(int width, int height, int[] abgr) {}
    public record Frame(ResourceLocation texture, int width, int height) {
        private static Frame empty() { return new Frame(null, 0, 0); }
    }
}
