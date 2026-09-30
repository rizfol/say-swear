package com.sayswear.voice;

import java.util.function.Consumer;

/** Streaming, 16 kHz mono floating-point PCM recognition boundary. */
public interface StreamingSpeechRecognizer extends AutoCloseable {
    void start(Consumer<String> onPartial, Runnable onEndpoint, Consumer<String> onError);
    void acceptAudio(float[] samples);
    void stop();

    @Override
    default void close() { stop(); }
}
