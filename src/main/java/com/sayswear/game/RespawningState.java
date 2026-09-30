package com.sayswear.game;

public final class RespawningState implements PlayerState {
    @Override
    public void update(Player player, GameModel game, double deltaTime) {
        player.setState(new ActiveState());
        game.notifyObservers(new GameEvent(GameEventType.RESPAWNED, game.getSnapshot()));
    }

    @Override public boolean allowsMovement() { return false; }
    @Override public PlayerStatus status() { return PlayerStatus.RESPAWNING; }
}
