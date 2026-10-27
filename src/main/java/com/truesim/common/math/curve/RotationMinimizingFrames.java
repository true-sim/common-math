package com.truesim.common.math.curve;

import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;

/**
 * Orientations along a 3D curve that twist as little as possible (double reflection method, Wang et al. 2008):
 * the right frames for road and track meshes, tubes and camera rails. Unlike Frenet frames they are defined on
 * straight parts and do not flip at inflection points.
 *
 * <p>On a closed curve the last frame may be rolled relative to the first; correct it with {@link Frame#banked} if
 * the seam matters.
 */
public final class RotationMinimizingFrames {

    private final Curve<Vec3> curve;
    private final double[] ups;
    private final int samples;

    private RotationMinimizingFrames(Curve<Vec3> curve, double[] ups, int samples) {
        this.curve = curve;
        this.ups = ups;
        this.samples = samples;
    }

    /**
     * Computes frames at {@code samples} intervals, starting with {@code initialUp} (made perpendicular to the curve).
     */
    public static RotationMinimizingFrames of(Curve<Vec3> curve, int samples, Vec3 initialUp) {
        if (samples < 1) {
            throw new IllegalArgumentException("Need at least one sample, got " + samples);
        }
        double[] ups = new double[3 * (samples + 1)];
        Vec3 x0 = curve.at(0);
        Vec3 t0 = curve.derivative(0).normalize();
        Vec3 r0 = initialUp.reject(t0).normalize();
        if (r0.lengthSquared() == 0) {
            r0 = t0.anyPerpendicular(); // initialUp was parallel to the curve
        }
        store(ups, 0, r0);
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / samples;
            Vec3 x1 = curve.at(t);
            Vec3 t1 = curve.derivative(t).normalize();
            Vec3 v1 = x1.sub(x0);
            double c1 = v1.lengthSquared();
            if (c1 > 0) {
                // Reflect across the plane bisecting the step, then across the one that matches the new tangent.
                Vec3 rl = r0.addScaled(v1, -2 / c1 * v1.dot(r0));
                Vec3 tl = t0.addScaled(v1, -2 / c1 * v1.dot(t0));
                Vec3 v2 = t1.sub(tl);
                double c2 = v2.lengthSquared();
                r0 = (c2 > 0 ? rl.addScaled(v2, -2 / c2 * v2.dot(rl)) : rl).normalize();
            }
            store(ups, i, r0);
            x0 = x1;
            t0 = t1;
        }
        return new RotationMinimizingFrames(curve, ups, samples);
    }

    private static void store(double[] ups, int i, Vec3 up) {
        ups[3 * i] = up.x();
        ups[3 * i + 1] = up.y();
        ups[3 * i + 2] = up.z();
    }

    public Curve<Vec3> curve() {
        return curve;
    }

    /** The frame at curve parameter {@code t} in [0, 1]. */
    public Frame at(double t) {
        double clamped = Maths.clamp01(t);
        double u = clamped * samples;
        int i = Math.min((int) u, samples - 1);
        double s = u - i;
        Vec3 forward = curve.derivative(clamped).normalize();
        Vec3 up = new Vec3(
            Maths.lerp(ups[3 * i], ups[3 * i + 3], s),
            Maths.lerp(ups[3 * i + 1], ups[3 * i + 4], s),
            Maths.lerp(ups[3 * i + 2], ups[3 * i + 5], s)).reject(forward).normalize();
        return new Frame(curve.at(clamped), forward, up, forward.cross(up));
    }
}
