package com.sayswear.voice;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.util.Arrays;
import java.util.Objects;

/** Java Sound capture. Construction does not open or record the microphone. */
public class MicrophoneDevice implements AutoCloseable {
    public static final int SAMPLE_RATE = 16_000;
    private static final int CHUNK_BYTES = 1_600; // 50 ms, 16-bit mono.
    private volatile TargetDataLine line;
    private final byte[] bytes = new byte[CHUNK_BYTES];
    private final LineFactory lines;

    @FunctionalInterface
    interface LineFactory {
        TargetDataLine get(AudioFormat format) throws LineUnavailableException;
    }

    public MicrophoneDevice() {
        this(format -> (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, format)));
    }

    MicrophoneDevice(LineFactory lines) { this.lines = Objects.requireNonNull(lines, "lines"); }

    // Keep native open/start and close ordered: closing a line mid-open can let it
    // reopen afterward. VoiceInputService performs these potentially slow calls on workers.
    public synchronized void open() throws LineUnavailableException {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        TargetDataLine opened = lines.get(format);
        line = opened;
        opened.open(format);
        opened.start();
    }

    public float[] readSamples() {
        TargetDataLine current = line;
        if (current == null || !current.isOpen()) return new float[0];
        int count = current.read(bytes, 0, bytes.length);
        return decodePcm16(bytes, Math.max(0, count));
    }

    static float[] decodePcm16(byte[] pcm, int length) {
        if (length < 0 || length > pcm.length || length % 2 != 0) {
            throw new IllegalArgumentException("PCM input must contain complete 16-bit samples.");
        }
        float[] samples = new float[length / 2];
        for (int i = 0; i < samples.length; i++) {
            short signed = (short) ((pcm[2 * i] & 0xff) | (pcm[2 * i + 1] << 8));
            samples[i] = signed / 32768.0f;
        }
        return samples;
    }

    @Override
    public synchronized void close() {
        TargetDataLine closing = line;
        line = null;
        if (closing != null) {
            closing.stop();
            closing.close(); // Unblocks a concurrent read on the capture worker.
        }
        Arrays.fill(bytes, (byte) 0);
    }
}
