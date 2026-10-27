package com.truesim.common.math.geometry;

import com.truesim.common.math.JitInlining;
import com.truesim.common.math.Mat4;
import com.truesim.common.math.Transform;
import com.truesim.common.math.Vec3;

/**
 * Immutable line segment from {@code start} to {@code end}.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Segment(Vec3 start, Vec3 end) {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    /** This segment moved into world space with {@code transform}. */
    public Segment transform(Transform transform) {
        return new Segment(transform.transformPoint(start), transform.transformPoint(end));
    }

    /** This segment transformed by an affine matrix. */
    public Segment transform(Mat4 matrix) {
        return new Segment(matrix.transformPoint(start), matrix.transformPoint(end));
    }

    public double length() {
        return start.distance(end);
    }

    /** The point at {@code t} in [0, 1] from start to end. */
    public Vec3 at(double t) {
        return start.interpolate(end, t);
    }

    /** The {@code t} in [0, 1] of the closest point to {@code p}. */
    public double closestParameter(Vec3 p) {
        return Intersections.segmentClosestParameter(start, end, p);
    }

    public Vec3 closestPoint(Vec3 p) {
        return at(closestParameter(p));
    }

    public double distance(Vec3 p) {
        return closestPoint(p).distance(p);
    }
}
