package com.sayswear.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sayswear.game.Direction;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/** Loopback HTTP boundary to the project's SemIf (formerly OpenJev) logits service. */
public final class OpenJevAdapter implements DecisionModel {
    public static final URI DEFAULT_ENDPOINT = URI.create("http://127.0.0.1:8765/decide");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, ActionDecision> CHOICES = createChoices();
    private static final String INSTRUCTIONS = """
            Interpret the player's latest movement intention in state.transcript. Select exactly one
            listed action. The transcript may be an unfinished or revised utterance: a correction
            such as 'right, no, left' means SWITCH_LEFT. The held directions and previous command
            describe the context at the START of this utterance, before earlier partials acted.
            Ordinary direction words set the complete desired direction set. 'Other way' reverses
            each initially held axis; without held directions choose NO_ACTION. 'Keep going' means
            KEEP_CURRENT, and 'stop' means RELEASE_ALL. 'A little' means a 200 ms timed action;
            'Again' or 'do that again' means REPEAT_LAST: replay state.lastMovement as a fresh
            action, including its original duration. This differs from keeping active inputs.
            A repeat request is REPEAT_LAST even when lastMovement is null; Java handles missing
            history without moving. The previous command text
            may say stop or keep going; lastMovement is the authoritative repeatable movement.
            'a tiny bit' means 100 ms; a longer brief action means 400 ms. RELEASE removes only
            explicitly named held directions. Perpendicular pairs are allowed; unresolved opposing
            directions are NO_ACTION. Prefer NO_ACTION for incomplete/unclear or unrelated language.
            Never navigate, solve a route, choose a goal, or invent movement. Requests to reach a
            destination or solve the game are NO_ACTION. If movementAllowed is false, choose NO_ACTION.
            """;

    private final URI endpoint;
    private final String model;
    private final Duration timeout;
    private final HttpClient client;
    private final boolean ownsClient;

    public OpenJevAdapter(URI endpoint, String model, Duration timeout) {
        this(endpoint, model, timeout,
                HttpClient.newBuilder().connectTimeout(positive(timeout)).build(), true);
    }

    public OpenJevAdapter(URI endpoint, String model, Duration timeout, HttpClient client) {
        this(endpoint, model, timeout, client, false);
    }

    private OpenJevAdapter(URI endpoint, String model, Duration timeout,
                           HttpClient client, boolean ownsClient) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        if (!("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))
                || endpoint.getHost() == null || endpoint.getUserInfo() != null) {
            throw new IllegalArgumentException("OpenJev endpoint must be an HTTP(S) URL without credentials.");
        }
        this.model = Objects.requireNonNull(model, "model").strip();
        if (this.model.isEmpty()) throw new IllegalArgumentException("OpenJev model name is required.");
        this.timeout = positive(timeout);
        this.client = Objects.requireNonNull(client, "client");
        this.ownsClient = ownsClient;
    }

    @Override
    public CompletionStage<ActionDecision> decide(TranscriptUpdate update, ControlContext baseContext) {
        Objects.requireNonNull(update, "update");
        Objects.requireNonNull(baseContext, "baseContext");
        try {
            var state = new LinkedHashMap<String, Object>();
            state.put("transcript", update.text());
            state.put("heldDirections", baseContext.heldDirections().stream().sorted().map(Enum::name).toList());
            state.put("previousCommand", baseContext.previousCommand());
            state.put("movementAllowed", baseContext.movementAllowed());
            MovementIntent lastMovement = baseContext.lastMovement();
            state.put("lastMovement", lastMovement == null ? null : Map.of(
                    "type", lastMovement.type().name(),
                    "directions", lastMovement.directions().stream().sorted().map(Enum::name).toList(),
                    "durationMs", lastMovement.durationMs()));
            var choices = CHOICES.entrySet().stream().map(entry -> Map.of(
                    "id", entry.getKey(), "type", entry.getValue().type().name(),
                    "directions", entry.getValue().directions().stream().sorted().map(Enum::name).toList(),
                    "durationMs", entry.getValue().durationMs(), "description", describe(entry.getValue()))).toList();
            String body = JSON.writeValueAsString(Map.of("model", model, "state", state,
                    "question", INSTRUCTIONS, "choices", choices, "timeoutMs", Math.max(1, timeout.toMillis())));
            var builder = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            CompletableFuture<HttpResponse<String>> transport = client.sendAsync(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var result = new CompletableFuture<ActionDecision>();
            transport.whenComplete((response, error) -> {
                if (error != null) {
                    Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                    result.completeExceptionally(new IllegalStateException(cause instanceof HttpTimeoutException
                            ? "OpenJev request timed out." : "OpenJev connection failed; check the decision server."));
                    return;
                }
                try {
                    result.complete(parseResponse(response));
                } catch (RuntimeException ex) {
                    result.completeExceptionally(ex);
                }
            });
            result.whenComplete((ignored, error) -> {
                if (result.isCancelled()) transport.cancel(true);
            });
            return result;
        } catch (JsonProcessingException | IllegalArgumentException | IllegalStateException ex) {
            // Provider bodies, request headers, and credentials never appear in user-facing errors.
            return CompletableFuture.failedFuture(new IllegalStateException("Could not create the OpenJev request."));
        }
    }

    private ActionDecision parseResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OpenJev returned HTTP " + response.statusCode() + ".");
        }
        if (response.body() == null || response.body().length() > 65_536) {
            throw new IllegalStateException("OpenJev returned an invalid response size.");
        }
        try {
            JsonNode answer = JSON.readTree(response.body());
            if (!"semif".equals(answer.path("backend").asText())
                    || !model.equals(answer.path("model").asText()) || !answer.path("choice").isTextual()) {
                throw new IllegalStateException("OpenJev response is missing the expected SemIf model and action choice.");
            }
            ActionDecision decision = CHOICES.get(answer.path("choice").textValue());
            if (decision == null) throw new IllegalStateException("OpenJev returned an unknown action choice.");
            return decision;
        } catch (JsonProcessingException | NullPointerException ex) {
            throw new IllegalStateException("OpenJev returned malformed JSON.");
        }
    }

    private static Map<String, ActionDecision> createChoices() {
        var choices = new LinkedHashMap<String, ActionDecision>();
        List<Set<Direction>> sets = List.of(Set.of(Direction.UP), Set.of(Direction.DOWN),
                Set.of(Direction.LEFT), Set.of(Direction.RIGHT), Set.of(Direction.UP, Direction.LEFT),
                Set.of(Direction.UP, Direction.RIGHT), Set.of(Direction.DOWN, Direction.LEFT),
                Set.of(Direction.DOWN, Direction.RIGHT));
        for (Set<Direction> directions : sets) {
            String label = directions.stream().sorted().map(Enum::name).collect(Collectors.joining("_"));
            for (ActionType type : List.of(ActionType.PRESS, ActionType.RELEASE, ActionType.SWITCH)) {
                choices.put(type + "_" + label, ActionDecision.semantic(type, directions, 0));
            }
            for (int duration : List.of(100, 200, 400)) {
                choices.put("TIMED_" + label + "_" + duration,
                        ActionDecision.semantic(ActionType.TIMED_PRESS, directions, duration));
            }
        }
        for (ActionType type : List.of(ActionType.KEEP_CURRENT, ActionType.REPEAT_LAST,
                ActionType.RELEASE_ALL, ActionType.NO_ACTION)) {
            choices.put(type.name(), ActionDecision.semantic(type, Set.of(), 0));
        }
        // SemIf supports 16 options per readout. The bridge factors these into bounded type,
        // vertical/horizontal direction, and duration questions, then returns one allowed label.
        return Collections.unmodifiableMap(choices);
    }

    private static String describe(ActionDecision decision) {
        return switch (decision.type()) {
            case PRESS -> "Set the desired directions to " + decision.directions();
            case SWITCH -> "Correct or reverse movement to " + decision.directions();
            case RELEASE -> "Release only " + decision.directions();
            case TIMED_PRESS -> "Hold only " + decision.directions() + " for " + decision.durationMs() + " milliseconds";
            case KEEP_CURRENT -> "Keep current movement and any existing timer unchanged";
            case REPEAT_LAST -> "Again: replay the last accepted movement as a fresh action with its original duration";
            case RELEASE_ALL -> "Stop: release every held direction";
            case NO_ACTION -> "Unclear, unsupported, incomplete, or autonomous navigation request; no change";
        };
    }

    private static Duration positive(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("A positive request timeout is required.");
        }
        return value;
    }

    @Override
    public void close() {
        if (ownsClient) client.shutdownNow();
    }
}
