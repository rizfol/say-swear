package com.sayswear.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sayswear.game.Direction;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OpenJevAdapterTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<JsonNode> request = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @AfterEach void stopServer() { if (server != null) server.stop(0); }

    @Test void sendsOnlyControlContextAndMapsTheAllowlistedSemanticChoice() throws Exception {
        URI endpoint = serve(200, response("TIMED_UP_RIGHT_200"));
        try (var adapter = new OpenJevAdapter(endpoint, "Qwen/Qwen3.5-4B", Duration.ofSeconds(2))) {
            var decision = adapter.decide(update(), context()).toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertEquals(ActionType.TIMED_PRESS, decision.type());
            assertEquals(Set.of(Direction.UP, Direction.RIGHT), decision.directions());
            assertEquals(200, decision.durationMs());
            assertEquals(-1, decision.transcriptVersion(), "Only the agent may stamp request metadata.");
            JsonNode body = request.get();
            assertEquals("Qwen/Qwen3.5-4B", body.path("model").asText());
            assertEquals(52, body.path("choices").size());
            assertEquals(5, body.path("state").size());
            assertTrue(body.path("state").path("lastMovement").isNull());
            assertEquals("a little up and right", body.path("state").path("transcript").asText());
            assertFalse(body.toString().contains("epoch"));
            assertFalse(body.toString().contains("position"));
            assertNull(authorization.get());
        }
    }

    @Test void sendsStructuredMovementHistoryAndLeavesReplayToTheJavaCore() throws Exception {
        URI endpoint = serve(200, response("REPEAT_LAST"));
        var history = new MovementIntent(ActionType.TIMED_PRESS, Set.of(Direction.UP, Direction.RIGHT), 200);
        var context = new ControlContext(Set.of(), "stop", 1, 8, 9, true, history);
        try (var adapter = new OpenJevAdapter(endpoint, "Qwen/Qwen3.5-4B", Duration.ofSeconds(2))) {
            var decision = adapter.decide(new TranscriptUpdate("do that again", 3, 9, 1), context)
                    .toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertEquals(ActionType.REPEAT_LAST, decision.type());
            assertEquals(Set.of(), decision.directions());
            assertEquals(0, decision.durationMs());
            assertEquals(-1, decision.transcriptVersion());
            JsonNode state = request.get().path("state");
            assertEquals("stop", state.path("previousCommand").asText());
            assertEquals(JSON.readTree("{\"type\":\"TIMED_PRESS\",\"directions\":[\"UP\",\"RIGHT\"],\"durationMs\":200}"),
                    state.path("lastMovement"));
            assertEquals(3, state.path("lastMovement").size(), "History must contain semantics only.");
        }
    }

    @Test void rejectsUnknownActionsWrongProviderAndMalformedResponses() throws Exception {
        for (String invalid : new String[]{response("PATHFIND_TO_GOAL"), "not json", "null",
                "{\"backend\":\"demo\",\"model\":\"Qwen/Qwen3.5-4B\",\"choice\":\"PRESS_LEFT\"}",
                "{\"backend\":\"semif\",\"model\":\"other-model\",\"choice\":\"PRESS_LEFT\"}"}) {
            URI endpoint = serve(200, invalid);
            try (var adapter = new OpenJevAdapter(endpoint, "Qwen/Qwen3.5-4B", Duration.ofSeconds(2))) {
                assertThrows(ExecutionException.class,
                        () -> adapter.decide(update(), context()).toCompletableFuture().get(3, TimeUnit.SECONDS));
            }
            stopServer();
        }
    }

    @Test void serviceFailureDoesNotEchoProviderBody() throws Exception {
        URI endpoint = serve(503, "private configuration details");
        try (var adapter = new OpenJevAdapter(endpoint, "Qwen/Qwen3.5-4B", Duration.ofSeconds(2))) {
            var error = assertThrows(ExecutionException.class,
                    () -> adapter.decide(update(), context()).toCompletableFuture().get(3, TimeUnit.SECONDS));
            assertEquals("OpenJev returned HTTP 503.", error.getCause().getMessage());
        }
    }

    private URI serve(int status, String response) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/decide", exchange -> {
            request.set(JSON.readTree(exchange.getRequestBody()));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/decide");
    }

    private static String response(String choice) {
        return "{\"backend\":\"semif\",\"model\":\"Qwen/Qwen3.5-4B\",\"choice\":\"" + choice + "\"}";
    }

    private static TranscriptUpdate update() { return new TranscriptUpdate("a little up and right", 2, 4, 1); }
    private static ControlContext context() { return new ControlContext(Set.of(Direction.RIGHT), "right", 1, 3, 4, true); }
}
