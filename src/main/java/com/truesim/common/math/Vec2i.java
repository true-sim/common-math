package com.truesim.common.math;

/**
 * Immutable 2D integer vector, e.g. grid cells and tile coordinates. Arithmetic wraps on overflow like {@code int}.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Vec2i(int x, int y) {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    public static final Vec2i ZERO = new Vec2i(0, 0);
    public static final Vec2i ONE = new Vec2i(1, 1);
    public static final Vec2i UNIT_X = new Vec2i(1, 0);
    public static final Vec2i UNIT_Y = new Vec2i(0, 1);

    public static Vec2i of(int x, int y) {
        return new Vec2i(x, y);
    }

    public Vec2i add(Vec2i other) {
        return new Vec2i(x + other.x, y + other.y);
    }

    public Vec2i sub(Vec2i other) {
        return new Vec2i(x - other.x, y - other.y);
    }

    public Vec2i scale(int factor) {
        return new Vec2i(x * factor, y * factor);
    }

    public Vec2i negate() {
        return new Vec2i(-x, -y);
    }

    public long dot(Vec2i other) {
        return (long) x * other.x + (long) y * other.y;
    }

    /** Grid distance moving along axes only. */
    public int manhattanDistance(Vec2i other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y);
    }

    /** Grid distance when diagonal moves count as one step. */
    public int chebyshevDistance(Vec2i other) {
        return Math.max(Math.abs(x - other.x), Math.abs(y - other.y));
    }

    public Vec2i min(Vec2i other) {
        return new Vec2i(Math.min(x, other.x), Math.min(y, other.y));
    }

    public Vec2i max(Vec2i other) {
        return new Vec2i(Math.max(x, other.x), Math.max(y, other.y));
    }

    public Vec2 toVec2() {
        return new Vec2(x, y);
    }
}
