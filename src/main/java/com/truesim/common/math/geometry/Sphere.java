package com.truesim.common.math.geometry;

import com.truesim.common.math.JitInlining;
import com.truesim.common.math.Mat4;
import com.truesim.common.math.Transform;
import com.truesim.common.math.Vec3;

/**
 * Immutable sphere (the solid ball, including its surface).
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Sphere(Vec3 center, double radius) {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    public boolean contains(Vec3 p) {
        return Intersections.pointSphere(p, center, radius);
    }

    public boolean intersects(Sphere other) {
        return Intersections.sphereSphere(center, radius, other.center, other.radius);
    }

    public boolean intersects(Aabb box) {
        return Intersections.sphereAabb(center, radius, box.min(), box.max());
    }

    /** The closest point on or in the sphere. */
    public Vec3 closestPoint(Vec3 p) {
        Vec3 offset = p.sub(center);
        double lengthSquared = offset.lengthSquared();
        boolean inside = lengthSquared <= radius * radius;
        double f = inside ? 0 : radius / Math.sqrt(lengthSquared);
        return new Vec3(
            inside ? p.x() : Math.fma(offset.x(), f, center.x()),
            inside ? p.y() : Math.fma(offset.y(), f, center.y()),
            inside ? p.z() : Math.fma(offset.z(), f, center.z()));
    }

    /** This sphere moved into world space; non-uniform scale grows the radius by the largest scale factor. */
    public Sphere transform(Transform transform) {
        Vec3 s = transform.scale().abs();
        return new Sphere(transform.transformPoint(center), radius * Math.max(s.x(), Math.max(s.y(), s.z())));
    }

    /** This sphere transformed by an affine matrix; non-uniform scale grows the radius by the largest scale factor. */
    public Sphere transform(Mat4 matrix) {
        Vec3 s = matrix.extractScale();
        return new Sphere(matrix.transformPoint(center), radius * Math.max(s.x(), Math.max(s.y(), s.z())));
    }

    public Aabb bounds() {
        return Aabb.fromCenterExtents(center, Vec3.splat(radius));
    }

    /**
     * Parameter where the ray hits the surface (where it exits, if it starts inside), or
     * {@link Double#POSITIVE_INFINITY} if it misses.
     */
    public double intersect(Ray ray) {
        return Intersections.raySphere(ray.origin(), ray.direction(), center, radius);
    }
}
