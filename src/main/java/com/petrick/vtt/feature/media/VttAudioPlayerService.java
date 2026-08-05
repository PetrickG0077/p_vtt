package com.petrick.vtt.feature.media;

import com.petrick.vtt.VTT;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.system.libc.LibCStdlib.free;

/** Asynchronous JavaSound player used by the VTT music catalog. */
public final class VttAudioPlayerService implements AutoCloseable {
    private volatile Path track;
    private volatile boolean playing;
    private volatile boolean paused;
    private volatile boolean loop;
    private volatile float trackVolume = 1.0F;
    private volatile float masterVolume = 1.0F;
    private volatile double positionSeconds;
    private volatile double durationSeconds;
    private volatile long generation;
    private volatile long fadeGeneration;
    private volatile float fadeVolume = 1.0F;
    private volatile SourceDataLine line;
    private volatile String status = "Stopped";
    private volatile String error = "";

    public synchronized void play(Path file) {
        play(file, 0.0, false);
    }

    public synchronized void play(Path file, double startSeconds, boolean startPaused) {
        if (file == null) return;
        stop();
        track = file.toAbsolutePath().normalize();
        error = "";
        status = "Opening " + track.getFileName();
        playing = true;
        paused = startPaused;
        long run = ++generation;
        double safeStart = Math.max(0.0, startSeconds);
        Thread.ofVirtual().name("vtt-audio-player").start(() -> run(run, safeStart));
    }

    /** Fades the current track out, swaps it, then fades the new track in. */
    public void transitionTo(Path file, double startSeconds, boolean startPaused) {
        if (file == null) return;
        long fade = ++fadeGeneration;
        Thread.ofVirtual().name("vtt-audio-crossfade").start(() -> {
            if (isPlaying()) fade(fade, fadeVolume, 0.0F, 250L);
            if (fade != fadeGeneration) return;
            synchronized (this) {
                play(file, startSeconds, startPaused);
                fadeVolume = startPaused ? 1.0F : 0.0F;
            }
            if (!startPaused) {
                long fadeIn = ++fadeGeneration;
                fade(fadeIn, 0.0F, 1.0F, 350L);
            }
        });
    }

    /** Stops playback after a short fade instead of cutting the audio abruptly. */
    public void fadeOutAndStop() {
        long fade = ++fadeGeneration;
        Thread.ofVirtual().name("vtt-audio-fade-stop").start(() -> {
            fade(fade, fadeVolume, 0.0F, 300L);
            if (fade == fadeGeneration) stop();
        });
    }

    private void fade(long fade, float from, float to, long durationMillis) {
        int steps = Math.max(1, (int) (durationMillis / 20L));
        for (int step = 1; step <= steps && fade == fadeGeneration; step++) {
            fadeVolume = from + (to - from) * step / steps;
            SourceDataLine current = line;
            if (current != null && current.isOpen()) applyVolume(current);
            try {
                Thread.sleep(20L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public synchronized void togglePause() {
        if (!playing) return;
        paused = !paused;
        status = paused ? "Paused" : "Playing";
        SourceDataLine current = line;
        if (current != null) {
            if (paused) current.stop(); else current.start();
        }
    }

    public synchronized void stop() {
        fadeGeneration++;
        playing = false;
        paused = false;
        generation++;
        SourceDataLine current = line;
        line = null;
        if (current != null) {
            current.stop();
            current.flush();
            current.close();
        }
        positionSeconds = 0.0;
        status = "Stopped";
        fadeVolume = 1.0F;
    }

    public synchronized void seek(double ratio) {
        if (track == null || durationSeconds <= 0.0) return;
        double seconds = Math.max(0.0, Math.min(1.0, ratio)) * durationSeconds;
        boolean resumePaused = paused;
        playing = false;
        generation++;
        SourceDataLine current = line;
        if (current != null) current.close();
        playing = true;
        paused = resumePaused;
        long run = ++generation;
        Thread.ofVirtual().name("vtt-audio-seek").start(() -> run(run, seconds));
    }

    private void run(long run, double startSeconds) {
        do {
            try {
                DecodedAudio decoded = decode(track);
                AudioFormat pcm = decoded.format();
                durationSeconds = decoded.durationSeconds();
                int offset = (int) Math.min(decoded.pcm().length,
                        Math.max(0L, (long) (startSeconds * pcm.getFrameRate())
                                * pcm.getFrameSize()));
                offset -= offset % pcm.getFrameSize();
                SourceDataLine output = AudioSystem.getSourceDataLine(pcm);
                    output.open(pcm);
                    line = output;
                    status = paused ? "Paused" : "Playing";
                    applyVolume(output);
                    if (!paused) output.start();
                    long written = offset;
                    while (run == generation && playing && offset < decoded.pcm().length) {
                        while (paused && run == generation && playing) {
                            Thread.sleep(20L);
                        }
                        if (run != generation || !playing) break;
                        int count = Math.min(32 * 1024, decoded.pcm().length - offset);
                        output.write(decoded.pcm(), offset, count);
                        offset += count;
                        written += count;
                        positionSeconds = written / (pcm.getFrameRate() * pcm.getFrameSize());
                        applyVolume(output);
                    }
                    if (run == generation && playing) output.drain();
                    output.close();
                    line = null;
            } catch (Exception exception) {
                if (run == generation) {
                    playing = false;
                    status = "Playback failed";
                    error = exception.getClass().getSimpleName() + ": "
                            + (exception.getMessage() == null ? "unknown error"
                            : exception.getMessage());
                    VTT.LOGGER.error("Could not play VTT audio {}", track, exception);
                }
                return;
            }
            startSeconds = 0.0;
            positionSeconds = 0.0;
        } while (run == generation && playing && loop);
        if (run == generation) {
            playing = false;
            status = "Stopped";
        }
    }

    private DecodedAudio decode(Path file) throws Exception {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".mp4")) return decodeMp4(file);
        if (name.endsWith(".mp3")) return decodeMp3(file);
        if (name.endsWith(".ogg")) return decodeOgg(file);
        return decodeJavaSound(file);
    }

    private DecodedAudio decodeMp4(Path file) throws Exception {
        try (org.jcodec.common.io.SeekableByteChannel channel =
                     org.jcodec.common.io.NIOUtils.readableChannel(file.toFile())) {
            org.jcodec.containers.mp4.demuxer.MP4Demuxer demuxer =
                    org.jcodec.containers.mp4.demuxer.MP4Demuxer.createMP4Demuxer(channel);
            java.util.List<org.jcodec.common.DemuxerTrack> tracks = demuxer.getAudioTracks();
            if (tracks.isEmpty()) throw new IllegalArgumentException("MP4 has no audio track");
            org.jcodec.common.DemuxerTrack track = tracks.getFirst();
            org.jcodec.common.DemuxerTrackMeta meta = track.getMeta();
            if (meta.getCodec() != org.jcodec.common.Codec.AAC) {
                throw new IllegalArgumentException("Only AAC audio is supported in MP4");
            }
            org.jcodec.codecs.aac.AACDecoder decoder = new org.jcodec.codecs.aac.AACDecoder(
                    meta.getCodecPrivate() == null ? ByteBuffer.allocate(0)
                            : meta.getCodecPrivate().duplicate());
            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            org.jcodec.common.AudioFormat decodedFormat = null;
            org.jcodec.common.model.Packet packet;
            while ((packet = track.nextFrame()) != null) {
                org.jcodec.common.model.AudioBuffer decoded = decoder.decodeFrame(
                        packet.getData(), ByteBuffer.allocate(64 * 1024));
                decodedFormat = decoded.getFormat();
                ByteBuffer data = decoded.getData().duplicate();
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                pcm.write(bytes);
            }
            if (decodedFormat == null || pcm.size() == 0) {
                throw new IllegalArgumentException("MP4 audio track is empty");
            }
            byte[] bytes = pcm.toByteArray();
            AudioFormat format = new AudioFormat(decodedFormat.getSampleRate(),
                    decodedFormat.getSampleSizeInBits(), decodedFormat.getChannels(),
                    decodedFormat.isSigned(), decodedFormat.isBigEndian());
            double duration = bytes.length / (format.getFrameRate() * format.getFrameSize());
            return new DecodedAudio(bytes, format, duration);
        }
    }

    private DecodedAudio decodeMp3(Path file) throws Exception {
        Path wave = Files.createTempFile("vtt_audio_", ".wav");
        try {
            Class<?> converterType = Class.forName("javazoom.jl.converter.Converter");
            Object converter = converterType.getConstructor().newInstance();
            converterType.getMethod("convert", String.class, String.class)
                    .invoke(converter, file.toString(), wave.toString());
            return decodeJavaSound(wave);
        } finally {
            Files.deleteIfExists(wave);
        }
    }

    private DecodedAudio decodeOgg(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length == 0) {
            throw new IllegalArgumentException("OGG/Vorbis file is empty");
        }
        ByteBuffer encoded = BufferUtils.createByteBuffer(bytes.length);
        encoded.put(bytes).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channels = stack.mallocInt(1);
            IntBuffer rate = stack.mallocInt(1);
            ShortBuffer samples = STBVorbis.stb_vorbis_decode_memory(encoded, channels, rate);
            if (samples == null) {
                throw new IllegalArgumentException("OGG/Vorbis decoding failed");
            }
            try {
                int channelCount = channels.get(0);
                int sampleRate = rate.get(0);
                if (channelCount < 1 || channelCount > 2 || sampleRate <= 0) {
                    throw new IllegalArgumentException("Unsupported OGG/Vorbis format: "
                            + channelCount + " channels at " + sampleRate + " Hz");
                }
                byte[] pcm = new byte[Math.multiplyExact(samples.remaining(), Short.BYTES)];
                ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer().put(samples.duplicate());
                return decoded(pcm, sampleRate, channelCount);
            } finally {
                free(samples);
            }
        }
    }

    private DecodedAudio decodeJavaSound(Path file) throws Exception {
        try (AudioInputStream source = AudioSystem.getAudioInputStream(file.toFile())) {
            AudioFormat original = source.getFormat();
            AudioFormat pcmFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    original.getSampleRate(), 16, original.getChannels(),
                    original.getChannels() * 2, original.getSampleRate(), false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(pcmFormat, source)) {
                return decoded(pcm.readAllBytes(), (int) pcmFormat.getSampleRate(),
                        pcmFormat.getChannels());
            }
        }
    }

    private DecodedAudio decoded(byte[] pcm, int rate, int channels) {
        AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
        double duration = pcm.length / (format.getFrameRate() * format.getFrameSize());
        return new DecodedAudio(pcm, format, duration);
    }

    private record DecodedAudio(byte[] pcm, AudioFormat format, double durationSeconds) {}

    private void applyVolume(SourceDataLine output) {
        if (!output.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl gain = (FloatControl) output.getControl(FloatControl.Type.MASTER_GAIN);
        float linear = Math.max(0.0001F,
                Math.min(1.0F, trackVolume * masterVolume * fadeVolume));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(),
                (float) (20.0 * Math.log10(linear)))));
    }

    public Path track() { return track; }
    public boolean isPlaying() { return playing; }
    public boolean isPaused() { return paused; }
    public boolean isLoop() { return loop; }
    public void setLoop(boolean loop) { this.loop = loop; }
    public float trackVolume() { return trackVolume; }
    public void setTrackVolume(float value) {
        trackVolume = clamp(value);
        SourceDataLine current = line;
        if (current != null && current.isOpen()) applyVolume(current);
    }
    public float masterVolume() { return masterVolume; }
    public void setMasterVolume(float value) {
        masterVolume = clamp(value);
        SourceDataLine current = line;
        if (current != null && current.isOpen()) applyVolume(current);
    }
    public double positionSeconds() { return positionSeconds; }
    public double durationSeconds() { return durationSeconds; }
    public String status() { return status; }
    public String error() { return error; }
    private float clamp(float value) { return Math.max(0.0F, Math.min(1.0F, value)); }
    @Override public void close() { stop(); }
}
