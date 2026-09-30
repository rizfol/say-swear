package com.sayswear.agent;

import com.sayswear.game.Direction;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit local-model smoke check; never opens a microphone or requests model downloads. */
@Tag("live-semif")
@EnabledIfEnvironmentVariable(named = "SAY_SWEAR_LIVE_SEMIF", matches = "1")
class LiveSemIfTest {
    @Test void distinguishesRepeatingRememberedMovementFromKeepingCurrentInputs() throws Exception {
        URI endpoint = URI.create(System.getenv().getOrDefault("SEMIF_ENDPOINT", OpenJevAdapter.DEFAULT_ENDPOINT.toString()));
        assertTrue(Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(endpoint.getHost()));
        var timedRight = new MovementIntent(ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 200);
        var timedDiagonal = new MovementIntent(ActionType.TIMED_PRESS, Set.of(Direction.UP, Direction.RIGHT), 100);
        var pressedUp = new MovementIntent(ActionType.PRESS, Set.of(Direction.UP), 0);
        var switchedLeft = new MovementIntent(ActionType.SWITCH, Set.of(Direction.LEFT), 0);
        List<RepeatCase> cases = List.of(
                new RepeatCase("again", Set.of(), "a little right", timedRight, ActionType.REPEAT_LAST),
                new RepeatCase("do that again", Set.of(), "stop", timedDiagonal, ActionType.REPEAT_LAST),
                new RepeatCase("again", Set.of(Direction.UP), "keep going", pressedUp, ActionType.REPEAT_LAST),
                new RepeatCase("do that again", Set.of(Direction.LEFT), "other way", switchedLeft, ActionType.REPEAT_LAST),
                new RepeatCase("again", Set.of(), "", null, ActionType.REPEAT_LAST),
                new RepeatCase("do that again", Set.of(), "stop", null, ActionType.REPEAT_LAST),
                new RepeatCase("keep going", Set.of(), "a little right", timedRight, ActionType.KEEP_CURRENT),
                new RepeatCase("stop", Set.of(Direction.UP), "up", pressedUp, ActionType.RELEASE_ALL));
        Duration timeout = diagnosticTimeout();
        try (var model = new OpenJevAdapter(endpoint, "Qwen/Qwen3.5-4B", timeout)) {
            long version = 0;
            for (RepeatCase example : cases) {
                var update = new TranscriptUpdate(example.text, ++version, version, 1);
                var context = new ControlContext(example.held, example.previous, 1, 1, version, true, example.history);
                long started = System.nanoTime();
                ActionDecision result = model.decide(update, context).toCompletableFuture()
                        .get(timeout.toMillis() + 1000, TimeUnit.MILLISECONDS);
                System.out.printf("SemIf repeat | %s | previous=%s history=%s | %s | %.1fms%n",
                        example.text, example.previous, example.history, result.type(),
                        (System.nanoTime() - started) / 1_000_000.0);
                assertAll(example.text + " with " + example.history,
                        () -> assertEquals(example.type, result.type()),
                        () -> assertTrue(result.directions().isEmpty()),
                        () -> assertEquals(0, result.durationMs()));
            }
        }
    }

    @Test void measuresActualAgentBurstWithItsEndToEndDeadline() throws Exception {
        URI endpoint = URI.create(System.getenv().getOrDefault("SEMIF_ENDPOINT", OpenJevAdapter.DEFAULT_ENDPOINT.toString()));
        assertTrue(Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(endpoint.getHost()));
        long timeoutMs = Long.parseLong(System.getenv().getOrDefault("SAY_SWEAR_BURST_TIMEOUT_MS", "2000"));
        Duration timeout = Duration.ofMillis(timeoutMs);
        String[] partials = {"right", "right no", "right no left", "right no left please"};
        try (var agent = new VoiceCommandAgent(new OpenJevAdapter(endpoint, "Qwen/Qwen3.5-4B", timeout), timeout)) {
            CompletableFuture<ActionDecision> latest = null;
            for (int i = 0; i < partials.length; i++) {
                final String text = partials[i];
                long version = i + 1;
                long started = System.nanoTime();
                latest = agent.interpret(new TranscriptUpdate(text, 1, version, 1),
                        new ControlContext(Set.of(), "", 1, 1, version, true)).toCompletableFuture();
                latest.whenComplete((result, error) -> System.out.printf(
                        "SemIf burst deadline=%dms | %s | %s | %.1fms%n", timeoutMs, text,
                        error == null ? result.type() + " " + result.directions() : error.getClass().getSimpleName(),
                        (System.nanoTime() - started) / 1_000_000.0));
                if (i + 1 < partials.length) Thread.sleep(180);
            }
            try {
                ActionDecision result = latest.get(timeoutMs + 2000, TimeUnit.MILLISECONDS);
                assertEquals(4, result.transcriptVersion());
                assertEquals(Set.of(Direction.LEFT), result.directions());
                assertTrue(Set.of(ActionType.PRESS, ActionType.SWITCH).contains(result.type()));
                System.out.println("SemIf burst latest: ACCEPTED within configured end-to-end budget.");
            } catch (ExecutionException failure) {
                assertInstanceOf(TimeoutException.class, failure.getCause());
                System.out.println("SemIf burst latest: FAILED CLOSED at the configured deadline; no decision returned.");
                assertTrue(timeoutMs < 2000,
                        "The default two-second budget must accept the latest correction in this smoke check.");
            }
        }
    }

    @Test void interpretsRepresentativeInstructionsThroughActualLocalSemIf() throws Exception {
        String configured = System.getenv().getOrDefault("SEMIF_ENDPOINT", OpenJevAdapter.DEFAULT_ENDPOINT.toString());
        URI endpoint = URI.create(configured);
        assertTrue(Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(endpoint.getHost()),
                "Live smoke test is intentionally limited to a local inference service.");
        List<Case> cases = List.of(
                new Case("left", Set.of(), ActionType.PRESS, Set.of(Direction.LEFT), 0),
                new Case("right", Set.of(), ActionType.PRESS, Set.of(Direction.RIGHT), 0),
                new Case("up", Set.of(), ActionType.PRESS, Set.of(Direction.UP), 0),
                new Case("down", Set.of(), ActionType.PRESS, Set.of(Direction.DOWN), 0),
                new Case("up and left", Set.of(), ActionType.PRESS, Set.of(Direction.UP, Direction.LEFT), 0),
                new Case("up and right", Set.of(), ActionType.PRESS, Set.of(Direction.UP, Direction.RIGHT), 0),
                new Case("down and left", Set.of(), ActionType.PRESS, Set.of(Direction.DOWN, Direction.LEFT), 0),
                new Case("down and right", Set.of(), ActionType.PRESS, Set.of(Direction.DOWN, Direction.RIGHT), 0),
                new Case("a little left", Set.of(), ActionType.TIMED_PRESS, Set.of(Direction.LEFT), 200),
                new Case("a little right", Set.of(), ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 200),
                new Case("a little up", Set.of(), ActionType.TIMED_PRESS, Set.of(Direction.UP), 200),
                new Case("a little down", Set.of(), ActionType.TIMED_PRESS, Set.of(Direction.DOWN), 200),
                new Case("other way", Set.of(Direction.RIGHT), ActionType.SWITCH, Set.of(Direction.LEFT), 0),
                new Case("other way", Set.of(Direction.UP, Direction.RIGHT), ActionType.SWITCH, Set.of(Direction.DOWN, Direction.LEFT), 0),
                new Case("other way", Set.of(), ActionType.NO_ACTION, Set.of(), 0),
                new Case("keep going", Set.of(Direction.UP), ActionType.KEEP_CURRENT, Set.of(), 0),
                new Case("right, no, left", Set.of(Direction.RIGHT), ActionType.SWITCH, Set.of(Direction.LEFT), 0),
                new Case("stop", Set.of(Direction.RIGHT), ActionType.RELEASE_ALL, Set.of(), 0),
                new Case("solve the level and navigate to the goal", Set.of(), ActionType.NO_ACTION, Set.of(), 0),
                new Case("banana soup", Set.of(), ActionType.NO_ACTION, Set.of(), 0));
        Duration timeout = diagnosticTimeout();
        try (var model = new OpenJevAdapter(endpoint, "Qwen/Qwen3.5-4B", timeout)) {
            long version = 0;
            for (Case example : cases) {
                var update = new TranscriptUpdate(example.text, ++version, version, 1);
                var context = new ControlContext(example.held, "", 1, 1, version, true);
                long started = System.nanoTime();
                ActionDecision result = model.decide(update, context).toCompletableFuture()
                        .get(timeout.toMillis() + 1000, TimeUnit.MILLISECONDS);
                double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
                System.out.printf("SemIf Qwen3.5 4B | %s | %s %s %dms | %.1fms%n",
                        example.text, result.type(), result.directions(), result.durationMs(), elapsedMs);
                assertAll(example.text,
                        () -> assertTrue(example.type == result.type()
                                        || Set.of(ActionType.PRESS, ActionType.SWITCH).contains(example.type)
                                        && Set.of(ActionType.PRESS, ActionType.SWITCH).contains(result.type()),
                                "Expected action intent " + example.type + ", got " + result.type()),
                        () -> assertEquals(example.directions, result.directions()),
                        () -> assertEquals(example.durationMs, result.durationMs()));
            }
        }
    }

    private static Duration diagnosticTimeout() {
        return Duration.ofMillis(Long.parseLong(System.getenv().getOrDefault("SEMIF_DIAGNOSTIC_TIMEOUT_MS", "15000")));
    }

    private record RepeatCase(String text, Set<Direction> held, String previous,
                              MovementIntent history, ActionType type) { }

    private record Case(String text, Set<Direction> held, ActionType type,
                        Set<Direction> directions, int durationMs) { }
}
