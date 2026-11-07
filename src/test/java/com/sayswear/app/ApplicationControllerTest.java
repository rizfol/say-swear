package com.sayswear.app;

import com.sayswear.agent.ActionDecision;
import com.sayswear.agent.ActionType;
import com.sayswear.agent.ControlContext;
import com.sayswear.agent.DecisionModel;
import com.sayswear.agent.TranscriptUpdate;
import com.sayswear.agent.VoiceCommandAgent;
import com.sayswear.game.Direction;
import com.sayswear.game.GameModel;
import com.sayswear.game.GameSnapshot;
import com.sayswear.game.PlayerStatus;
import com.sayswear.game.Player;
import com.sayswear.game.Vector2;
import com.sayswear.view.GameView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import static org.junit.jupiter.api.Assertions.*;

/** Real controller, agent scheduling, control manager, commands and game; only model/I/O are fakes. */
class ApplicationControllerTest {
    private final ControlledModel model = new ControlledModel();
    private final RecordingView view = new RecordingView();
    private final VoiceCommandAgent agent = new VoiceCommandAgent(model, Duration.ofSeconds(10));
    private final ApplicationController controller = new ApplicationController(new GameModel(), view, agent, false);

    @BeforeEach
    void start() throws Exception {
        controller.start();
        flush();
    }

    @AfterEach
    void close() { controller.close(); }

    @Test
    void aLateOlderResponseCannotReplaceTheNewestAcceptedInstruction() throws Exception {
        Call old = command("right");
        Call newer = command("left");
        complete(newer, ActionType.PRESS, Set.of(Direction.LEFT), 0);
        complete(old, ActionType.PRESS, Set.of(Direction.RIGHT), 0);

        assertEquals(Set.of(Direction.LEFT), controller.getSnapshot().heldDirections());
        assertEquals(1, view.messages.stream().filter(message -> message.startsWith("Accepted:")).count());
    }

    @Test
    void fallCleanupInvalidatesInFlightAiAndRecoveryRequiresFreshInput() throws Exception {
        complete(command("right"), ActionType.PRESS, Set.of(Direction.RIGHT), 0);
        controller.advanceForTest(2).get(5, TimeUnit.SECONDS);
        assertTrue(controller.getSnapshot().position().distance(controller.getSnapshot().level().start()) > 10);
        complete(command("up"), ActionType.PRESS, Set.of(Direction.UP), 0);
        Call beforeFall = command("down");
        controller.advanceForTest(0.4).get(5, TimeUnit.SECONDS);
        assertEquals(PlayerStatus.FALLING, controller.getSnapshot().status());
        assertTrue(controller.getSnapshot().heldDirections().isEmpty());
        assertEquals(Vector2.ZERO, controller.getSnapshot().velocity());

        complete(beforeFall, ActionType.PRESS, Set.of(Direction.DOWN), 0);
        controller.advanceForTest(1.0 / 60).get(5, TimeUnit.SECONDS);
        assertEquals(PlayerStatus.RESPAWNING, controller.getSnapshot().status());
        controller.advanceForTest(1.0 / 60).get(5, TimeUnit.SECONDS);
        assertEquals(PlayerStatus.ACTIVE, controller.getSnapshot().status());
        assertEquals(controller.getSnapshot().level().start(), controller.getSnapshot().position());
        assertTrue(controller.getSnapshot().heldDirections().isEmpty());

        complete(command("right"), ActionType.PRESS, Set.of(Direction.RIGHT), 0);
        assertEquals(Set.of(Direction.RIGHT), controller.getSnapshot().heldDirections());
        assertTrue(view.messages.stream().anyMatch(message -> message.contains("You fell")));
    }

    @Test
    void aCurrentModelFailureStopsMovementAndTheNextExplicitTextCommandCanRetry() throws Exception {
        complete(command("right"), ActionType.PRESS, Set.of(Direction.RIGHT), 0);
        Call failing = command("up");
        fail(failing);
        assertTrue(controller.getSnapshot().heldDirections().isEmpty());
        assertEquals(Vector2.ZERO, controller.getSnapshot().velocity());
        assertTrue(view.messages.stream().anyMatch(message -> message.contains("Decision unavailable")));

        complete(command("left"), ActionType.PRESS, Set.of(Direction.LEFT), 0);
        assertEquals(Set.of(Direction.LEFT), controller.getSnapshot().heldDirections());
    }

    @Test
    void anOlderFailureCannotStopANewerAcceptedInstruction() throws Exception {
        complete(command("right"), ActionType.PRESS, Set.of(Direction.RIGHT), 0);
        Call older = command("up");
        Call newer = command("left");
        complete(newer, ActionType.PRESS, Set.of(Direction.LEFT), 0);
        fail(older);

        assertEquals(Set.of(Direction.LEFT), controller.getSnapshot().heldDirections());
        assertFalse(view.messages.stream().anyMatch(message -> message.contains("Decision unavailable")));
    }

    @Test
    void timerExpiryRejectsAnOtherwiseCurrentResponseBasedOnOldControlContext() throws Exception {
        complete(command("a little right"), ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 200);
        Call relative = command("other way");
        assertEquals(Set.of(Direction.RIGHT), relative.context().heldDirections());
        controller.advanceForTest(0.2).get(5, TimeUnit.SECONDS);
        complete(relative, ActionType.SWITCH, Set.of(Direction.LEFT), 0);

        assertTrue(controller.getSnapshot().heldDirections().isEmpty());
        assertTrue(view.messages.stream().anyMatch(message -> message.contains("Controls changed while interpreting")));
    }

    @ParameterizedTest
    @CsvSource({"100,0.01", "100,0.05", "100,0.16", "200,0.01", "200,0.05", "200,0.3",
            "400,0.01", "400,0.05", "400,0.5"})
    void timedMovementDistanceDoesNotDependOnFrameSize(int durationMs, double frameSeconds) throws Exception {
        complete(command("brief right"), ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), durationMs);
        double remaining = durationMs / 1000.0 + 0.1;
        while (remaining > 1e-10) {
            double step = Math.min(frameSeconds, remaining);
            controller.advanceForTest(step).get(5, TimeUnit.SECONDS);
            remaining -= step;
        }

        GameSnapshot state = controller.getSnapshot();
        assertEquals(state.level().start().x() + Player.SPEED * durationMs / 1000.0,
                state.position().x(), 1e-8);
        assertEquals(state.level().start().y(), state.position().y(), 1e-8);
        assertTrue(state.heldDirections().isEmpty());
        assertEquals(Vector2.ZERO, state.velocity());
        assertEquals(state, view.lastRendered);
    }

    @Test
    void expiryAtTheEndOfAFramePublishesTheReleasedSnapshot() throws Exception {
        complete(command("brief right"), ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 100);
        controller.advanceForTest(0.1).get(5, TimeUnit.SECONDS);
        GameSnapshot state = controller.getSnapshot();
        assertEquals(10 + Player.SPEED * 0.1, state.position().x(), 1e-8);
        assertTrue(state.heldDirections().isEmpty());
        assertEquals(Vector2.ZERO, state.velocity());
        assertEquals(state, view.lastRendered);
    }

    @Test
    void aFallBeforeTimedExpiryCancelsTheRemainingHoldAndStaleInterpretation() throws Exception {
        complete(command("brief up"), ActionType.TIMED_PRESS, Set.of(Direction.UP), 400);
        Call beforeFall = command("right");
        controller.advanceForTest(0.5).get(5, TimeUnit.SECONDS);
        assertEquals(PlayerStatus.FALLING, controller.getSnapshot().status());
        assertTrue(controller.getSnapshot().heldDirections().isEmpty());
        complete(beforeFall, ActionType.PRESS, Set.of(Direction.RIGHT), 0);
        assertTrue(controller.getSnapshot().heldDirections().isEmpty());

        controller.advanceForTest(1.0 / 60).get(5, TimeUnit.SECONDS);
        controller.advanceForTest(1.0 / 60).get(5, TimeUnit.SECONDS);
        assertEquals(PlayerStatus.ACTIVE, controller.getSnapshot().status());
        assertEquals(controller.getSnapshot().level().start(), controller.getSnapshot().position());
        complete(command("right"), ActionType.PRESS, Set.of(Direction.RIGHT), 0);
        controller.advanceForTest(0.6).get(5, TimeUnit.SECONDS);
        assertEquals(Set.of(Direction.RIGHT), controller.getSnapshot().heldDirections());
        assertEquals(10 + Player.SPEED * 0.6, controller.getSnapshot().position().x(), 1e-8);
    }

    @Test
    void againSendsStructuredMovementAfterExpiryAndExecutesANewTimedMovement() throws Exception {
        complete(command("a little right"), ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 200);
        controller.advanceForTest(0.1).get(5, TimeUnit.SECONDS);
        controller.advanceForTest(0.1).get(5, TimeUnit.SECONDS);
        assertTrue(controller.getSnapshot().heldDirections().isEmpty());
        Vector2 beforeRepeat = controller.getSnapshot().position();
        assertEquals(controller.getSnapshot().level().start().x() + Player.SPEED * 0.2,
                beforeRepeat.x(), 1e-8);
        Call again = command("again");
        assertTrue(again.context().heldDirections().isEmpty());
        assertEquals("a little right", again.context().previousCommand());
        assertEquals(ActionType.TIMED_PRESS, again.context().lastMovement().type());
        assertEquals(Set.of(Direction.RIGHT), again.context().lastMovement().directions());
        assertEquals(200, again.context().lastMovement().durationMs());
        complete(again, ActionType.REPEAT_LAST, Set.of(), 0);
        controller.advanceForTest(0.1).get(5, TimeUnit.SECONDS);
        assertTrue(controller.getSnapshot().position().x() > beforeRepeat.x());
        controller.advanceForTest(0.1).get(5, TimeUnit.SECONDS);
        assertTrue(controller.getSnapshot().heldDirections().isEmpty());
        assertEquals(beforeRepeat.x() + Player.SPEED * 0.2, controller.getSnapshot().position().x(), 1e-8);
        assertTrue(view.messages.stream().anyMatch(message -> message.contains("REPEAT_LAST TIMED_PRESS [RIGHT] for 200 ms")));
    }

    @Test
    void againAfterRestartHasNoHistoryAndExplainsWhyNothingMoves() throws Exception {
        complete(command("right"), ActionType.PRESS, Set.of(Direction.RIGHT), 0);
        controller.restart();
        flush();
        Call again = command("again");
        assertNull(again.context().lastMovement());
        complete(again, ActionType.REPEAT_LAST, Set.of(), 0);
        assertTrue(controller.getSnapshot().heldDirections().isEmpty());
        assertTrue(view.messages.contains("No previous movement to repeat. Give a direction first."));
    }

    @Test
    void anInvalidCurrentProposalUsesTheSameNeutralFailurePath() throws Exception {
        complete(command("right"), ActionType.PRESS, Set.of(Direction.RIGHT), 0);
        complete(command("conflicting output"), ActionType.PRESS, Set.of(Direction.LEFT, Direction.RIGHT), 0);

        assertTrue(controller.getSnapshot().heldDirections().isEmpty());
        assertEquals(Vector2.ZERO, controller.getSnapshot().velocity());
        assertTrue(view.messages.stream().anyMatch(message -> message.contains("Invalid model decision")));
    }

    @Test
    void pausingCancelsPendingInterpretationAndStopsAnActiveTimedCommand() throws Exception {
        complete(command("a little right"), ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 400);
        Call pending = command("left");
        controller.pauseControls();
        flush();
        complete(pending, ActionType.PRESS, Set.of(Direction.LEFT), 0);
        controller.advanceForTest(0.5).get(5, TimeUnit.SECONDS);

        assertTrue(controller.getSnapshot().heldDirections().isEmpty());
        assertEquals(Vector2.ZERO, controller.getSnapshot().velocity());
        assertEquals(controller.getSnapshot().level().start(), controller.getSnapshot().position());
    }

    @Test
    void restartInvalidatesAnOutstandingResponseAndResetsTheActualGame() throws Exception {
        complete(command("right"), ActionType.PRESS, Set.of(Direction.RIGHT), 0);
        controller.advanceForTest(1).get(5, TimeUnit.SECONDS);
        assertNotEquals(controller.getSnapshot().level().start(), controller.getSnapshot().position());
        Call old = command("left");
        controller.restart();
        flush();
        complete(old, ActionType.PRESS, Set.of(Direction.LEFT), 0);

        assertEquals(PlayerStatus.ACTIVE, controller.getSnapshot().status());
        assertEquals(controller.getSnapshot().level().start(), controller.getSnapshot().position());
        assertTrue(controller.getSnapshot().heldDirections().isEmpty());
    }

    private Call command(String text) throws Exception {
        controller.submitTextCommand(text);
        return model.awaitCall();
    }

    private void complete(Call call, ActionType type, Set<Direction> directions, int durationMs) throws Exception {
        call.result().complete(ActionDecision.semantic(type, directions, durationMs));
        flush();
    }

    private void fail(Call call) throws Exception {
        call.result().completeExceptionally(new IllegalStateException("local model unavailable"));
        flush();
    }

    private void flush() throws Exception { controller.barrier().get(5, TimeUnit.SECONDS); }

    private record Call(TranscriptUpdate update, ControlContext context, TrackedFuture result) { }

    private static final class ControlledModel implements DecisionModel {
        private final BlockingQueue<Call> calls = new LinkedBlockingQueue<>();

        @Override
        public CompletionStage<ActionDecision> decide(TranscriptUpdate update, ControlContext baseContext) {
            TrackedFuture result = new TrackedFuture();
            calls.add(new Call(update, baseContext, result));
            return result;
        }

        Call awaitCall() throws InterruptedException {
            Call call = calls.poll(5, TimeUnit.SECONDS);
            assertNotNull(call, "Agent should dispatch a model request");
            assertTrue(call.result().subscribed.await(5, TimeUnit.SECONDS),
                    "Agent should install its provider completion handler");
            return call;
        }
    }

    /** Handshake removes the race between dispatching a request and completing its test future. */
    private static final class TrackedFuture extends CompletableFuture<ActionDecision> {
        private final CountDownLatch subscribed = new CountDownLatch(1);

        @Override
        public CompletableFuture<ActionDecision> whenComplete(
                BiConsumer<? super ActionDecision, ? super Throwable> action) {
            CompletableFuture<ActionDecision> result = super.whenComplete(action);
            subscribed.countDown();
            return result;
        }
    }

    private static final class RecordingView implements GameView {
        private final List<String> messages = new CopyOnWriteArrayList<>();
        private volatile GameSnapshot lastRendered;
        @Override public void render(GameSnapshot snapshot) { lastRendered = snapshot; }
        @Override public void showMessage(String message) { messages.add(message); }
    }
}
