package com.truesim.common.math.geometry;

import com.truesim.common.math.JitInlining;
import com.truesim.common.math.Vec2;

/**
 * Immutable axis-aligned 2D rectangle from {@code min} to {@code max} (inclusive), e.g. UI bounds or 2D bounding
 * boxes.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Rect(Vec2 min, Vec2 max) {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    /** Contains nothing; the neutral start for {@link #include} and {@link #union}. */
    public static final Rect EMPTY = new Rect(Vec2.splat(Double.POSITIVE_INFINITY), Vec2.splat(Double.NEGATIVE_INFINITY));

    /** Rectangle spanned by two corners, in any order. */
    public static Rect of(Vec2 a, Vec2 b) {
        return new Rect(a.min(b), a.max(b));
    }

    public static Rect fromPositionSize(Vec2 position, Vec2 size) {
        return of(position, position.add(size));
    }

    public boolean isEmpty() {
        return min.x() > max.x() || min.y() > max.y();
    }

    public Vec2 center() {
        return min.interpolate(max, 0.5);
    }

    public Vec2 size() {
        return max.sub(min);
    }

    public double area() {
        Vec2 s = size();
        return s.x() * s.y();
    }

    public boolean contains(Vec2 p) {
        return p.x() >= min.x() && p.x() <= max.x() && p.y() >= min.y() && p.y() <= max.y();
    }

    public boolean contains(Rect other) {
        return contains(other.min) && contains(other.max);
    }

    public boolean intersects(Rect other) {
        return min.x() <= other.max.x() && max.x() >= other.min.x() && min.y() <= other.max.y() && max.y() >= other.min.y();
    }

    /** The overlapping part, or an empty rectangle if they do not overlap. */
    public Rect intersection(Rect other) {
        return new Rect(min.max(other.min), max.min(other.max));
    }

    public Vec2 closestPoint(Vec2 p) {
        return p.clamp(min, max);
    }

    public Rect union(Rect other) {
        return new Rect(min.min(other.min), max.max(other.max));
    }

    public Rect include(Vec2 p) {
        return new Rect(min.min(p), max.max(p));
    }

    /** Grown by {@code margin} on every side (shrunk if negative). */
    public Rect expand(double margin) {
        Vec2 m = Vec2.splat(margin);
        return new Rect(min.sub(m), max.add(m));
    }
}
