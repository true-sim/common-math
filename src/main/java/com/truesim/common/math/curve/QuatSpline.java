package com.truesim.common.math.curve;

import com.truesim.common.math.Maths;
import com.truesim.common.math.Quat;
import java.util.List;

/**
 * Smooth rotation through all of its keys (spherical quadrangle interpolation, "squad"): unlike chained slerps, the
 * angular velocity has no jumps at the keys. {@code t = 0} is the first key and {@code t = 1} the last (or the first
 * again, if closed); each segment gets an equal share of {@code t}.
 */
public final class QuatSpline {

    private final Quat[] keys;
    private final Quat[] controls;
    private final int segments;

    private QuatSpline(Quat[] keys, Quat[] controls, int segments) {
        this.keys = keys;
        this.controls = controls;
        this.segments = segments;
    }

    /**
     * Spline through {@code rotations}.
     *
     * @param closed whether the spline loops back from the last key to the first
     */
    public static QuatSpline of(List<Quat> rotations, boolean closed) {
        int n = rotations.size();
        if (n < 2) {
            throw new IllegalArgumentException("A spline needs at least two keys, got " + n);
        }
        int count = closed ? n + 1 : n;
        Quat[] keys = new Quat[count];
        for (int i = 0; i < count; i++) {
            Quat q = rotations.get(i % n).normalize();
            // Keep consecutive keys in the same hemisphere, so every segment takes the short way.
            keys[i] = i > 0 && keys[i - 1].dot(q) < 0 ? new Quat(-q.x(), -q.y(), -q.z(), -q.w()) : q;
        }
        Quat[] controls = new Quat[count];
        for (int i = 0; i < count; i++) {
            Quat previous = closed ? keys[Math.floorMod(i - 1, n)] : keys[Math.max(i - 1, 0)];
            Quat next = closed ? keys[(i + 1) % n] : keys[Math.min(i + 1, count - 1)];
            controls[i] = control(previous, keys[i], next);
        }
        return new QuatSpline(keys, controls, count - 1);
    }

    /** Squad inner control point: {@code q * exp(-(log(q^-1 next) + log(q^-1 previous)) / 4)}. */
    private static Quat control(Quat previous, Quat q, Quat next) {
        Quat inverse = q.conjugate();
        Quat a = inverse.mul(next).log();
        Quat b = inverse.mul(previous).log();
        Quat sum = new Quat(-(a.x() + b.x()) / 4, -(a.y() + b.y()) / 4, -(a.z() + b.z()) / 4, 0);
        return q.mul(sum.exp());
    }

    public int segments() {
        return segments;
    }

    /** The rotation at {@code t} in [0, 1]. */
    public Quat at(double t) {
        double u = Maths.clamp01(t) * segments;
        int i = Math.min((int) u, segments - 1);
        double s = u - i;
        return keys[i].slerp(keys[i + 1], s).slerp(controls[i].slerp(controls[i + 1], s), 2 * s * (1 - s));
    }
}
