package com.sayswear.voice;

import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/** Typed sherpa-onnx Java API integration for the English 2023-06-26 streaming model. */
public final class SherpaOnnxAdapter implements StreamingSpeechRecognizer {
    private final Path modelDir;
    private OnlineRecognizer recognizer;
    private OnlineStream stream;
    private Consumer<String> partial;
    private Consumer<String> error;
    private Runnable endpoint;
    private String lastText = "";

    public SherpaOnnxAdapter(Path modelDir) { this.modelDir = Objects.requireNonNull(modelDir, "modelDir"); }

    @Override
    public synchronized void start(Consumer<String> onPartial, Runnable onEndpoint, Consumer<String> onError) {
        stop();
        partial = Objects.requireNonNull(onPartial, "onPartial");
        endpoint = Objects.requireNonNull(onEndpoint, "onEndpoint");
        error = Objects.requireNonNull(onError, "onError");
        String encoder = required("encoder-epoch-99-avg-1-chunk-16-left-128.onnx");
        String decoder = required("decoder-epoch-99-avg-1-chunk-16-left-128.onnx");
        String joiner = required("joiner-epoch-99-avg-1-chunk-16-left-128.onnx");
        String tokens = required("tokens.txt");
        var transducer = OnlineTransducerModelConfig.builder()
                .setEncoder(encoder).setDecoder(decoder).setJoiner(joiner).build();
        var model = OnlineModelConfig.builder().setTransducer(transducer).setTokens(tokens)
                .setNumThreads(2).setDebug(false).build();
        var config = OnlineRecognizerConfig.builder().setOnlineModelConfig(model)
                .setDecodingMethod("greedy_search").setEnableEndpoint(true).build();
        try {
            recognizer = new OnlineRecognizer(config);
            stream = recognizer.createStream();
        } catch (RuntimeException | LinkageError ex) {
            stop();
            throw ex;
        }
        lastText = "";
    }

    private String required(String filename) {
        Path file = modelDir.resolve(filename);
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new IllegalArgumentException("Missing ASR model file: " + filename);
        }
        return file.toAbsolutePath().toString();
    }

    @Override
    public synchronized void acceptAudio(float[] samples) {
        Objects.requireNonNull(samples, "samples");
        if (recognizer == null || stream == null || samples.length == 0) return;
        try {
            stream.acceptWaveform(samples, MicrophoneDevice.SAMPLE_RATE);
            while (recognizer.isReady(stream)) recognizer.decode(stream);
            String text = recognizer.getResult(stream).getText().strip();
            if (!text.isEmpty() && !text.equals(lastText)) {
                lastText = text;
                partial.accept(text);
            }
            if (recognizer.isEndpoint(stream)) {
                recognizer.reset(stream);
                lastText = "";
                endpoint.run();
            }
        } catch (RuntimeException | LinkageError ex) {
            Consumer<String> callback = error;
            stop();
            if (callback != null) callback.accept("Streaming speech recognition failed; check ASR model/native files.");
        }
    }

    @Override
    public synchronized void stop() {
        if (stream != null) { stream.release(); stream = null; }
        if (recognizer != null) { recognizer.release(); recognizer = null; }
        lastText = "";
    }
}
