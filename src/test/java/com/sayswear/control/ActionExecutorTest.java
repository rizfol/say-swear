package com.sayswear.control;

import com.sayswear.game.Direction;
import com.sayswear.game.GameModel;
import com.sayswear.game.Vector2;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ActionExecutorTest {
    @Test
    void tickQuantizationReleasesAHoldOnceWithoutAFloatingPointExtraTick() {
        GameModel game = new GameModel();
        ActionExecutor executor = new ActionExecutor(game);
        executor.execute(List.of(new TimedPressCommand(Direction.RIGHT, 200)));
        for (int tick = 0; tick < 11; tick++) {
            assertFalse(executor.advance(1.0 / 60));
        }
        assertEquals(Set.of(Direction.RIGHT), game.getSnapshot().heldDirections());
        assertTrue(executor.advance(1.0 / 60));
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        assertFalse(executor.advance(1));
    }

    @Test
    void cancellationIsIdempotentAndOldTimerCannotAffectANewCommand() {
        GameModel game = new GameModel();
        ActionExecutor executor = new ActionExecutor(game);
        executor.execute(List.of(new TimedPressCommand(Direction.LEFT, 200)));
        executor.cancelAll();
        executor.cancelAll();
        executor.execute(List.of(new KeyDownCommand(Direction.LEFT)));
        assertFalse(executor.advance(1));
        assertEquals(Set.of(Direction.LEFT), game.getSnapshot().heldDirections());
    }

    @Test
    void twoTimersInOnePlanAreRejectedBeforeAnyInputIsApplied() {
        GameModel game = new GameModel();
        ActionExecutor executor = new ActionExecutor(game);
        assertThrows(IllegalArgumentException.class, () -> executor.execute(List.of(
                new TimedPressCommand(Direction.UP, 100), new TimedPressCommand(Direction.RIGHT, 100))));
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        assertFalse(executor.advance(1));
    }

    @Test
    void failureHalfwayThroughAPlanLeavesNoPartialInputOrTimerBehind() {
        GameModel game = new GameModel();
        ActionExecutor executor = new ActionExecutor(game);
        GameCommand failing = new GameCommand() {
            @Override public void execute(GameModel model) { throw new IllegalStateException("execution failed"); }
            @Override public void cancel(GameModel model) { }
        };
        assertThrows(IllegalStateException.class, () -> executor.execute(List.of(
                new TimedPressCommand(Direction.UP, 100), failing)));
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        assertEquals(Vector2.ZERO, game.getSnapshot().velocity());
        assertFalse(executor.advance(1));
    }

    @Test
    void appliedReleaseCommandsAreNeverUndoneByCancellation() {
        GameModel game = new GameModel();
        game.press(Direction.RIGHT);
        KeyUpCommand release = new KeyUpCommand(Direction.RIGHT);
        release.execute(game);
        release.cancel(game);
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        game.press(Direction.UP);
        ReleaseAllCommand stop = new ReleaseAllCommand();
        stop.execute(game);
        stop.cancel(game);
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
    }

    @Test
    void invalidTimerAdvanceCannotConsumeAnExistingHold() {
        GameModel game = new GameModel();
        ActionExecutor executor = new ActionExecutor(game);
        executor.execute(List.of(new TimedPressCommand(Direction.RIGHT, 100)));
        assertThrows(IllegalArgumentException.class, () -> executor.advance(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> executor.advance(-0.1));
        assertFalse(executor.advance(0.099));
        assertEquals(Set.of(Direction.RIGHT), game.getSnapshot().heldDirections());
        assertTrue(executor.advance(0.001));
    }
}
