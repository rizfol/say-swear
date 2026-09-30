package com.sayswear.game;

@FunctionalInterface
public interface GameObserver {
    void onGameEvent(GameEvent event);
}
