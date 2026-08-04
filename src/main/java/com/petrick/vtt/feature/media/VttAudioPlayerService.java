package com.petrick.vtt.feature.media;

import com.petrick.vtt.VTT;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
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
    private volatile SourceDataLine line;
    private volatile String status = "Stopped";
    private volatile String error = "";

    public synchronized void play(Path file) {
        if (file == null) return;
        stop();
        track = file.toAbsolutePath().normalize();
        error = "";
        status = "Opening " + track.getFileName();
        playing = true;
        paused = false;
        long run = ++generation;
        Thread.ofVirtual().name("vtt-audio-player").start(() -> run(run, 0.0));
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
        if (name.endsWith(".mp3")) return decodeMp3(file);
        if (name.endsWith(".ogg")) return decodeOgg(file);
        return decodeJavaSound(file);
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
        ByteBuffer encoded = BufferUtils.createByteBuffer(bytes.length).put(bytes).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channels = stack.mallocInt(1);
            IntBuffer rate = stack.mallocInt(1);
            PointerBuffer output = stack.mallocPointer(1);
            int samplesPerChannel = STBVorbis.stb_vorbis_decode_memory(
                    encoded, channels, rate, output);
            if (samplesPerChannel <= 0 || output.get(0) == MemoryUtil.NULL) {
                throw new IllegalArgumentException("OGG/Vorbis decoding failed");
            }
            int sampleCount = samplesPerChannel * channels.get(0);
            ShortBuffer samples = MemoryUtil.memShortBuffer(output.get(0), sampleCount);
            byte[] pcm = new byte[sampleCount * 2];
            ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples);
            free(samples);
            return decoded(pcm, rate.get(0), channels.get(0));
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
        float linear = Math.max(0.0001F, Math.min(1.0F, trackVolume * masterVolume));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(),
                (float) (20.0 * Math.log10(linear)))));
    }

    public Path track() { return track; }
    public boolean isPlaying() { return playing; }
    public boolean isPaused() { return paused; }
    public boolean isLoop() { return loop; }
    public void setLoop(boolean loop) { this.loop = loop; }
    public float trackVolume() { return trackVolume; }
    public void setTrackVolume(float value) { trackVolume = clamp(value); }
    public float masterVolume() { return masterVolume; }
    public void setMasterVolume(float value) { masterVolume = clamp(value); }
    public double positionSeconds() { return positionSeconds; }
    public double durationSeconds() { return durationSeconds; }
    public String status() { return status; }
    public String error() { return error; }
    private float clamp(float value) { return Math.max(0.0F, Math.min(1.0F, value)); }
    @Override public void close() { stop(); }
}
