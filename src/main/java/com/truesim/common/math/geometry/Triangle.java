package com.truesim.common.math.geometry;

import com.truesim.common.math.JitInlining;
import com.truesim.common.math.Mat4;
import com.truesim.common.math.Transform;
import com.truesim.common.math.Vec3;

/**
 * Immutable triangle. Its front side is where {@code a}, {@code b}, {@code c} appear counter-clockwise.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Triangle(Vec3 a, Vec3 b, Vec3 c) {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    /** Unit normal of the front side. */
    public Vec3 normal() {
        return b.sub(a).cross(c.sub(a)).normalize();
    }

    public double area() {
        return 0.5 * b.sub(a).cross(c.sub(a)).length();
    }

    public Vec3 centroid() {
        return a.add(b).add(c).scale(1.0 / 3);
    }

    public Plane plane() {
        return Plane.fromPoints(a, b, c);
    }

    /** This triangle moved into world space with {@code transform}. */
    public Triangle transform(Transform transform) {
        return new Triangle(transform.transformPoint(a), transform.transformPoint(b), transform.transformPoint(c));
    }

    /** This triangle transformed by an affine matrix. */
    public Triangle transform(Mat4 matrix) {
        return new Triangle(matrix.transformPoint(a), matrix.transformPoint(b), matrix.transformPoint(c));
    }

    public Aabb bounds() {
        return new Aabb(a.min(b).min(c), a.max(b).max(c));
    }

    /**
     * Barycentric coordinates {@code (u, v, w)} of {@code p} projected onto the triangle's plane:
     * {@code p == a * u + b * v + c * w}. All three are in [0, 1] when the point is inside.
     */
    public Vec3 barycentric(Vec3 p) {
        Vec3 v0 = b.sub(a);
        Vec3 v1 = c.sub(a);
        Vec3 v2 = p.sub(a);
        double d00 = v0.dot(v0);
        double d01 = v0.dot(v1);
        double d11 = v1.dot(v1);
        double d20 = v2.dot(v0);
        double d21 = v2.dot(v1);
        double denominator = d00 * d11 - d01 * d01;
        double v = (d11 * d20 - d01 * d21) / denominator;
        double w = (d00 * d21 - d01 * d20) / denominator;
        return new Vec3(1 - v - w, v, w);
    }

    /**
     * Parameter where the ray hits the triangle from either side (Moeller-Trumbore), or
     * {@link Double#POSITIVE_INFINITY} if it misses.
     */
    public double intersect(Ray ray) {
        return Intersections.rayTriangle(ray.origin(), ray.direction(), a, b, c);
    }

    /** The closest point on the triangle; also correct for zero-area triangles. */
    public Vec3 closestPoint(Vec3 p) {
        Vec3 n = b.sub(a).cross(c.sub(a));
        double nn = n.lengthSquared();
        if (nn > 0) {
            Vec3 projected = p.addScaled(n, -n.dot(p.sub(a)) / nn);
            Vec3 weights = barycentric(projected);
            if (weights.x() >= 0 && weights.y() >= 0 && weights.z() >= 0) {
                return projected; // inside: straight down onto the face
            }
        }
        return closestOnEdges(p); // outside, or a zero-area triangle: the nearest edge
    }

    private Vec3 closestOnEdges(Vec3 p) {
        Vec3 best = new Segment(a, b).closestPoint(p);
        Vec3 onBc = new Segment(b, c).closestPoint(p);
        Vec3 onCa = new Segment(c, a).closestPoint(p);
        best = onBc.distanceSquared(p) < best.distanceSquared(p) ? onBc : best;
        return onCa.distanceSquared(p) < best.distanceSquared(p) ? onCa : best;
    }
}
