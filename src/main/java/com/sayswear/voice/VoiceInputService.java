package com.sayswear.voice;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/** Owns microphone/ASR lifetime without blocking the application's simulation thread. */
public final class VoiceInputService implements AutoCloseable {
    @FunctionalInterface
    public interface TranscriptListener {
        void onTranscript(String text, long utteranceId, long epoch);
    }

    private final StreamingSpeechRecognizer recognizer;
    private final TranscriptListener transcript;
    private final BiConsumer<String, Long> failure;
    private final LongConsumer ready;
    private final Supplier<MicrophoneDevice> microphones;
    private final ExecutorService capture = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("voice-capture").factory());
    private final ExecutorService closing = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("microphone-close").factory());
    private volatile Session active;
    private boolean closed;

    public VoiceInputService(StreamingSpeechRecognizer recognizer, TranscriptListener transcript,
                             BiConsumer<String, Long> failure) {
        this(recognizer, transcript, failure, epoch -> { });
    }

    public VoiceInputService(StreamingSpeechRecognizer recognizer, TranscriptListener transcript,
                             BiConsumer<String, Long> failure, LongConsumer ready) {
        this(recognizer, transcript, failure, ready, MicrophoneDevice::new);
    }

    VoiceInputService(StreamingSpeechRecognizer recognizer, TranscriptListener transcript,
                      BiConsumer<String, Long> failure, Supplier<MicrophoneDevice> microphones) {
        this(recognizer, transcript, failure, epoch -> { }, microphones);
    }

    VoiceInputService(StreamingSpeechRecognizer recognizer, TranscriptListener transcript,
                      BiConsumer<String, Long> failure, LongConsumer ready, Supplier<MicrophoneDevice> microphones) {
        this.recognizer = Objects.requireNonNull(recognizer, "recognizer");
        this.transcript = Objects.requireNonNull(transcript, "transcript");
        this.failure = Objects.requireNonNull(failure, "failure");
        this.ready = Objects.requireNonNull(ready, "ready");
        this.microphones = Objects.requireNonNull(microphones, "microphones");
    }

    public synchronized void start(long epoch) {
        if (closed) throw new IllegalStateException("Voice input is closed.");
        if (active != null && active.epoch == epoch && !active.cancelled.get()) return;
        stop();
        Session session = new Session(epoch);
        active = session;
        capture.execute(() -> run(session));
    }

    private void run(Session session) {
        try {
            if (!isCurrent(session)) return;
            recognizer.start(text -> partial(session, text), () -> endpoint(session), message -> fail(session, message));
            if (!isCurrent(session)) return;
            session.microphone = microphones.get();
            session.microphone.open();
            if (isCurrent(session)) ready.accept(session.epoch);
            while (isCurrent(session)) {
                float[] samples = session.microphone.readSamples();
                if (!isCurrent(session)) break;
                if (samples.length == 0) throw new IllegalStateException("Microphone capture ended unexpectedly.");
                recognizer.acceptAudio(samples);
            }
        } catch (Exception | LinkageError ex) {
            fail(session, "Voice input failed: " + safeMessage(ex));
        } finally {
            try {
                if (session.microphone != null) session.microphone.close();
            } finally {
                try { recognizer.stop(); }
                finally { synchronized (this) { if (active == session) active = null; } }
            }
        }
    }

    private void partial(Session session, String text) {
        if (isCurrent(session) && text != null && !text.isBlank()) {
            transcript.onTranscript(text.strip(), session.utterance.get(), session.epoch);
        }
    }

    private void endpoint(Session session) {
        if (isCurrent(session)) session.utterance.incrementAndGet();
    }

    private synchronized void fail(Session session, String message) {
        if (isCurrent(session) && session.failed.compareAndSet(false, true)) {
            session.cancelled.set(true);
            closing.execute(() -> { if (session.microphone != null) session.microphone.close(); });
            failure.accept(message == null || message.isBlank() ? "Voice recognition failed." : message, session.epoch);
        }
    }

    private boolean isCurrent(Session session) { return active == session && !session.cancelled.get(); }

    /** Called by a capture source on its worker, never by the UI thread. */
    public void acceptAudio(float[] samples) {
        Session session = active;
        if (session != null && isCurrent(session)) recognizer.acceptAudio(samples);
    }

    public void onPartial(String text) {
        Session session = active;
        if (session != null) partial(session, text);
    }

    public void onEndpoint() {
        Session session = active;
        if (session != null) endpoint(session);
    }

    public synchronized void stop() {
        Session session = active;
        active = null;
        if (session == null) return;
        session.cancelled.set(true);
        closing.execute(() -> { if (session.microphone != null) session.microphone.close(); });
        // The capture worker alone stops/releases native recognition before opening another session.
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        stop();
        closed = true;
        capture.execute(recognizer::close);
        capture.shutdown();
        closing.shutdown();
    }

    private static String safeMessage(Throwable error) {
        if (error instanceof LinkageError) return "sherpa-onnx native library is unavailable for this platform.";
        String message = error.getMessage();
        return message == null || message.isBlank() ? "check microphone access and ASR model files." : message;
    }

    private static final class Session {
        private final long epoch;
        private final AtomicLong utterance = new AtomicLong(1);
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean failed = new AtomicBoolean();
        private volatile MicrophoneDevice microphone;
        private Session(long epoch) { this.epoch = epoch; }
    }
}
