package com.sayswear.game;

import java.util.Objects;
import java.util.Set;

public final class Player {
    public static final double SPEED = 7.0;

    private Vector2 position;
    private Vector2 velocity = Vector2.ZERO;
    private PlayerState state = new ActiveState();

    public Player(Vector2 position) {
        this.position = Objects.requireNonNull(position, "position");
    }

    public Vector2 position() { return position; }
    public Vector2 velocity() { return velocity; }
    public PlayerStatus status() { return state.status(); }
    public boolean canMove() { return state.allowsMovement(); }

    public void update(GameModel game, double deltaTime) {
        state.update(this, game, deltaTime);
    }

    public void move(double deltaTime, Set<Direction> held) {
        GameModel.requireDeltaTime(deltaTime);
        refreshVelocity(held);
        position = position.add(velocity.scale(deltaTime));
    }

    public void setState(PlayerState state) {
        this.state = Objects.requireNonNull(state, "state");
        if (!state.allowsMovement()) {
            stop();
        }
    }

    public void respawn(Vector2 position) {
        this.position = Objects.requireNonNull(position, "position");
        stop();
    }

    void refreshVelocity(Set<Direction> held) {
        if (!canMove() || held.isEmpty()) {
            stop();
            return;
        }
        int dx = 0;
        int dy = 0;
        for (Direction direction : held) {
            dx += direction.dx();
            dy += direction.dy();
        }
        double length = Math.hypot(dx, dy);
        velocity = length == 0 ? Vector2.ZERO : new Vector2(SPEED * dx / length, SPEED * dy / length);
    }

    void stop() { velocity = Vector2.ZERO; }
}
