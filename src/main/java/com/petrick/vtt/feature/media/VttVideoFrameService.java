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
    private static volatile long requestedPositionMillis;
    private static volatile DecodedFrame pendingFrame;
    private static volatile SeekableByteChannel activeChannel;
    private static volatile boolean loading;
    private static volatile long loadingStartedAt;
    private static volatile long durationMillis;
    private static volatile long seekRequestMillis = -1L;
    private static String loadedPath = "";
    private static ResourceLocation texture;
    private static DynamicTexture dynamicTexture;
    private static int width;
    private static int height;
    private static String failure = "";

    private VttVideoFrameService() {}

    public static Frame frame(Path file, boolean playing, long positionMillis) {
        if (file == null || !Files.isRegularFile(file)) return Frame.empty();
        String requested = file.toAbsolutePath().normalize().toString();
        if (!requested.equals(loadedPath)) start(file, requested);
        requestedPlaying = playing;
        requestedPositionMillis = Math.max(0L, positionMillis);
        uploadNewestFrame();
        return texture == null ? Frame.empty() : new Frame(texture, width, height);
    }

    public static String failure() { return failure; }
    public static boolean isLoading() { return loading; }
    public static long loadingStartedAt() { return loadingStartedAt; }
    public static long durationMillis() { return durationMillis; }
    public static void requestSeek(long positionMillis) {
        seekRequestMillis = Math.max(0L, positionMillis);
    }

    public static void clear() {
        generation++;
        requestedPlaying = false;
        requestedPositionMillis = 0L;
        pendingFrame = null;
        closeActiveChannel();
        loadedPath = "";
        texture = null;
        if (dynamicTexture != null) dynamicTexture.close();
        dynamicTexture = null;
        width = 0;
        height = 0;
        failure = "";
        loading = false;
        loadingStartedAt = 0L;
        durationMillis = 0L;
        seekRequestMillis = -1L;
    }

    private static void start(Path file, String requested) {
        clear();
        loadedPath = requested;
        loading = true;
        loadingStartedAt = System.currentTimeMillis();
        int taskGeneration = ++generation;
        DECODER.execute(() -> decode(file, requested, taskGeneration));
    }

    private static void decode(Path file, String requested, int taskGeneration) {
        try (SeekableByteChannel channel = NIOUtils.readableChannel(file.toFile())) {
            activeChannel = channel;
            FrameGrab grab = FrameGrab.createFrameGrab(channel);
            double totalDurationSeconds = grab.getVideoTrack().getMeta().getTotalDuration();
            int totalFrames = grab.getVideoTrack().getMeta().getTotalFrames();
            double sourceFps = totalDurationSeconds > 0.0 && totalFrames > 0
                    ? totalFrames / totalDurationSeconds : 30.0;
            durationMillis = Math.max(0L, Math.round(totalDurationSeconds * 1_000.0));
            publish(grab.getNativeFrame(), taskGeneration);
            long decodedFrame = 0L;
            long publishedBucket = 0L;
            while (taskGeneration == generation) {
                long requestedSeek = seekRequestMillis;
                if (requestedSeek >= 0L) {
                    seekRequestMillis = -1L;
                    grab.seekToSecondPrecise(requestedSeek / 1_000.0);
                    Picture sought = grab.getNativeFrame();
                    decodedFrame = Math.max(0L,
                            Math.round(requestedSeek * sourceFps / 1_000.0));
                    publishedBucket = requestedSeek * TARGET_FPS / 1_000L;
                    publish(sought, taskGeneration);
                    continue;
                }
                if (!requestedPlaying) {
                    Thread.sleep(10L);
                    continue;
                }
                long requestedMillis = requestedPositionMillis;
                long desiredBucket = requestedMillis * TARGET_FPS / 1_000L;
                if (desiredBucket <= publishedBucket) {
                    Thread.sleep(2L);
                    continue;
                }

                long desiredFrame = Math.max(0L,
                        Math.round(requestedMillis * sourceFps / 1_000.0));
                if (desiredFrame < decodedFrame || desiredFrame - decodedFrame > sourceFps * 2.0) {
                    grab.seekToSecondPrecise(requestedMillis / 1_000.0);
                    decodedFrame = desiredFrame;
                }

                Picture picture = null;
                do {
                    picture = grab.getNativeFrame();
                    if (picture == null) {
                        requestedPlaying = false;
                        break;
                    }
                    decodedFrame++;
                } while (decodedFrame < desiredFrame && taskGeneration == generation);

                if (picture != null) {
                    publish(picture, taskGeneration);
                    publishedBucket = desiredBucket;
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            if (taskGeneration == generation) {
                failure = "MP4 playback could not be decoded";
                loading = false;
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
        if (pendingFrame == frame) pendingFrame = null;
        loading = false;
        NativeImage image = new NativeImage(frame.width(), frame.height(), false);
        int index = 0;
        for (int y = 0; y < frame.height(); y++) {
            for (int x = 0; x < frame.width(); x++) image.setPixelRGBA(x, y, frame.abgr()[index++]);
        }
        width = frame.width();
        height = frame.height();
        if (dynamicTexture == null) {
            dynamicTexture = new DynamicTexture(image);
            texture = Minecraft.getInstance().getTextureManager().register(
                    "vtt_show_video/" + Integer.toUnsignedString(loadedPath.hashCode()),
                    dynamicTexture);
        } else {
            dynamicTexture.setPixels(image);
            dynamicTexture.upload();
        }
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
