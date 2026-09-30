package com.sayswear.control;

import com.sayswear.game.GameModel;

/** A deterministic input operation. Cancellation releases inputs; it never rewinds position. */
public interface GameCommand {
    void execute(GameModel game);
    void cancel(GameModel game);
}
