package com.sayswear.control;

import com.sayswear.game.Direction;
import com.sayswear.game.GameModel;
import java.util.Objects;

public final class KeyUpCommand implements GameCommand {
    private final Direction direction;

    public KeyUpCommand(Direction direction) {
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    @Override public void execute(GameModel game) { game.release(direction); }
    @Override public void cancel(GameModel game) { /* An applied release is never undone. */ }
}
