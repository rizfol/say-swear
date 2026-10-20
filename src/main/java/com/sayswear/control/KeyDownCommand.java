package com.sayswear.control;

import com.sayswear.game.Direction;
import com.sayswear.game.GameModel;
import java.util.Objects;

public final class KeyDownCommand implements GameCommand {
    private final Direction direction;
    private boolean applied;

    public KeyDownCommand(Direction direction) {
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    @Override
    public void execute(GameModel game) {
        game.press(direction);
        applied = true;
    }

    @Override
    public void cancel(GameModel game) {
        if (applied) {
            applied = false;
            game.release(direction);
        }
    }
}
