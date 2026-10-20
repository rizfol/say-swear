package com.sayswear.voice;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in native integration using the model's bundled speech recording, never a microphone.
 * The upstream example for test_wavs/0.wav begins "AFTER EARLY NIGHTFALL" and mentions
 * "YELLOW LAMPS". See docs/asr-setup.md for its source and setup instructions.
 */
@Tag("native-asr")
@EnabledIfEnvironmentVariable(named = "SHERPA_MODEL_DIR", matches = ".+")
class SherpaNativeSmokeTest {
    private static final int SAMPLE_RATE = 16_000;
    private static final int CHUNK_SAMPLES = 320; // 20 ms, without real-time sleeps.

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void bundledSpeechProducesChangingPartialsAndAnEndpoint() throws Exception {
        Path modelDirectory = Path.of(System.getenv("SHERPA_MODEL_DIR"));
        Path wave = modelDirectory.resolve("test_wavs/0.wav");
        assertTrue(Files.isRegularFile(wave), "Run scripts/setup-asr.ps1 first: " + wave);
        float[] samples = readPcm16(wave);
        List<String> partials = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        AtomicInteger endpoints = new AtomicInteger();
        AtomicInteger samplesSubmitted = new AtomicInteger();
        AtomicInteger firstPartialAt = new AtomicInteger(-1);

        try (SherpaOnnxAdapter recognizer = new SherpaOnnxAdapter(modelDirectory)) {
            recognizer.start(text -> {
                partials.add(text);
                firstPartialAt.compareAndSet(-1, samplesSubmitted.get());
            }, endpoints::incrementAndGet, errors::add);
            for (int offset = 0; offset < samples.length; offset += CHUNK_SAMPLES) {
                int end = Math.min(samples.length, offset + CHUNK_SAMPLES);
                samplesSubmitted.set(end);
                recognizer.acceptAudio(Arrays.copyOfRange(samples, offset, end));
            }
            // Flush the streaming decoder and exercise the trailing-silence endpoint rule.
            for (int i = 0; i < 3 * SAMPLE_RATE / CHUNK_SAMPLES; i++) {
                recognizer.acceptAudio(new float[CHUNK_SAMPLES]);
            }
        }

        assertTrue(errors.isEmpty(), () -> "ASR error callbacks: " + errors);
        assertTrue(partials.stream().distinct().count() >= 2,
                () -> "Expected changing incremental recognition, got: " + partials);
        assertTrue(firstPartialAt.get() > 0 && firstPartialAt.get() < samples.length,
                "A partial transcript must arrive before the entire recording is submitted");
        String longest = partials.stream().max(Comparator.comparingInt(String::length))
                .orElse("").toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        assertTrue(longest.contains("AFTER EARLY NIGHTFALL"), () -> "Unexpected transcript: " + longest);
        assertTrue(longest.contains("YELLOW LAMPS"), () -> "Unexpected transcript: " + longest);
        assertTrue(endpoints.get() > 0, "Trailing silence must produce an utterance endpoint");
        System.out.printf(Locale.ROOT, "Native ASR smoke: %d partials, %d endpoints, %.2f s audio.%n",
                partials.size(), endpoints.get(), samples.length / (double) SAMPLE_RATE);
    }

    private static float[] readPcm16(Path wave) throws Exception {
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(wave.toFile())) {
            AudioFormat format = audio.getFormat();
            assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding(), "Expected signed PCM WAV");
            assertEquals(1, format.getChannels(), "Expected mono recording");
            assertEquals(SAMPLE_RATE, (int) format.getSampleRate(), "Expected the bundled 16 kHz recording");
            assertEquals(16, format.getSampleSizeInBits(), "Expected 16-bit PCM");
            byte[] pcm = audio.readAllBytes();
            assertTrue(pcm.length > 0 && pcm.length % 2 == 0, "PCM sample data must be nonempty and complete");
            float[] samples = new float[pcm.length / 2];
            for (int i = 0; i < samples.length; i++) {
                int low = pcm[2 * i + (format.isBigEndian() ? 1 : 0)] & 0xff;
                int high = pcm[2 * i + (format.isBigEndian() ? 0 : 1)];
                samples[i] = (short) ((high << 8) | low) / 32768.0f;
            }
            return samples;
        }
    }
}
