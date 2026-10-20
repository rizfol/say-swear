package com.sayswear.game;

public final class FinishedState implements PlayerState {
    @Override
    public void update(Player player, GameModel game, double deltaTime) {
        player.stop();
    }

    @Override public boolean allowsMovement() { return false; }
    @Override public PlayerStatus status() { return PlayerStatus.FINISHED; }
}
