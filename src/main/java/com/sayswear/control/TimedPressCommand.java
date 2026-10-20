package com.sayswear.control;

import com.sayswear.game.Direction;
import com.sayswear.game.GameModel;
import com.sayswear.game.PlayerStatus;
import java.util.Set;

/** A single-use hold whose expiry is advanced only by the serialized application loop. */
public final class TimedPressCommand implements GameCommand {
    private final Set<Direction> directions;
    private final int durationMs;
    private double remainingMs;
    private boolean executed;
    private boolean active;

    public TimedPressCommand(Direction direction, int durationMs) {
        this(Set.of(direction), durationMs);
    }

    public TimedPressCommand(Set<Direction> directions, int durationMs) {
        this.directions = Set.copyOf(directions);
        if (this.directions.isEmpty() || this.directions.size() > 2
                || this.directions.stream().anyMatch(direction -> this.directions.contains(direction.opposite()))) {
            throw new IllegalArgumentException("Timed input needs one or two compatible directions");
        }
        if (durationMs != 100 && durationMs != 200 && durationMs != 400) {
            throw new IllegalArgumentException("Timed input must use 100, 200, or 400 milliseconds");
        }
        this.durationMs = durationMs;
        remainingMs = durationMs;
    }

    @Override
    public void execute(GameModel game) {
        if (executed) {
            return;
        }
        executed = true;
        if (game.getSnapshot().status() != PlayerStatus.ACTIVE) {
            remainingMs = 0;
            return;
        }
        for (Direction direction : Direction.values()) {
            if (directions.contains(direction)) {
                game.press(direction);
            }
        }
        remainingMs = durationMs;
        active = true;
    }

    @Override
    public void cancel(GameModel game) {
        if (active) {
            active = false;
            remainingMs = 0;
            directions.forEach(game::release);
        }
    }

    /** Returns true exactly once when this advancement expires an active hold. */
    public boolean advance(double deltaTime, GameModel game) {
        if (!Double.isFinite(deltaTime) || deltaTime < 0) {
            throw new IllegalArgumentException("deltaTime must be a finite nonnegative number of seconds");
        }
        if (!active) {
            return false;
        }
        remainingMs -= deltaTime * 1000;
        if (remainingMs <= 1e-8) {
            cancel(game);
            return true;
        }
        return false;
    }

    boolean isActive() { return active; }
}
