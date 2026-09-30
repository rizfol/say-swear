package com.sayswear.app;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/** Small, explicit launcher configuration; never changes the chosen model on failure. */
public record AppConfig(boolean cli, boolean demo, boolean help, boolean quietCli, URI endpoint, String model,
                        Duration decisionTimeout, Path asrModelDirectory) {
    public static AppConfig parse(String[] arguments) { return parse(arguments, System.getenv()); }

    static AppConfig parse(String[] arguments, Map<String, String> environment) {
        boolean cli = false, demo = false, help = false, quietCli = false;
        String endpoint = environment.getOrDefault("SEMIF_ENDPOINT", "http://127.0.0.1:8765/decide");
        String model = "Qwen/Qwen3.5-4B";
        String timeout = environment.getOrDefault("DECISION_TIMEOUT_MS", "2000");
        String asr = environment.getOrDefault("SHERPA_MODEL_DIR",
                "models/sherpa-onnx-streaming-zipformer-en-2023-06-26");
        for (int i = 0; i < arguments.length; i++) {
            String argument = arguments[i];
            switch (argument) {
                case "--cli" -> cli = true;
                case "--gui" -> cli = false;
                case "--demo" -> demo = true;
                case "--quiet-cli" -> quietCli = true;
                case "--help", "-h" -> help = true;
                case "--endpoint", "--decision-timeout-ms", "--asr-model-dir" -> {
                    if (++i == arguments.length) throw new IllegalArgumentException("Missing value for " + argument);
                    switch (argument) {
                        case "--endpoint" -> endpoint = arguments[i];
                        case "--decision-timeout-ms" -> timeout = arguments[i];
                        case "--asr-model-dir" -> asr = arguments[i];
                    }
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + argument);
            }
        }
        long milliseconds;
        try { milliseconds = Long.parseLong(timeout); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Decision timeout must be an integer"); }
        if (milliseconds < 100 || milliseconds > 30_000)
            throw new IllegalArgumentException("Decision timeout must be between 100 and 30000 ms");
        URI uri = URI.create(endpoint);
        if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Use the local SemIf HTTP endpoint without credentials");
        return new AppConfig(cli, demo, help, quietCli, uri, model, Duration.ofMillis(milliseconds), Path.of(asr));
    }

    public String decisionDescription() {
        return demo ? "DEMO ONLY — limited deterministic parser; AI is disabled"
                : "Local SemIf / OpenJev · Qwen3.5 4B · " + endpoint;
    }
}
