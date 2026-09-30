package com.sayswear.control;

import com.sayswear.game.GameModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Owns active timers. It has no background timer thread or delayed release callbacks. */
public final class ActionExecutor {
    private final GameModel game;
    private final List<TimedPressCommand> activeTimed = new ArrayList<>(1);

    public ActionExecutor(GameModel game) {
        this.game = Objects.requireNonNull(game, "game");
    }

    public void execute(List<GameCommand> commands) {
        List<GameCommand> plan = List.copyOf(commands);
        if (plan.isEmpty()) {
            return;
        }
        if (!activeTimed.isEmpty()) {
            throw new IllegalStateException("Cancel the old timed plan before executing a replacement");
        }
        if (plan.stream().filter(TimedPressCommand.class::isInstance).count() > 1) {
            throw new IllegalArgumentException("A plan may own only one timed press");
        }
        List<GameCommand> applied = new ArrayList<>();
        try {
            for (GameCommand command : plan) {
                applied.add(command);
                command.execute(game);
                if (command instanceof TimedPressCommand timed && timed.isActive()) {
                    activeTimed.add(timed);
                }
            }
        } catch (RuntimeException failure) {
            activeTimed.clear();
            for (int i = applied.size() - 1; i >= 0; i--) {
                try {
                    applied.get(i).cancel(game);
                } catch (RuntimeException cancellationFailure) {
                    failure.addSuppressed(cancellationFailure);
                }
            }
            game.releaseAll();
            throw failure;
        }
    }

    public void cancelAll() {
        List<TimedPressCommand> cancelled = List.copyOf(activeTimed);
        activeTimed.clear();
        cancelled.forEach(command -> command.cancel(game));
    }

    /** @return whether an active timed command expired during this tick */
    public boolean advance(double deltaTime) {
        if (!Double.isFinite(deltaTime) || deltaTime < 0) {
            throw new IllegalArgumentException("deltaTime must be a finite nonnegative number of seconds");
        }
        boolean expired = false;
        var iterator = activeTimed.iterator();
        while (iterator.hasNext()) {
            TimedPressCommand command = iterator.next();
            if (command.advance(deltaTime, game)) {
                iterator.remove();
                expired = true;
            }
        }
        return expired;
    }
}
