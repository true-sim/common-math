package com.truesim.common.math.geometry;

import com.truesim.common.math.JitInlining;
import com.truesim.common.math.Mat4;
import com.truesim.common.math.Transform;
import com.truesim.common.math.Vec3;

/**
 * Immutable capsule: all points within {@code radius} of the segment from {@code start} to {@code end}. The usual
 * shape for characters, limbs and rounded colliders.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Capsule(Vec3 start, Vec3 end, double radius) {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    public Segment axis() {
        return new Segment(start, end);
    }

    public Aabb bounds() {
        Vec3 r = Vec3.splat(radius);
        return new Aabb(start.min(end).sub(r), start.max(end).add(r));
    }

    public boolean contains(Vec3 p) {
        return Intersections.pointCapsule(p, start, end, radius);
    }

    /** Distance from the surface to {@code p}: negative inside. */
    public double signedDistance(Vec3 p) {
        return axis().distance(p) - radius;
    }

    /** The closest point on or in the capsule. */
    public Vec3 closestPoint(Vec3 p) {
        return new Sphere(axis().closestPoint(p), radius).closestPoint(p);
    }

    public boolean intersects(Sphere sphere) {
        return Intersections.sphereCapsule(sphere.center(), sphere.radius(), start, end, radius);
    }

    public boolean intersects(Capsule other) {
        return Intersections.capsuleCapsule(start, end, radius, other.start, other.end, other.radius);
    }

    /** Where the ray enters the capsule (0 if it starts inside), or {@link Double#POSITIVE_INFINITY} if it misses. */
    public double intersect(Ray ray) {
        return Intersections.rayCapsule(ray.origin(), ray.direction(), start, end, radius);
    }

    /** This capsule moved into world space; non-uniform scale grows the radius by the largest scale factor. */
    public Capsule transform(Transform transform) {
        Vec3 s = transform.scale().abs();
        return new Capsule(transform.transformPoint(start), transform.transformPoint(end),
            radius * Math.max(s.x(), Math.max(s.y(), s.z())));
    }

    /** This capsule transformed by an affine matrix; non-uniform scale grows the radius by the largest scale factor. */
    public Capsule transform(Mat4 matrix) {
        Vec3 s = matrix.extractScale();
        return new Capsule(matrix.transformPoint(start), matrix.transformPoint(end),
            radius * Math.max(s.x(), Math.max(s.y(), s.z())));
    }
}
