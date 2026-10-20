package com.sayswear.game;

public interface PlayerState {
    void update(Player player, GameModel game, double deltaTime);
    boolean allowsMovement();
    PlayerStatus status();
}
