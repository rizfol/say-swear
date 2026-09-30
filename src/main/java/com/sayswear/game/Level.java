package com.sayswear.game;

import java.util.List;
import java.util.Objects;

/** Immutable fixed corridor geometry. It never searches for or chooses a route. */
public final class Level {
    public static final double MARKER_RADIUS = 1.0;
    private static final double EPSILON = 1e-9;

    private final List<Vector2> safePath;
    private final double pathHalfWidth;
    private final Vector2 goal;
    private final double playerRadius;
    private final double width;
    private final double height;

    public Level(List<Vector2> safePath, double pathHalfWidth, Vector2 goal,
                 double playerRadius, double width, double height) {
        this.safePath = List.copyOf(safePath);
        this.goal = Objects.requireNonNull(goal, "goal");
        if (this.safePath.size() < 2) {
            throw new IllegalArgumentException("A path needs at least two points");
        }
        if (!Double.isFinite(pathHalfWidth) || !Double.isFinite(playerRadius)
                || playerRadius <= 0 || pathHalfWidth <= playerRadius
                || !Double.isFinite(width) || width <= 0
                || !Double.isFinite(height) || height <= 0) {
            throw new IllegalArgumentException("Invalid level dimensions or collision radius");
        }
        this.pathHalfWidth = pathHalfWidth;
        this.playerRadius = playerRadius;
        this.width = width;
        this.height = height;
        for (int i = 0; i < this.safePath.size(); i++) {
            Vector2 point = this.safePath.get(i);
            if (point.x() < 0 || point.x() > width || point.y() < 0 || point.y() > height) {
                throw new IllegalArgumentException("Path points must fit the level bounds");
            }
            if (i > 0 && point.equals(this.safePath.get(i - 1))) {
                throw new IllegalArgumentException("Path segments must have positive length");
            }
        }
        if (!isSafe(goal)) {
            throw new IllegalArgumentException("Goal must be on the safe path");
        }
    }

    public static Level standard() {
        return new Level(List.of(
                new Vector2(10, 64), new Vector2(30, 64),
                new Vector2(30, 45), new Vector2(52, 45),
                new Vector2(52, 62), new Vector2(80, 62),
                new Vector2(80, 28), new Vector2(60, 28),
                new Vector2(60, 12), new Vector2(100, 12)),
                3.0,
                new Vector2(100, 12), 0.7, 110, 76);
    }

    public List<Vector2> safePath() { return safePath; }
    public double pathHalfWidth() { return pathHalfWidth; }
    public Vector2 goal() { return goal; }
    public Vector2 start() { return safePath.getFirst(); }
    public double playerRadius() { return playerRadius; }
    public double width() { return width; }
    public double height() { return height; }

    /**
     * The player center must lie inside a corridor shrunk by the player's radius.
     * Round segment ends make the whole circular footprint safe at turns too.
     */
    public boolean isSafe(Vector2 position) {
        Objects.requireNonNull(position, "position");
        double clearance = pathHalfWidth - playerRadius;
        for (int i = 1; i < safePath.size(); i++) {
            if (distanceToSegment(position, safePath.get(i - 1), safePath.get(i))
                    <= clearance + EPSILON) {
                return true;
            }
        }
        return false;
    }

    public boolean isGoal(Vector2 position) {
        return goal.distance(Objects.requireNonNull(position, "position"))
                <= MARKER_RADIUS + playerRadius + EPSILON;
    }

    private static double distanceToSegment(Vector2 point, Vector2 start, Vector2 end) {
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double projection = ((point.x() - start.x()) * dx + (point.y() - start.y()) * dy)
                / (dx * dx + dy * dy);
        double t = Math.max(0, Math.min(1, projection));
        return Math.hypot(point.x() - start.x() - t * dx, point.y() - start.y() - t * dy);
    }
}
