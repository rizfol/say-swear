package com.sayswear.agent;

import com.sayswear.game.Direction;
import java.util.Objects;
import java.util.Set;

/** Repeatable movement semantics only; contains no request identity or running command. */
public record MovementIntent(ActionType type, Set<Direction> directions, int durationMs) {
    public MovementIntent {
        Objects.requireNonNull(type, "type");
        directions = Set.copyOf(directions);
        if (type != ActionType.PRESS && type != ActionType.SWITCH && type != ActionType.TIMED_PRESS) {
            throw new IllegalArgumentException("Only movement can be remembered for repetition");
        }
        if (directions.isEmpty() || directions.size() > 2) {
            throw new IllegalArgumentException("Movement requires compatible directions");
        }
        for (Direction direction : directions) {
            if (directions.contains(direction.opposite())) {
                throw new IllegalArgumentException("Movement requires compatible directions");
            }
        }
        if (type == ActionType.TIMED_PRESS
                ? durationMs != 100 && durationMs != 200 && durationMs != 400
                : durationMs != 0) {
            throw new IllegalArgumentException("Invalid movement duration");
        }
    }
}
