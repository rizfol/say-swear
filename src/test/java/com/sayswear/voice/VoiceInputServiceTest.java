package com.sayswear.voice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class VoiceInputServiceTest {
    @Test void convertsSignedLittleEndianPcmWithoutSignExtensionOfTheLowByte() {
        byte[] bytes = {(byte) 0xff, 0, 0, (byte) 0x80, (byte) 0xff, 0x7f, (byte) 0xff, (byte) 0xff};
        assertArrayEquals(new float[]{255 / 32768f, -1, 32767 / 32768f, -1 / 32768f},
                MicrophoneDevice.decodePcm16(bytes, bytes.length));
        assertThrows(IllegalArgumentException.class, () -> MicrophoneDevice.decodePcm16(bytes, 3));
    }

    @Test void callbacksRetainEpochAndUtteranceAndOldStreamsCannotLeakIntoRestart() throws Exception {
        var recognizer = new FakeRecognizer();
        BlockingQueue<String> transcripts = new LinkedBlockingQueue<>();
        BlockingQueue<Long> failures = new LinkedBlockingQueue<>();
        try (var voice = new VoiceInputService(recognizer,
                (text, utterance, epoch) -> transcripts.add(text + ":" + utterance + ":" + epoch),
                (message, epoch) -> failures.add(epoch), FakeMicrophone::new)) {
            voice.start(10);
            Callbacks old = recognizer.next();
            old.partial.accept("left");
            old.endpoint.run();
            old.partial.accept("right");
            assertEquals("left:1:10", transcripts.poll(1, TimeUnit.SECONDS));
            assertEquals("right:2:10", transcripts.poll(1, TimeUnit.SECONDS));
            voice.stop();
            voice.start(11);
            Callbacks current = recognizer.next();
            old.partial.accept("stale");
            old.error.accept("old stream failed");
            assertNull(transcripts.poll());
            assertNull(failures.poll());
            current.partial.accept("up");
            assertEquals("up:1:11", transcripts.poll(1, TimeUnit.SECONDS));
            current.error.accept("recognizer failed");
            assertEquals(11L, failures.poll(1, TimeUnit.SECONDS));
            current.partial.accept("ignored after failure");
            assertNull(transcripts.poll());
        }
    }

    @Test void startupAndStopDoNotWaitForNativeInitialization() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var recognizer = new FakeRecognizer() {
            @Override public void start(Consumer<String> partial, Runnable endpoint, Consumer<String> error) {
                entered.countDown();
                try { release.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                super.start(partial, endpoint, error);
            }
        };
        try (var voice = new VoiceInputService(recognizer, (a, b, c) -> { }, (a, b) -> { }, FakeMicrophone::new)) {
            assertTimeout(Duration.ofMillis(300), () -> voice.start(1));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertTimeout(Duration.ofMillis(300), voice::stop);
            release.countDown();
        } finally { release.countDown(); }
    }

    @Test void asynchronousRecognitionFailureUnblocksCaptureWithoutControllerCleanup() throws Exception {
        var reading = new CountDownLatch(1);
        var stopped = new CountDownLatch(1);
        var recognizer = new FakeRecognizer() {
            @Override public void stop() { stopped.countDown(); }
        };
        var microphone = new FakeMicrophone() {
            @Override public float[] readSamples() {
                reading.countDown();
                return super.readSamples();
            }
        };
        try (var voice = new VoiceInputService(recognizer, (a, b, c) -> { }, (a, b) -> { }, () -> microphone)) {
            voice.start(1);
            Callbacks callbacks = recognizer.next();
            assertTrue(reading.await(1, TimeUnit.SECONDS));
            callbacks.error.accept("recognizer failed asynchronously");
            assertTrue(stopped.await(1, TimeUnit.SECONDS), "Failed capture must release the recognizer itself.");
        }
    }

    @Test void reportsReadyOnlyAfterMicrophoneOpenForTheCurrentSession() throws Exception {
        var opening = new CountDownLatch(1);
        var allowOpen = new CountDownLatch(1);
        var ready = new LinkedBlockingQueue<Long>();
        var recognizer = new FakeRecognizer();
        var microphone = new FakeMicrophone() {
            @Override public void open() {
                opening.countDown();
                try { allowOpen.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        };
        try (var voice = new VoiceInputService(recognizer, (a, b, c) -> { }, (a, b) -> { },
                ready::add, () -> microphone)) {
            voice.start(17);
            assertTrue(opening.await(1, TimeUnit.SECONDS));
            assertNull(ready.poll());
            allowOpen.countDown();
            assertEquals(17L, ready.poll(1, TimeUnit.SECONDS));
        } finally { allowOpen.countDown(); }
    }

    @Test void missingModelFailsBeforeNativeLibraryLoad(@TempDir Path directory) {
        var adapter = new SherpaOnnxAdapter(directory);
        var error = assertThrows(IllegalArgumentException.class,
                () -> adapter.start(text -> { }, () -> { }, message -> { }));
        assertTrue(error.getMessage().contains("Missing ASR model file"));
        adapter.close();
    }

    private record Callbacks(Consumer<String> partial, Runnable endpoint, Consumer<String> error) { }
    private static class FakeRecognizer implements StreamingSpeechRecognizer {
        final BlockingQueue<Callbacks> calls = new LinkedBlockingQueue<>();
        @Override public void start(Consumer<String> partial, Runnable endpoint, Consumer<String> error) {
            calls.add(new Callbacks(partial, endpoint, error));
        }
        @Override public void acceptAudio(float[] samples) { }
        @Override public void stop() { }
        Callbacks next() throws InterruptedException {
            Callbacks call = calls.poll(2, TimeUnit.SECONDS);
            assertNotNull(call);
            return call;
        }
    }
    private static class FakeMicrophone extends MicrophoneDevice {
        private final BlockingQueue<float[]> samples = new LinkedBlockingQueue<>();
        @Override public void open() { }
        @Override public float[] readSamples() {
            try { return samples.take(); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); return new float[0]; }
        }
        @Override public void close() { samples.offer(new float[0]); }
    }
}
