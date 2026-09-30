package com.sayswear.game;

import java.util.Set;

public final class ActiveState implements PlayerState {
    @Override
    public void update(Player player, GameModel game, double deltaTime) {
        Set<Direction> held = game.heldDirections();
        if (held.isEmpty() || deltaTime == 0) {
            player.move(0, held);
            game.evaluatePosition();
            return;
        }
        // Check position throughout a large update, not just at its potentially safe endpoint.
        double clearance = game.level().pathHalfWidth() - game.level().playerRadius();
        double maximumStep = Math.min(1.0 / 120.0, clearance / (2.0 * Player.SPEED));
        int steps = (int) Math.ceil(deltaTime / maximumStep);
        if (steps > 100_000) {
            throw new IllegalArgumentException("Update is too large for this corridor width");
        }
        double step = deltaTime / steps;
        for (int i = 0; i < steps && player.canMove(); i++) {
            player.move(step, game.heldDirections());
            game.evaluatePosition();
        }
    }

    @Override public boolean allowsMovement() { return true; }
    @Override public PlayerStatus status() { return PlayerStatus.ACTIVE; }
}
