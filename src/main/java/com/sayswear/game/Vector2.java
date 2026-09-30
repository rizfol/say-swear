package com.sayswear.game;

/** Immutable world-space coordinate or velocity. */
public record Vector2(double x, double y) {
    public static final Vector2 ZERO = new Vector2(0, 0);

    public Vector2 {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Coordinates must be finite");
        }
    }

    public Vector2 add(Vector2 other) { return add(other.x, other.y); }
    public Vector2 add(double dx, double dy) { return new Vector2(x + dx, y + dy); }
    public Vector2 subtract(Vector2 other) { return new Vector2(x - other.x, y - other.y); }
    public Vector2 scale(double factor) { return new Vector2(x * factor, y * factor); }
    public double length() { return Math.hypot(x, y); }
    public double distance(Vector2 other) { return Math.hypot(x - other.x, y - other.y); }
}
