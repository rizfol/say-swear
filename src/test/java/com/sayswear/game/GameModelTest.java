package com.sayswear.game;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GameModelTest {
    @Test
    void diagonalAndCardinalMovementHaveTheSameSpeed() {
        GameModel cardinal = new GameModel(wideLevel());
        GameModel diagonal = new GameModel(wideLevel());
        cardinal.press(Direction.RIGHT);
        diagonal.press(Direction.RIGHT);
        diagonal.press(Direction.UP);
        cardinal.update(1);
        diagonal.update(1);

        assertEquals(Player.SPEED, cardinal.getSnapshot().position().distance(wideLevel().start()), 1e-9);
        assertEquals(Player.SPEED, diagonal.getSnapshot().position().distance(wideLevel().start()), 1e-9);
        assertEquals(Player.SPEED, diagonal.getSnapshot().velocity().length(), 1e-9);
        assertEquals(30 - Player.SPEED / Math.sqrt(2), diagonal.getSnapshot().position().y(), 1e-9);
    }

    @Test
    void releasingControlsStopsImmediatelyAndSnapshotsRemainImmutable() {
        GameModel game = new GameModel();
        game.press(Direction.RIGHT);
        GameSnapshot moving = game.getSnapshot();
        assertEquals(Player.SPEED, moving.velocity().x());

        game.releaseAll();
        GameSnapshot stopped = game.getSnapshot();
        assertEquals(Vector2.ZERO, stopped.velocity());
        assertTrue(stopped.heldDirections().isEmpty());
        assertEquals(Set.of(Direction.RIGHT), moving.heldDirections());
        assertThrows(UnsupportedOperationException.class, () -> moving.heldDirections().clear());
    }

    @Test
    void oppositeInputsAreRejectedWithoutChangingTheHeldDirection() {
        GameModel game = new GameModel();
        game.press(Direction.RIGHT);
        assertThrows(IllegalArgumentException.class, () -> game.press(Direction.LEFT));
        assertEquals(Set.of(Direction.RIGHT), game.getSnapshot().heldDirections());
        game.press(Direction.RIGHT);
        assertEquals(Set.of(Direction.RIGHT), game.getSnapshot().heldDirections());
    }

    @Test
    void fallCleanupAndRecoveryTakeDistinctTicksWithFreshFinalSnapshots() {
        GameModel game = new GameModel();
        List<GameEvent> events = new ArrayList<>();
        game.addObserver(events::add);
        // Simulate the controller's synchronous deterministic cleanup observer.
        game.addObserver(event -> {
            if (event.type() == GameEventType.FALL_DETECTED) {
                game.releaseAll();
            }
        });
        game.press(Direction.UP);
        game.update(0.5);

        assertEquals(PlayerStatus.FALLING, game.getSnapshot().status());
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        assertEquals(Vector2.ZERO, game.getSnapshot().velocity());
        GameEvent fall = events.stream().filter(event -> event.type() == GameEventType.FALL_DETECTED)
                .findFirst().orElseThrow();
        assertEquals(Set.of(Direction.UP), fall.snapshot().heldDirections());
        assertEquals(GameEventType.STATE_CHANGED, events.getLast().type());
        assertTrue(events.getLast().snapshot().heldDirections().isEmpty());

        game.press(Direction.RIGHT);
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        game.update(1.0 / 60);
        assertEquals(PlayerStatus.RESPAWNING, game.getSnapshot().status());
        assertEquals(game.getSnapshot().level().start(), game.getSnapshot().position());
        game.press(Direction.RIGHT);
        game.update(1.0 / 60);
        assertEquals(PlayerStatus.ACTIVE, game.getSnapshot().status());
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        assertTrue(events.stream().anyMatch(event -> event.type() == GameEventType.RESPAWNED));
    }

    @Test
    void aLongUpdateCannotJumpAcrossVoidToAnotherSafeArm() {
        Level level = new Level(List.of(new Vector2(5, 5), new Vector2(5, 25),
                new Vector2(25, 25), new Vector2(25, 5)), 3,
                new Vector2(25, 5), 0.7, 30, 30);
        GameModel game = new GameModel(level);
        game.press(Direction.RIGHT);
        // A single unchecked displacement would finish at the safe goal across the void.
        game.update(20.0 / Player.SPEED);

        assertEquals(PlayerStatus.FALLING, game.getSnapshot().status());
        assertTrue(game.getSnapshot().position().x() < 8);
    }

    @Test
    void everyFallReturnsToTheStartAfterProgressAlongTheLevel() {
        GameModel game = new GameModel();
        game.addObserver(event -> {
            if (event.type() == GameEventType.FALL_DETECTED) game.releaseAll();
        });

        for (int run = 0; run < 2; run++) {
            travel(game, Direction.RIGHT, 20);
            travel(game, Direction.UP, 19);
            if (run == 1) {
                travel(game, Direction.RIGHT, 22);
                travel(game, Direction.DOWN, 17);
            }
            assertEquals(PlayerStatus.ACTIVE, game.getSnapshot().status());
            assertTrue(game.getSnapshot().position().distance(game.getSnapshot().level().start()) > 20);
            game.press(run == 0 ? Direction.UP : Direction.DOWN);
            game.update(0.5);
            assertEquals(PlayerStatus.FALLING, game.getSnapshot().status());
            game.update(1.0 / 60);
            assertEquals(PlayerStatus.RESPAWNING, game.getSnapshot().status());
            assertEquals(game.getSnapshot().level().start(), game.getSnapshot().position());
            assertEquals(Vector2.ZERO, game.getSnapshot().velocity());
            assertTrue(game.getSnapshot().heldDirections().isEmpty());
            game.update(1.0 / 60);
            assertEquals(PlayerStatus.ACTIVE, game.getSnapshot().status());
            game.update(1);
            assertEquals(game.getSnapshot().level().start(), game.getSnapshot().position());
        }
    }

    @Test
    void goalFinishesOnceAndRestartCreatesANeutralFreshRun() {
        GameModel game = new GameModel(straightLevel());
        List<GameEvent> events = new ArrayList<>();
        game.addObserver(events::add);
        game.addObserver(event -> {
            if (event.type() == GameEventType.GOAL_REACHED) game.releaseAll();
        });
        game.press(Direction.RIGHT);
        game.update(9);
        assertEquals(PlayerStatus.FINISHED, game.getSnapshot().status());
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        Vector2 finishedPosition = game.getSnapshot().position();
        game.press(Direction.LEFT);
        game.update(1);
        assertEquals(finishedPosition, game.getSnapshot().position());
        assertEquals(1, events.stream().filter(event -> event.type() == GameEventType.GOAL_REACHED).count());

        game.restart();
        assertEquals(PlayerStatus.ACTIVE, game.getSnapshot().status());
        assertEquals(straightLevel().start(), game.getSnapshot().position());
        assertEquals(Vector2.ZERO, game.getSnapshot().velocity());
        assertTrue(game.getSnapshot().heldDirections().isEmpty());
        game.press(Direction.RIGHT);
        game.update(9);
        assertEquals(PlayerStatus.FINISHED, game.getSnapshot().status());
        assertEquals(2, events.stream().filter(event -> event.type() == GameEventType.GOAL_REACHED).count());
    }

    @Test
    void invalidTimeStepsDoNotMutateTheSimulation() {
        GameModel game = new GameModel();
        game.press(Direction.RIGHT);
        GameSnapshot before = game.getSnapshot();
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, 61}) {
            assertThrows(IllegalArgumentException.class, () -> game.update(invalid));
            assertEquals(before, game.getSnapshot());
        }
    }

    @Test
    void observersCanUnsubscribeWithoutDisruptingTheCurrentPublication() {
        GameModel game = new GameModel();
        List<GameEvent> events = new ArrayList<>();
        GameObserver observer = events::add;
        game.addObserver(observer);
        game.addObserver(observer);
        game.addObserver(event -> game.removeObserver(observer));
        game.update(0);
        game.update(0);
        assertEquals(1, events.size());
    }

    private static Level wideLevel() {
        return new Level(List.of(new Vector2(10, 30), new Vector2(100, 30)), 20,
                new Vector2(100, 30), 0.7, 110, 60);
    }

    private static Level straightLevel() {
        return new Level(List.of(new Vector2(5, 10), new Vector2(65, 10)), 3,
                new Vector2(65, 10), 0.7, 70, 20);
    }

    private static void travel(GameModel game, Direction direction, double distance) {
        game.press(direction);
        game.update(distance / Player.SPEED);
        game.releaseAll();
    }
}
