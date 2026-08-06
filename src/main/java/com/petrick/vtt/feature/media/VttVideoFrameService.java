package com.petrick.vtt.feature.media;

import com.mojang.blaze3d.platform.NativeImage;
import com.petrick.vtt.VTT;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.PictureWithMetadata;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

/**
 * Client-only MP4 decoder. Decoding and color conversion run off the render thread;
 * the render thread only uploads the newest already-decoded frame.
 */
public final class VttVideoFrameService {
    private static volatile int targetFps = VttVideoPreferences.Quality.BALANCED.targetFps();
    private static volatile int maxWidth = VttVideoPreferences.Quality.BALANCED.playbackWidth();
    private static volatile int maxPreloadWidth =
            VttVideoPreferences.Quality.BALANCED.preloadWidth();
    private static volatile long preloadMemoryLimitBytes =
            VttVideoPreferences.DEFAULT_CACHE_MEMORY_MB * 1024L * 1024L;
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "VTT MP4 decoder");
        thread.setDaemon(true);
        return thread;
    });
    private static final ExecutorService PRELOADER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "VTT MP4 preloader");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentMap<String, PreloadState> PRELOADS = new ConcurrentHashMap<>();
    private static volatile int generation;
    private static volatile int preloadGeneration;
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
    private static int cachedFrameIndex = -1;
    private static volatile long displayedPositionMillis;
    private static String failure = "";

    private VttVideoFrameService() {}

    public static Frame frame(Path file, boolean playing, long positionMillis) {
        if (file == null || !Files.isRegularFile(file)) return Frame.empty();
        String requested = file.toAbsolutePath().normalize().toString();
        PreloadState preload = PRELOADS.get(requested);
        if (!requested.equals(loadedPath)) {
            if (preload != null && preload.complete && !preload.frames.isEmpty()) {
                startCached(requested, preload);
            } else {
                start(file, requested);
            }
        }
        requestedPlaying = playing;
        requestedPositionMillis = Math.max(0L, positionMillis);
        if (preload != null && preload.complete && !preload.frames.isEmpty()) {
            int index = findCachedFrameIndex(preload.frames, requestedPositionMillis);
            if (index != cachedFrameIndex) {
                cachedFrameIndex = index;
                CachedFrame cached = preload.frames.get(index);
                displayedPositionMillis = cached.timestampMillis();
                pendingFrame = cached.frame();
            }
        }
        uploadNewestFrame();
        return texture == null ? Frame.empty() : new Frame(texture, width, height);
    }

    public static String failure() { return failure; }
    public static boolean isLoading() { return loading; }
    public static long loadingStartedAt() { return loadingStartedAt; }
    public static long durationMillis() { return durationMillis; }
    public static long displayedPositionMillis() { return displayedPositionMillis; }
    public static void requestSeek(long positionMillis) {
        seekRequestMillis = Math.max(0L, positionMillis);
    }

    public static void preload(Path file) {
        if (file == null || !Files.isRegularFile(file)) return;
        String key = preloadKey(file);
        PreloadState current = PRELOADS.get(key);
        if (current != null && (current.complete || current.failure.isBlank())) return;
        PreloadState state = new PreloadState();
        PRELOADS.put(key, state);
        int taskGeneration = preloadGeneration;
        PRELOADER.execute(() -> warmVideo(file, key, state, taskGeneration));
    }

    public static boolean isPreloading(Path file) {
        PreloadState state = PRELOADS.get(preloadKey(file));
        return state != null && !state.complete && state.failure.isBlank();
    }

    public static boolean isPreloaded(Path file) {
        PreloadState state = PRELOADS.get(preloadKey(file));
        return state != null && state.complete;
    }

    public static float preloadProgress(Path file) {
        PreloadState state = PRELOADS.get(preloadKey(file));
        return state == null ? 0.0F : Math.max(0.0F, Math.min(1.0F, state.progress));
    }

    public static String preloadFailure(Path file) {
        PreloadState state = PRELOADS.get(preloadKey(file));
        return state == null ? "" : state.failure;
    }

    public static int preloadMemoryLimitMb() {
        return (int) (preloadMemoryLimitBytes / (1024L * 1024L));
    }

    public static void setPreloadMemoryLimitMb(int value) {
        int safe = VttVideoPreferences.clampCacheMemory(value);
        long bytes = safe * 1024L * 1024L;
        if (bytes == preloadMemoryLimitBytes) return;
        preloadMemoryLimitBytes = bytes;
        resetPlayback();
        preloadGeneration++;
        PRELOADS.clear();
    }

    /** Applies a local playback profile and invalidates frames built for the old profile. */
    public static void setQuality(VttVideoPreferences.Quality quality) {
        VttVideoPreferences.Quality safe = quality == null
                ? VttVideoPreferences.Quality.BALANCED : quality;
        if (targetFps == safe.targetFps() && maxWidth == safe.playbackWidth()
                && maxPreloadWidth == safe.preloadWidth()) return;
        targetFps = safe.targetFps();
        maxWidth = safe.playbackWidth();
        maxPreloadWidth = safe.preloadWidth();
        resetPlayback();
        preloadGeneration++;
        PRELOADS.clear();
    }

    public static void clear() {
        resetPlayback();
        preloadGeneration++;
        PRELOADS.clear();
    }

    private static void resetPlayback() {
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
        cachedFrameIndex = -1;
        displayedPositionMillis = 0L;
        failure = "";
        loading = false;
        loadingStartedAt = 0L;
        durationMillis = 0L;
        seekRequestMillis = -1L;
    }

    private static void start(Path file, String requested) {
        resetPlayback();
        loadedPath = requested;
        loading = true;
        loadingStartedAt = System.currentTimeMillis();
        int taskGeneration = ++generation;
        DECODER.execute(() -> decode(file, requested, taskGeneration));
    }

    private static void startCached(String requested, PreloadState state) {
        resetPlayback();
        loadedPath = requested;
        durationMillis = state.durationMillis;
        loading = true;
        loadingStartedAt = System.currentTimeMillis();
        cachedFrameIndex = 0;
        pendingFrame = state.frames.getFirst().frame();
    }

    private static void warmVideo(
            Path file, String key, PreloadState state, int taskGeneration
    ) {
        try (SeekableByteChannel channel = NIOUtils.readableChannel(file.toFile())) {
            FrameGrab grab = FrameGrab.createFrameGrab(channel);
            int totalFrames = Math.max(1, grab.getVideoTrack().getMeta().getTotalFrames());
            double durationSeconds = Math.max(0.001,
                    grab.getVideoTrack().getMeta().getTotalDuration());
            double sourceFps = totalFrames / durationSeconds;
            int targetFrames = Math.max(1, (int) Math.ceil(durationSeconds * targetFps));
            PictureWithMetadata firstMetadata = grab.getNativeFrameWithMetadata();
            if (firstMetadata == null) throw new IllegalArgumentException("Video has no frames");
            Picture first = firstMetadata.getPicture();
            double aspect = first.getWidth() / (double) Math.max(1, first.getHeight());
            long memoryLimit = preloadMemoryLimitBytes;
            long bytesPerFrame = Math.max(1L, memoryLimit / targetFrames);
            int memoryWidth = (int) Math.floor(Math.sqrt(bytesPerFrame * aspect / 4.0));
            int preloadWidth = Math.max(160,
                    Math.min(maxPreloadWidth, memoryWidth));
            long minimumFrameBytes = Math.max(1L, Math.round(
                    preloadWidth * (preloadWidth / aspect) * 4.0));
            int maximumCachedFrames = Math.max(1,
                    (int) Math.min(Integer.MAX_VALUE, memoryLimit / minimumFrameBytes));
            int preloadFps = Math.max(2, Math.min(targetFps,
                    (int) Math.floor(maximumCachedFrames / durationSeconds)));
            targetFrames = Math.max(1, (int) Math.ceil(durationSeconds * preloadFps));
            List<CachedFrame> frames = new ArrayList<>(targetFrames);
            frames.add(new CachedFrame(Math.max(0L,
                    Math.round(firstMetadata.getTimestamp() * 1_000.0)),
                    convert(first, preloadWidth)));
            int decoded = 0;
            int nextTargetFrame = Math.max(1,
                    (int) Math.round(sourceFps / preloadFps));
            PictureWithMetadata metadata;
            while (taskGeneration == preloadGeneration
                    && (metadata = grab.getNativeFrameWithMetadata()) != null) {
                decoded++;
                if (decoded >= nextTargetFrame) {
                    frames.add(new CachedFrame(Math.max(0L,
                            Math.round(metadata.getTimestamp() * 1_000.0)),
                            convert(metadata.getPicture(), preloadWidth)));
                    nextTargetFrame = Math.max(decoded + 1,
                            (int) Math.round(frames.size() * sourceFps / preloadFps));
                }
                state.progress = Math.min(0.999F, decoded / (float) totalFrames);
            }
            if (taskGeneration != preloadGeneration) return;
            frames.sort(Comparator.comparingLong(CachedFrame::timestampMillis));
            state.frames = List.copyOf(frames);
            state.durationMillis = Math.round(durationSeconds * 1_000.0);
            state.framesPerSecond = preloadFps;
            state.progress = 1.0F;
            state.complete = true;
            VTT.LOGGER.info("Preloaded VTT video {}: {} frames at {} FPS, {}x{}",
                    key, frames.size(), preloadFps,
                    frames.getFirst().frame().width(), frames.getFirst().frame().height());
        } catch (Exception exception) {
            state.failure = "Preload failed";
            VTT.LOGGER.warn("Failed to preload VTT MP4 show: {}", file, exception);
        }
    }

    private static String preloadKey(Path file) {
        return file == null ? "" : file.toAbsolutePath().normalize().toString();
    }

    private static int findCachedFrameIndex(List<CachedFrame> frames, long positionMillis) {
        int low = 0;
        int high = frames.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (frames.get(middle).timestampMillis() <= positionMillis) {
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return Math.max(0, Math.min(frames.size() - 1, high));
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
                    publishedBucket = requestedSeek * targetFps / 1_000L;
                    publish(sought, taskGeneration);
                    continue;
                }
                if (!requestedPlaying) {
                    Thread.sleep(10L);
                    continue;
                }
                long requestedMillis = requestedPositionMillis;
                long desiredBucket = requestedMillis * targetFps / 1_000L;
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
        width = frame.width();
        height = frame.height();
        if (dynamicTexture == null) {
            NativeImage image = new NativeImage(frame.width(), frame.height(), false);
            copyPixels(frame, image);
            dynamicTexture = new DynamicTexture(image);
            texture = Minecraft.getInstance().getTextureManager().register(
                    "vtt_show_video/" + Integer.toUnsignedString(loadedPath.hashCode()),
                    dynamicTexture);
        } else {
            NativeImage image = dynamicTexture.getPixels();
            if (image == null || image.getWidth() != frame.width()
                    || image.getHeight() != frame.height()) {
                dynamicTexture.close();
                image = new NativeImage(frame.width(), frame.height(), false);
                copyPixels(frame, image);
                dynamicTexture = new DynamicTexture(image);
                texture = Minecraft.getInstance().getTextureManager().register(
                        "vtt_show_video/" + Integer.toUnsignedString(loadedPath.hashCode()),
                        dynamicTexture);
                return;
            }
            copyPixels(frame, image);
            dynamicTexture.upload();
        }
    }

    private static void copyPixels(DecodedFrame frame, NativeImage image) {
        int index = 0;
        for (int y = 0; y < frame.height(); y++) {
            for (int x = 0; x < frame.width(); x++) {
                image.setPixelRGBA(x, y, frame.abgr()[index++]);
            }
        }
    }

    private static DecodedFrame convert(Picture source) {
        return convert(source, maxWidth);
    }

    private static DecodedFrame convert(Picture source, int maximumWidth) {
        Transform transform = ColorUtil.getTransform(source.getColor(), ColorSpace.RGB);
        if (transform == null) throw new IllegalArgumentException("Unsupported MP4 color space: " + source.getColor());
        Picture rgb = Picture.create(source.getWidth(), source.getHeight(), ColorSpace.RGB);
        transform.transform(source, rgb);
        int targetWidth = Math.min(source.getWidth(), maximumWidth);
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
    private record CachedFrame(long timestampMillis, DecodedFrame frame) {}
    private static final class PreloadState {
        private volatile float progress;
        private volatile boolean complete;
        private volatile String failure = "";
        private volatile long durationMillis;
        private volatile int framesPerSecond = targetFps;
        private volatile List<CachedFrame> frames = List.of();
    }
    public record Frame(ResourceLocation texture, int width, int height) {
        private static Frame empty() { return new Frame(null, 0, 0); }
    }
}
