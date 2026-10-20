package com.sayswear.agent;

import com.sayswear.game.Direction;
import java.util.Objects;
import java.util.Set;

/** Deliberately contains no position, map or goal information. */
public record ControlContext(Set<Direction> heldDirections, String previousCommand,
                             long epoch, long revision, long latestVersion, boolean movementAllowed,
                             MovementIntent lastMovement) {
    public ControlContext {
        heldDirections = Set.copyOf(heldDirections);
        Objects.requireNonNull(previousCommand, "previousCommand");
    }

    /** A context with no remembered movement, such as the beginning of a session. */
    public ControlContext(Set<Direction> heldDirections, String previousCommand,
                          long epoch, long revision, long latestVersion, boolean movementAllowed) {
        this(heldDirections, previousCommand, epoch, revision, latestVersion, movementAllowed, null);
    }
}
