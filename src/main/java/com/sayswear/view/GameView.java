package com.sayswear.view;

import com.sayswear.game.GameEvent;
import com.sayswear.game.GameObserver;
import com.sayswear.game.GameSnapshot;

/** Presentation boundary shared by the JavaFX and terminal frontends. */
public interface GameView extends GameObserver {
    void render(GameSnapshot snapshot);

    void showMessage(String message);

    default void showTranscript(String transcript) { }

    default void showVoiceStatus(String status) { }

    @Override
    default void onGameEvent(GameEvent event) {
        render(event.snapshot());
        switch (event.type()) {
            case FALL_DETECTED -> showMessage("You fell. Returning to the start.");
            case RESPAWNED -> showMessage("Back at the start. Give a fresh instruction.");
            case GOAL_REACHED -> showMessage("You made it. Goal reached!");
            case STATE_CHANGED -> { }
        }
    }
}
