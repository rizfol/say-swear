package com.sayswear.control;

import com.sayswear.game.GameModel;

public final class ReleaseAllCommand implements GameCommand {
    @Override public void execute(GameModel game) { game.releaseAll(); }
    @Override public void cancel(GameModel game) { /* An applied release is never undone. */ }
}
