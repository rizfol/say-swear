package com.sayswear.game;

public final class FallingState implements PlayerState {
    @Override
    public void update(Player player, GameModel game, double deltaTime) {
        game.resetToStart();
    }

    @Override public boolean allowsMovement() { return false; }
    @Override public PlayerStatus status() { return PlayerStatus.FALLING; }
}
