package com.sayswear.game;

import java.util.Objects;
import java.util.Set;

/** A publication-time value; neither frontend can mutate the live game through it. */
public record GameSnapshot(Vector2 position, Vector2 velocity,
                           Set<Direction> heldDirections, PlayerStatus status,
                           Level level) {
    public GameSnapshot {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(velocity, "velocity");
        heldDirections = Set.copyOf(heldDirections);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(level, "level");
    }
}
