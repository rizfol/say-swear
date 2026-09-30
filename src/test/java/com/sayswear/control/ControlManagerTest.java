package com.sayswear.control;

import com.sayswear.agent.ActionDecision;
import com.sayswear.agent.ActionType;
import com.sayswear.agent.ControlContext;
import com.sayswear.agent.MovementIntent;
import com.sayswear.game.Direction;
import com.sayswear.game.GameEventType;
import com.sayswear.game.GameModel;
import com.sayswear.game.PlayerStatus;
import com.sayswear.game.Vector2;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ControlManagerTest {
    private final GameModel game = new GameModel();
    private final ActionExecutor executor = new ActionExecutor(game);
    private final ControlManager controls = new ControlManager(game, executor);
    private long nextVersion;

    @Test
    void acceptedActionChangesAuthoritativeInputsAndCommandMemory() {
        ActionDecision right = request(ActionType.PRESS, Set.of(Direction.RIGHT), 0, 10, "go right");
        assertTrue(controls.accept(right));
        assertEquals(Set.of(Direction.RIGHT), game.getSnapshot().heldDirections());
        ControlContext context = controls.getContext();
        assertEquals(game.getSnapshot().heldDirections(), context.heldDirections());
        assertEquals("go right", context.previousCommand());
        assertEquals(1, context.revision());
        assertTrue(context.movementAllowed());
    }

    @Test
    void ordinaryPressAndSwitchReplaceTheWholeDirectionSet() {
        accept(ActionType.PRESS, Set.of(Direction.UP, Direction.RIGHT), 0, 1);
        accept(ActionType.PRESS, Set.of(Direction.LEFT), 0, 2);
        assertEquals(Set.of(Direction.LEFT), game.getSnapshot().heldDirections());
        accept(ActionType.SWITCH, Set.of(Direction.RIGHT), 0, 3);
        assertEquals(Set.of(Direction.RIGHT), game.getSnapshot().heldDirections());
    }

    @Test
    void aSupersededMalformedDecisionIsIgnoredBeforeSchemaValidation() {
        ActionDecision older = request(ActionType.PRESS, Set.of(Direction.LEFT, Direction.RIGHT), 0, 1, "bad old");
        ActionDecision newer = request(ActionType.PRESS, Set.of(Direction.UP), 0, 2, "up");
        assertFalse(controls.accept(older));
        assertTrue(controls.accept(newer));
        assertFalse(controls.accept(older));
        assertEquals(Set.of(Direction.UP), game.getSnapshot().heldDirections());
    }

    @Test
    void currentOppositeDirectionsAndIllegalDurationsCannotChangeControls() {
        accept(ActionType.PRESS, Set.of(Direction.RIGHT), 0, 1);
        ActionDecision opposed = request(ActionType.PRESS, Set.of(Direction.LEFT, Direction.RIGHT), 0, 2, "opposed");
        assertThrows(IllegalArgumentException.class, () -> controls.accept(opposed));
        assertEquals(Set.of(Direction.RIGHT), game.getSnapshot().heldDirections());
        ActionDecision tooLong = request(ActionType.TIMED_PRESS, Set.of(Direction.LEFT), 1000, 3, "too long");
        assertThrows(IllegalArgumentException.class, () -> controls.accept(tooLong));
        ActionDecision durationOnPress = request(ActionType.PRESS, Set.of(Direction.LEFT), 100, 4, "invalid");
        assertThrows(IllegalArgumentException.class, () -> controls.accept(durationOnPress));
        ActionDecision emptyPress = request(ActionType.PRESS, Set.of(), 0, 5, "empty");
        assertThrows(IllegalArgumentException.class, () -> controls.accept(emptyPress));
    }

    @Test
    void nondirectionalActionsRejectDirectionsAndInvalidMetadata() {
        ActionDecision invalid = request(ActionType.KEEP_CURRENT, Set.of(Direction.RIGHT), 0, 1, "keep");
        assertThrows(IllegalArgumentException.class, () -> controls.accept(invalid));
        var context = controls.getContext();
        ActionDecision badIdentity = new ActionDecision(ActionType.NO_ACTION, Set.of(), 0,
                context.latestVersion(), -1, context.epoch(), context.revision(), "bad identity");
        assertThrows(IllegalArgumentException.class, () -> controls.accept(badIdentity));
    }

    @Test
    void beginInputCannotRollBackLatestVersion() {
        controls.beginInput(20);
        controls.beginInput(3);
        assertEquals(20, controls.getContext().latestVersion());
        assertThrows(IllegalArgumentException.class, () -> controls.beginInput(-1));
    }

    @Test
    void timedInputExpiresOnceAndInvalidatesOldContext() {
        accept(ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 200, 1);
        ActionDecision waiting = request(ActionType.SWITCH, Set.of(Direction.LEFT), 0, 2, "other way");
        long before = controls.getContext().revision();
        controls.advance(0.199);
        assertEquals(Set.of(Direction.RIGHT), game.getSnapshot().heldDirections());
        controls.advance(0.001);
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        assertEquals(before + 1, controls.getContext().revision());
        assertFalse(controls.accept(waiting));
        controls.advance(1);
        assertEquals(before + 1, controls.getContext().revision());
    }

    @Test
    void semanticPartialDuplicatesCannotRestartOrExtendATimer() {
        accept(ActionType.TIMED_PRESS, Set.of(Direction.LEFT), 200, 7);
        controls.advance(0.1);
        long revision = controls.getContext().revision();
        ActionDecision duplicate = request(ActionType.TIMED_PRESS, Set.of(Direction.LEFT), 200, 7, "a little left please");
        assertTrue(controls.accept(duplicate));
        assertEquals(revision, controls.getContext().revision());
        controls.advance(0.1);
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        ActionDecision afterExpiry = request(ActionType.TIMED_PRESS, Set.of(Direction.LEFT), 200, 7, "a little left please now");
        assertTrue(controls.accept(afterExpiry));
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
    }

    @Test
    void sameActionInANewUtteranceIsAnIntentionalNewTimedPress() {
        accept(ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 100, 1);
        controls.advance(0.1);
        accept(ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 100, 2);
        assertEquals(Set.of(Direction.RIGHT), game.getSnapshot().heldDirections());
        controls.advance(0.1);
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
    }

    @Test
    void repeatAfterExpiryCreatesAFreshTimerWithTheRememberedDurationAndDirections() {
        accept(ActionType.TIMED_PRESS, Set.of(Direction.RIGHT, Direction.UP), 200, 1);
        controls.advance(0.2);
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        assertEquals(new MovementIntent(ActionType.TIMED_PRESS, Set.of(Direction.RIGHT, Direction.UP), 200),
                controls.getContext().lastMovement());
        accept(ActionType.REPEAT_LAST, Set.of(), 0, 2);
        controls.advance(0.199);
        assertEquals(Set.of(Direction.RIGHT, Direction.UP), game.getSnapshot().heldDirections());
        controls.advance(0.001);
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        accept(ActionType.REPEAT_LAST, Set.of(), 0, 3);
        assertEquals(Set.of(Direction.RIGHT, Direction.UP), game.getSnapshot().heldDirections());
        controls.advance(0.2);
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
    }

    @Test
    void repeatPartialsCannotRearmATimerEvenAcrossInterveningNoAction() {
        accept(ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 200, 1);
        controls.advance(0.2);
        accept(ActionType.REPEAT_LAST, Set.of(), 0, 2);
        controls.advance(0.1);
        accept(ActionType.REPEAT_LAST, Set.of(), 0, 2);
        controls.advance(0.1);
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        accept(ActionType.NO_ACTION, Set.of(), 0, 2);
        accept(ActionType.REPEAT_LAST, Set.of(), 0, 2);
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
    }

    @Test
    void stopReleaseKeepAndUnrecognizedInputPreserveOnlyTheLastMovementIntent() {
        accept(ActionType.PRESS, Set.of(Direction.RIGHT, Direction.UP), 0, 1);
        MovementIntent movement = controls.getContext().lastMovement();
        accept(ActionType.RELEASE, Set.of(Direction.RIGHT), 0, 2);
        accept(ActionType.KEEP_CURRENT, Set.of(), 0, 3);
        accept(ActionType.NO_ACTION, Set.of(), 0, 4);
        accept(ActionType.RELEASE_ALL, Set.of(), 0, 5);
        assertEquals(movement, controls.getContext().lastMovement());
        assertEquals("RELEASE_ALL", controls.getContext().previousCommand());
        accept(ActionType.REPEAT_LAST, Set.of(), 0, 6);
        assertEquals(Set.of(Direction.RIGHT, Direction.UP), game.getSnapshot().heldDirections());
        controls.advance(1);
        assertEquals(Set.of(Direction.RIGHT, Direction.UP), game.getSnapshot().heldDirections());
    }

    @Test
    void aCorrectedMovementReplacesTheRememberedIntent() {
        accept(ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 400, 1);
        accept(ActionType.SWITCH, Set.of(Direction.LEFT), 0, 2);
        accept(ActionType.RELEASE_ALL, Set.of(), 0, 3);
        accept(ActionType.REPEAT_LAST, Set.of(), 0, 4);
        controls.advance(1);
        assertEquals(Set.of(Direction.LEFT), game.getSnapshot().heldDirections());
        assertEquals(ActionType.SWITCH, controls.getContext().lastMovement().type());
    }

    @Test
    void repeatWithoutHistoryDoesNothingAndMalformedOrStaleRepeatCannotExecute() {
        accept(ActionType.REPEAT_LAST, Set.of(), 0, 1);
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        assertNull(controls.getContext().lastMovement());
        accept(ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 100, 2);
        controls.advance(0.1);
        ActionDecision malformed = request(ActionType.REPEAT_LAST, Set.of(Direction.LEFT), 0, 3, "again");
        assertThrows(IllegalArgumentException.class, () -> controls.accept(malformed));
        ActionDecision late = request(ActionType.REPEAT_LAST, Set.of(), 0, 4, "again");
        controls.reset();
        assertFalse(controls.accept(late));
        accept(ActionType.REPEAT_LAST, Set.of(), 0, 5);
        assertNull(controls.getContext().lastMovement());
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
    }

    @Test
    void cancellingATimerCannotLaterReleaseANewerPressOfTheSameDirection() {
        accept(ActionType.TIMED_PRESS, Set.of(Direction.LEFT), 200, 1);
        controls.advance(0.05);
        accept(ActionType.PRESS, Set.of(Direction.LEFT), 0, 2);
        long revision = controls.getContext().revision();
        controls.advance(1);
        assertEquals(Set.of(Direction.LEFT), game.getSnapshot().heldDirections());
        assertEquals(revision, controls.getContext().revision());
    }

    @Test
    void combinedReplacementReassertsDirectionReleasedByOldTimerCancellation() {
        accept(ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 200, 1);
        accept(ActionType.PRESS, Set.of(Direction.RIGHT, Direction.UP), 0, 2);
        controls.advance(1);
        assertEquals(Set.of(Direction.RIGHT, Direction.UP), game.getSnapshot().heldDirections());
    }

    @Test
    void partialReleasePreservesOtherDirectionsAfterCancellingTheOldTimedHold() {
        accept(ActionType.TIMED_PRESS, Set.of(Direction.UP, Direction.RIGHT), 200, 1);
        accept(ActionType.RELEASE, Set.of(Direction.RIGHT), 0, 2);
        assertEquals(Set.of(Direction.UP), game.getSnapshot().heldDirections());
        controls.advance(1);
        assertEquals(Set.of(Direction.UP), game.getSnapshot().heldDirections());
    }

    @Test
    void keepCurrentAndNoActionPreserveTheExistingExpiry() {
        accept(ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 200, 1);
        controls.advance(0.05);
        accept(ActionType.KEEP_CURRENT, Set.of(), 0, 2);
        controls.advance(0.05);
        accept(ActionType.NO_ACTION, Set.of(), 0, 3);
        controls.advance(0.1);
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
    }

    @Test
    void releaseAllCancelsTimersAndStopsVelocityImmediately() {
        accept(ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 200, 1);
        accept(ActionType.RELEASE_ALL, Set.of(), 0, 2);
        assertEquals(Vector2.ZERO, game.getSnapshot().velocity());
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        long revision = controls.getContext().revision();
        controls.advance(1);
        assertEquals(revision, controls.getContext().revision());
    }

    @Test
    void aDifferentCorrectionInTheSameUtteranceReplacesTheDuplicateKey() {
        accept(ActionType.PRESS, Set.of(Direction.RIGHT), 0, 9);
        accept(ActionType.SWITCH, Set.of(Direction.LEFT), 0, 9);
        accept(ActionType.PRESS, Set.of(Direction.RIGHT), 0, 9);
        assertEquals(Set.of(Direction.RIGHT), game.getSnapshot().heldDirections());
        assertEquals(3, controls.getContext().revision());
    }

    @Test
    void resetInvalidatesOldEpochAndClearsMemoryInputsAndTimers() {
        accept(ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 200, 1);
        ActionDecision late = request(ActionType.PRESS, Set.of(Direction.LEFT), 0, 2, "left");
        long oldEpoch = controls.getContext().epoch();
        controls.reset();
        assertEquals(oldEpoch + 1, controls.getContext().epoch());
        assertEquals("", controls.getContext().previousCommand());
        assertNull(controls.getContext().lastMovement());
        assertEquals(-1, controls.getContext().latestVersion());
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        assertEquals(Vector2.ZERO, game.getSnapshot().velocity());
        assertFalse(controls.accept(late));
        long revision = controls.getContext().revision();
        controls.advance(1);
        assertEquals(revision, controls.getContext().revision());
    }

    @Test
    void fallObserverCancelsPendingAndTimedControlBeforeTheFinalStateSnapshot() {
        game.addObserver(event -> {
            if (event.type() == GameEventType.FALL_DETECTED) controls.reset();
        });
        accept(ActionType.TIMED_PRESS, Set.of(Direction.UP), 400, 1);
        ActionDecision late = request(ActionType.SWITCH, Set.of(Direction.DOWN), 0, 2, "other way");
        // Simulate a simulation update crossing the edge before the 400 ms hold expires.
        controls.advance(0.35);
        game.update(0.35);
        assertEquals(PlayerStatus.FALLING, game.getSnapshot().status());
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        assertFalse(controls.getContext().movementAllowed());
        assertNull(controls.getContext().lastMovement());
        assertFalse(controls.accept(late));
        game.update(1.0 / 60);
        game.update(1.0 / 60);
        assertEquals(PlayerStatus.ACTIVE, game.getSnapshot().status());
        assertFalse(controls.accept(late));
        accept(ActionType.PRESS, Set.of(Direction.RIGHT), 0, 3);
        controls.advance(1);
        assertEquals(Set.of(Direction.RIGHT), game.getSnapshot().heldDirections());
    }

    private void accept(ActionType type, Set<Direction> directions, int durationMs, long utterance) {
        assertTrue(controls.accept(request(type, directions, durationMs, utterance, type.name())));
    }

    private ActionDecision request(ActionType type, Set<Direction> directions, int durationMs,
                                   long utterance, String sourceText) {
        controls.beginInput(++nextVersion);
        ControlContext context = controls.getContext();
        return new ActionDecision(type, directions, durationMs, context.latestVersion(), utterance,
                context.epoch(), context.revision(), sourceText);
    }
}
