package com.petrick.vtt.feature.media;

import com.petrick.vtt.VTT;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.nio.file.Path;

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

    public synchronized void play(Path file) {
        if (file == null) return;
        stop();
        track = file.toAbsolutePath().normalize();
        playing = true;
        paused = false;
        long run = ++generation;
        Thread.ofVirtual().name("vtt-audio-player").start(() -> run(run, 0.0));
    }

    public synchronized void togglePause() {
        if (!playing) return;
        paused = !paused;
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
            try (AudioInputStream encoded = AudioSystem.getAudioInputStream(track.toFile())) {
                AudioFormat source = encoded.getFormat();
                AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        source.getSampleRate(), 16, source.getChannels(),
                        source.getChannels() * 2, source.getSampleRate(), false);
                try (AudioInputStream decoded = AudioSystem.getAudioInputStream(pcm, encoded)) {
                    durationSeconds = duration(decoded, pcm);
                    if (durationSeconds <= 0.0) durationSeconds = metadataDuration();
                    long skip = (long) (startSeconds * pcm.getFrameRate()) * pcm.getFrameSize();
                    long skipped = 0L;
                    while (skipped < skip) {
                        long amount = decoded.skip(skip - skipped);
                        if (amount <= 0L) break;
                        skipped += amount;
                    }
                    SourceDataLine output = AudioSystem.getSourceDataLine(pcm);
                    output.open(pcm);
                    line = output;
                    applyVolume(output);
                    if (!paused) output.start();
                    byte[] buffer = new byte[32 * 1024];
                    long written = skipped;
                    int count;
                    while (run == generation && playing
                            && (count = decoded.read(buffer)) >= 0) {
                        while (paused && run == generation && playing) {
                            Thread.sleep(20L);
                        }
                        if (run != generation || !playing) break;
                        output.write(buffer, 0, count);
                        written += count;
                        positionSeconds = written / (pcm.getFrameRate() * pcm.getFrameSize());
                        applyVolume(output);
                    }
                    if (run == generation && playing) output.drain();
                    output.close();
                    line = null;
                }
            } catch (Exception exception) {
                if (run == generation) {
                    playing = false;
                    VTT.LOGGER.error("Could not play VTT audio {}", track, exception);
                }
                return;
            }
            startSeconds = 0.0;
            positionSeconds = 0.0;
        } while (run == generation && playing && loop);
        if (run == generation) playing = false;
    }

    private double duration(AudioInputStream stream, AudioFormat format) {
        return stream.getFrameLength() > 0 && stream.getFrameLength() != AudioSystem.NOT_SPECIFIED
                ? stream.getFrameLength() / format.getFrameRate() : 0.0;
    }

    private double metadataDuration() {
        try {
            Object micros = AudioSystem.getAudioFileFormat(track.toFile())
                    .properties().get("duration");
            return micros instanceof Number number ? number.doubleValue() / 1_000_000.0 : 0.0;
        } catch (Exception ignored) {
            return 0.0;
        }
    }

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
    private float clamp(float value) { return Math.max(0.0F, Math.min(1.0F, value)); }
    @Override public void close() { stop(); }
}
