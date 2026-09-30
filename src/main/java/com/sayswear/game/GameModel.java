package com.sayswear.game;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** The caller serializes mutations on the application loop; observers run synchronously. */
public final class GameModel {
    private static final System.Logger LOGGER = System.getLogger(GameModel.class.getName());
    private final Player player;
    private final Level level;
    private final EnumSet<Direction> heldDirections = EnumSet.noneOf(Direction.class);
    private final List<GameObserver> observers = new ArrayList<>();

    public GameModel() { this(Level.standard()); }

    public GameModel(Level level) {
        this.level = Objects.requireNonNull(level, "level");
        player = new Player(level.start());
    }

    public void update(double deltaTime) {
        requireDeltaTime(deltaTime);
        player.update(this, deltaTime);
        notifyObservers(new GameEvent(GameEventType.STATE_CHANGED, getSnapshot()));
    }

    public void evaluatePosition() {
        if (!player.canMove()) {
            return;
        }
        if (!level.isSafe(player.position())) {
            player.setState(new FallingState());
            notifyObservers(new GameEvent(GameEventType.FALL_DETECTED, getSnapshot()));
            return;
        }
        if (level.isGoal(player.position())) {
            player.setState(new FinishedState());
            notifyObservers(new GameEvent(GameEventType.GOAL_REACHED, getSnapshot()));
        }
    }

    public void press(Direction direction) {
        Objects.requireNonNull(direction, "direction");
        if (!player.canMove()) {
            return;
        }
        if (heldDirections.contains(direction.opposite())) {
            throw new IllegalArgumentException("Opposite directions cannot be held together");
        }
        heldDirections.add(direction);
        player.refreshVelocity(heldDirections);
    }

    public void release(Direction direction) {
        heldDirections.remove(Objects.requireNonNull(direction, "direction"));
        player.refreshVelocity(heldDirections);
    }

    public void releaseAll() {
        heldDirections.clear();
        player.stop();
    }

    public void resetToStart() {
        releaseAll();
        player.setState(new RespawningState());
        player.respawn(level.start());
    }

    /** Start a fresh run; the controller first invalidates external control/agent work. */
    public void restart() {
        releaseAll();
        player.respawn(level.start());
        player.setState(new ActiveState());
        notifyObservers(new GameEvent(GameEventType.STATE_CHANGED, getSnapshot()));
    }

    public GameSnapshot getSnapshot() {
        return new GameSnapshot(player.position(), player.velocity(), heldDirections,
                player.status(), level);
    }

    public void addObserver(GameObserver observer) {
        Objects.requireNonNull(observer, "observer");
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void removeObserver(GameObserver observer) { observers.remove(observer); }

    public void notifyObservers(GameEvent event) {
        Objects.requireNonNull(event, "event");
        for (GameObserver observer : List.copyOf(observers)) {
            try {
                observer.onGameEvent(event);
            } catch (RuntimeException exception) {
                // A broken presentation observer must not prevent the controller's cleanup.
                LOGGER.log(System.Logger.Level.WARNING, "Game observer failed", exception);
            }
        }
    }

    Set<Direction> heldDirections() { return Set.copyOf(heldDirections); }
    Level level() { return level; }

    static void requireDeltaTime(double deltaTime) {
        if (!Double.isFinite(deltaTime) || deltaTime < 0 || deltaTime > 60) {
            throw new IllegalArgumentException("deltaTime must be finite and between 0 and 60 seconds");
        }
    }
}
