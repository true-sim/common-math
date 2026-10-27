package com.truesim.common.math.geometry;

import com.truesim.common.math.JitInlining;
import com.truesim.common.math.Mat4;
import com.truesim.common.math.Transform;
import com.truesim.common.math.Vec3;

/**
 * Immutable axis-aligned bounding box from {@code min} to {@code max} (inclusive).
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Aabb(Vec3 min, Vec3 max) {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    /** Contains nothing; the neutral start for {@link #include} and {@link #union}. */
    public static final Aabb EMPTY = new Aabb(Vec3.splat(Double.POSITIVE_INFINITY), Vec3.splat(Double.NEGATIVE_INFINITY));

    /** Box spanned by two corners, in any order. */
    public static Aabb of(Vec3 a, Vec3 b) {
        return new Aabb(a.min(b), a.max(b));
    }

    public static Aabb fromCenterExtents(Vec3 center, Vec3 halfExtents) {
        return new Aabb(center.sub(halfExtents), center.add(halfExtents));
    }

    public boolean isEmpty() {
        return min.x() > max.x() || min.y() > max.y() || min.z() > max.z();
    }

    public Vec3 center() {
        return min.interpolate(max, 0.5);
    }

    public Vec3 size() {
        return max.sub(min);
    }

    /** Half of {@link #size()}. */
    public Vec3 extents() {
        return size().scale(0.5);
    }

    public double volume() {
        Vec3 s = size();
        return s.x() * s.y() * s.z();
    }

    /** Surface area, e.g. for bounding volume hierarchy cost estimates. */
    public double surfaceArea() {
        Vec3 s = size();
        return 2 * (s.x() * s.y() + s.y() * s.z() + s.z() * s.x());
    }

    // ---------------------------------------------------------------- queries

    public boolean contains(Vec3 p) {
        return Intersections.pointAabb(p, min, max);
    }

    public boolean contains(Aabb other) {
        return contains(other.min) && contains(other.max);
    }

    public boolean intersects(Aabb other) {
        return Intersections.aabbAabb(min, max, other.min, other.max);
    }

    /** The closest point inside the box. */
    public Vec3 closestPoint(Vec3 p) {
        return p.clamp(min, max);
    }

    /** Distance to {@code p}; 0 inside. */
    public double distance(Vec3 p) {
        return closestPoint(p).distance(p);
    }

    /**
     * Parameter where the ray enters the box (0 if it starts inside), or {@link Double#POSITIVE_INFINITY} if it misses.
     */
    public double intersect(Ray ray) {
        return Intersections.rayAabb(ray.origin(), ray.direction(), min, max);
    }

    // ---------------------------------------------------------------- construction

    public Aabb union(Aabb other) {
        return new Aabb(min.min(other.min), max.max(other.max));
    }

    /** The smallest box containing this box and {@code p}. */
    public Aabb include(Vec3 p) {
        return new Aabb(min.min(p), max.max(p));
    }

    /** Grown by {@code margin} on every side (shrunk if negative). */
    public Aabb expand(double margin) {
        Vec3 m = Vec3.splat(margin);
        return new Aabb(min.sub(m), max.add(m));
    }

    /** The box containing this box after moving it into world space with {@code transform}. */
    public Aabb transform(Transform transform) {
        return transform(transform.toMat4());
    }

    /** The box containing this box after transforming it by an affine matrix (Arvo's method). */
    public Aabb transform(Mat4 m) {
        Vec3 t = m.extractTranslation();
        return new Aabb(
            new Vec3(
                t.x() + low(m.m00(), min.x(), max.x()) + low(m.m01(), min.y(), max.y()) + low(m.m02(), min.z(), max.z()),
                t.y() + low(m.m10(), min.x(), max.x()) + low(m.m11(), min.y(), max.y()) + low(m.m12(), min.z(), max.z()),
                t.z() + low(m.m20(), min.x(), max.x()) + low(m.m21(), min.y(), max.y()) + low(m.m22(), min.z(), max.z())),
            new Vec3(
                t.x() + high(m.m00(), min.x(), max.x()) + high(m.m01(), min.y(), max.y()) + high(m.m02(), min.z(), max.z()),
                t.y() + high(m.m10(), min.x(), max.x()) + high(m.m11(), min.y(), max.y()) + high(m.m12(), min.z(), max.z()),
                t.z() + high(m.m20(), min.x(), max.x()) + high(m.m21(), min.y(), max.y()) + high(m.m22(), min.z(), max.z())));
    }

    private static double low(double factor, double a, double b) {
        return Math.min(factor * a, factor * b);
    }

    private static double high(double factor, double a, double b) {
        return Math.max(factor * a, factor * b);
    }
}
