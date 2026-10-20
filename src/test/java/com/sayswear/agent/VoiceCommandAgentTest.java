package com.sayswear.agent;

import com.sayswear.game.Direction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class VoiceCommandAgentTest {
    @Test void freezesMovementHistoryWithinAnUtteranceAndRefreshesItForTheNext() throws Exception {
        var model = new ControlledModel();
        var original = new MovementIntent(ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 200);
        var changed = new MovementIntent(ActionType.PRESS, Set.of(Direction.UP), 0);
        try (var agent = new VoiceCommandAgent(model, Duration.ofSeconds(3))) {
            var first = agent.interpret(new TranscriptUpdate("again", 7, 1, 1),
                    new ControlContext(Set.of(), "stop", 1, 2, 1, true, original)).toCompletableFuture();
            Call one = model.next();
            assertEquals(original, one.context.lastMovement());
            one.result.complete(ActionDecision.semantic(ActionType.REPEAT_LAST, Set.of(), 0));
            first.get(1, TimeUnit.SECONDS);
            var second = agent.interpret(new TranscriptUpdate("again please", 7, 2, 1),
                    new ControlContext(Set.of(Direction.UP), "up", 1, 3, 2, true, changed)).toCompletableFuture();
            Call two = model.next();
            assertEquals(original, two.context.lastMovement());
            assertEquals("stop", two.context.previousCommand());
            two.result.complete(ActionDecision.semantic(ActionType.REPEAT_LAST, Set.of(), 0));
            assertEquals(3, second.get(1, TimeUnit.SECONDS).contextRevision());
            var third = agent.interpret(new TranscriptUpdate("again", 8, 3, 1),
                    new ControlContext(Set.of(Direction.UP), "up", 1, 3, 3, true, changed)).toCompletableFuture();
            Call three = model.next();
            assertEquals(changed, three.context.lastMovement());
            three.result.complete(ActionDecision.semantic(ActionType.REPEAT_LAST, Set.of(), 0));
            assertEquals(3, third.get(1, TimeUnit.SECONDS).transcriptVersion());
        }
    }

    @Test void freezesUtteranceContextButStampsCurrentSubmissionMetadata() throws Exception {
        var model = new ControlledModel();
        try (var agent = new VoiceCommandAgent(model, Duration.ofSeconds(3))) {
            var first = agent.interpret(update(1, 7), context(Direction.RIGHT, 2)).toCompletableFuture();
            Call one = model.next();
            one.result.complete(ActionDecision.semantic(ActionType.SWITCH, Set.of(Direction.LEFT), 0));
            first.get(1, TimeUnit.SECONDS);
            var second = agent.interpret(update(2, 7), context(Direction.LEFT, 3)).toCompletableFuture();
            Call two = model.next();
            assertEquals(Set.of(Direction.RIGHT), two.context.heldDirections());
            two.result.complete(new ActionDecision(ActionType.SWITCH, Set.of(Direction.LEFT), 0, 999, 999, 999, 999, "forged"));
            ActionDecision result = second.get(1, TimeUnit.SECONDS);
            assertEquals(2, result.transcriptVersion());
            assertEquals(7, result.utteranceId());
            assertEquals(1, result.epoch());
            assertEquals(3, result.contextRevision());
            assertEquals("other way", result.sourceText());
        }
    }

    @Test void boundsCallsAndReplacesOnlyTheLatestPendingInput() throws Exception {
        var model = new ControlledModel();
        try (var agent = new VoiceCommandAgent(model, Duration.ofSeconds(3))) {
            agent.interpret(update(1, 1), context(Direction.RIGHT, 1));
            Call first = model.next();
            agent.interpret(update(2, 1), context(Direction.RIGHT, 1));
            Call second = model.next();
            var superseded = agent.interpret(update(3, 1), context(Direction.RIGHT, 1)).toCompletableFuture();
            var latest = agent.interpret(update(4, 1), context(Direction.RIGHT, 1)).toCompletableFuture();
            assertTrue(superseded.isCancelled());
            assertNull(model.calls.poll());
            first.result.complete(stop());
            Call fourth = model.next();
            assertEquals(4, fourth.update.version());
            fourth.result.complete(stop());
            assertEquals(4, latest.get(1, TimeUnit.SECONDS).transcriptVersion());
            second.result.complete(stop());
        }
    }

    @Test void queuedDeadlineIncludesWaitAndTimedOutCallsRetainSlots() throws Exception {
        var model = new ControlledModel();
        try (var agent = new VoiceCommandAgent(model, Duration.ofMillis(250))) {
            agent.interpret(update(1, 1), context(Direction.RIGHT, 1));
            Call first = model.next();
            agent.interpret(update(2, 1), context(Direction.RIGHT, 1));
            Call second = model.next();
            var queued = agent.interpret(update(3, 1), context(Direction.RIGHT, 1)).toCompletableFuture();
            ExecutionException failure = assertThrows(ExecutionException.class, () -> queued.get(2, TimeUnit.SECONDS));
            assertInstanceOf(TimeoutException.class, failure.getCause());
            assertFalse(first.result.isCancelled());
            assertFalse(second.result.isCancelled());
            agent.interpret(update(4, 2), context(Direction.LEFT, 2));
            assertNull(model.calls.poll());
            first.result.complete(stop());
            assertEquals(4, model.next().update.version());
        }
    }

    @Test void resetInvalidatesResultsAndClearsFrozenContextWithoutOverbooking() throws Exception {
        var model = new ControlledModel();
        try (var agent = new VoiceCommandAgent(model, Duration.ofSeconds(3))) {
            var stale = agent.interpret(update(1, 1), context(Direction.RIGHT, 1)).toCompletableFuture();
            Call first = model.next();
            agent.reset();
            assertTrue(stale.isCancelled());
            assertFalse(first.result.isCancelled());
            var fresh = agent.interpret(update(2, 1), context(Direction.UP, 5)).toCompletableFuture();
            Call second = model.next();
            assertEquals(Set.of(Direction.UP), second.context.heldDirections());
            first.result.complete(stop());
            assertTrue(stale.isCancelled());
            second.result.complete(stop());
            assertEquals(5, fresh.get(1, TimeUnit.SECONDS).contextRevision());
        }
    }

    private static TranscriptUpdate update(long version, long utterance) { return new TranscriptUpdate("other way", utterance, version, 1); }
    private static ControlContext context(Direction held, long revision) { return new ControlContext(Set.of(held), "previous", 1, revision, 1, true); }
    private static ActionDecision stop() { return ActionDecision.semantic(ActionType.RELEASE_ALL, Set.of(), 0); }

    private record Call(TranscriptUpdate update, ControlContext context, CompletableFuture<ActionDecision> result) { }
    private static final class ControlledModel implements DecisionModel {
        final BlockingQueue<Call> calls = new LinkedBlockingQueue<>();
        @Override public CompletionStage<ActionDecision> decide(TranscriptUpdate update, ControlContext context) {
            var result = new CompletableFuture<ActionDecision>();
            calls.add(new Call(update, context, result));
            return result;
        }
        Call next() throws InterruptedException {
            Call call = calls.poll(2, TimeUnit.SECONDS);
            assertNotNull(call, "Expected a model invocation.");
            return call;
        }
    }
}
