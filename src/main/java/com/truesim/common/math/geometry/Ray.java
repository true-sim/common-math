package com.truesim.common.math.geometry;

import com.truesim.common.math.JitInlining;
import com.truesim.common.math.Mat4;
import com.truesim.common.math.Transform;
import com.truesim.common.math.Vec3;

/**
 * Immutable half-line from {@code origin} along {@code direction}. With a unit direction, ray parameters are
 * distances.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Ray(Vec3 origin, Vec3 direction) {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    /** Ray from {@code origin} through {@code target}, with a unit direction. */
    public static Ray through(Vec3 origin, Vec3 target) {
        return new Ray(origin, target.sub(origin).normalize());
    }

    /** The point at parameter {@code t}. */
    public Vec3 at(double t) {
        return origin.addScaled(direction, t);
    }

    /** This ray moved into the space of {@code transform}; use {@code transform.inverse()} for local-space queries. */
    public Ray transform(Transform transform) {
        return new Ray(transform.transformPoint(origin), transform.transformDirection(direction));
    }

    /** This ray transformed by an affine matrix. */
    public Ray transform(Mat4 matrix) {
        return new Ray(matrix.transformPoint(origin), matrix.transformDirection(direction));
    }

    /** Distance from this ray (not the infinite line) to {@code point}. */
    public double distance(Vec3 point) {
        double t = Math.max(0, point.sub(origin).dot(direction) / direction.lengthSquared());
        return at(t).distance(point);
    }
}
