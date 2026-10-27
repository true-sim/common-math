package com.truesim.common.math.geometry;

import com.truesim.common.math.JitInlining;
import com.truesim.common.math.Mat4;
import com.truesim.common.math.Transform;
import com.truesim.common.math.Vec3;

/**
 * Immutable plane of all points {@code p} with {@code normal.dot(p) == distance}. The normal points to the positive
 * ("front") side and is unit length when built with the factories.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Plane(Vec3 normal, double distance) {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    /** Plane through {@code point} facing {@code normal} (normalized). */
    public static Plane fromPointNormal(Vec3 point, Vec3 normal) {
        Vec3 n = normal.normalize();
        return new Plane(n, n.dot(point));
    }

    /** Plane through three points; the front side is where they appear counter-clockwise. */
    public static Plane fromPoints(Vec3 a, Vec3 b, Vec3 c) {
        return fromPointNormal(a, b.sub(a).cross(c.sub(a)));
    }

    /** Positive in front of the plane, negative behind it. */
    public double signedDistance(Vec3 point) {
        return normal.dot(point) - distance;
    }

    /** The closest point on the plane. */
    public Vec3 project(Vec3 point) {
        return point.addScaled(normal, -signedDistance(point));
    }

    /** This plane moved into world space with {@code transform}. */
    public Plane transform(Transform transform) {
        Vec3 n = transform.rotation().rotate(normal.div(transform.scale())).normalize();
        return new Plane(n, n.dot(transform.transformPoint(normal.scale(distance))));
    }

    /** This plane transformed by an affine matrix. */
    public Plane transform(Mat4 matrix) {
        Vec3 n = matrix.normalMatrix().transform(normal).normalize();
        return new Plane(n, n.dot(matrix.transformPoint(normal.scale(distance))));
    }

    /** The same plane facing the other way. */
    public Plane flip() {
        return new Plane(normal.negate(), -distance);
    }

    /** Parameter of the ray's intersection, or {@link Double#POSITIVE_INFINITY} if it misses (or runs parallel). */
    public double intersect(Ray ray) {
        return Intersections.rayPlane(ray.origin(), ray.direction(), normal, distance);
    }
}
