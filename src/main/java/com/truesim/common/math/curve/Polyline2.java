package com.truesim.common.math.curve;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.geometry.Polygons;
import java.util.Arrays;

/**
 * A polyline in the plane with its arc length: points along it by distance, its segments' unit directions, the point
 * nearest another, values interpolated along it; and the operations roads, paths and rivers are made with: Chaikin's
 * corner cutting and even resampling. The arrays are its own, read directly ({@link #x}, {@link #y}, {@link #s}).
 *
 * <pre>{@code
 * Polyline2 line = Polyline2.chaikin(new Polyline2(xs, ys), 3).resample(2);   // smooth, a point every 2 m or less
 * double[] p = new double[4];
 * line.at(line.length() / 2, p);                                             // x, y, direction x, direction y
 * }</pre>
 */
public final class Polyline2 {

    /** The points' x. */
    public final double[] x;
    /** The points' y. */
    public final double[] y;
    /** The arc length at each point: 0 at the first. */
    public final double[] s;

    /** The polyline through ({@code x[i]}, {@code y[i]}), its arc length measured (the arrays are kept, not copied). */
    @AllowAllocation("the arc lengths, once")
    public Polyline2(double[] x, double[] y) {
        if (x.length == 0 || x.length != y.length) {
            throw new IllegalArgumentException("A polyline needs points, as many x as y: " + x.length + ", "
                + y.length);
        }
        this.x = x;
        this.y = y;
        s = new double[x.length];
        for (int i = 1; i < x.length; i++) {
            s[i] = s[i - 1] + Polygons.length(x[i] - x[i - 1], y[i] - y[i - 1]);
        }
    }

    /** How many points. */
    public int count() {
        return x.length;
    }

    /** Its length. */
    public double length() {
        return s[s.length - 1];
    }

    /** The segment {@code at} lies on: the last point at or before it (0 to count - 2; 0 for a single point). */
    public int segment(double at) {
        int k = Arrays.binarySearch(s, at);
        if (k < 0) {
            k = -k - 2;
        }
        return Math.clamp(k, 0, Math.max(0, x.length - 2));
    }

    /**
     * The point {@code at} along (clamped to the polyline) into {@code out}: x, y, then its segment's unit direction
     * (+x for a single point or a segment of no length).
     */
    public void at(double at, double[] out) {
        if (x.length == 1) {
            out[0] = x[0];
            out[1] = y[0];
            out[2] = 1;
            out[3] = 0;
            return;
        }
        int k = segment(at);
        double l = s[k + 1] - s[k];
        double t = l > 0 ? Math.clamp((at - s[k]) / l, 0, 1) : 0;
        double dx = x[k + 1] - x[k];
        double dy = y[k + 1] - y[k];
        out[0] = x[k] + t * dx;
        out[1] = y[k] + t * dy;
        out[2] = l > 0 ? dx / l : 1;
        out[3] = l > 0 ? dy / l : 0;
    }

    /** {@code values} (one per point) interpolated {@code at} along. */
    public double value(double[] values, double at) {
        if (values.length == 1) {
            return values[0];
        }
        int k = segment(at);
        double l = s[k + 1] - s[k];
        double t = l > 0 ? Math.clamp((at - s[k]) / l, 0, 1) : 0;
        return values[k] + t * (values[k + 1] - values[k]);
    }

    /**
     * The distance along of the point of the polyline nearest ({@code px}, {@code py}), and that point into
     * {@code out}: x, y, and its distance from ({@code px}, {@code py}).
     */
    public double nearest(double px, double py, double[] out) {
        out[0] = x[0];
        out[1] = y[0];
        out[2] = Polygons.length(x[0] - px, y[0] - py);
        double along = 0;
        for (int i = 1; i < x.length; i++) {
            double t = Polygons.project(px, py, x[i - 1], y[i - 1], x[i], y[i]);
            double qx = x[i - 1] + t * (x[i] - x[i - 1]);
            double qy = y[i - 1] + t * (y[i] - y[i - 1]);
            double d = Polygons.length(qx - px, qy - py);
            if (d < out[2]) {
                along = s[i - 1] + t * (s[i] - s[i - 1]);
                out[0] = qx;
                out[1] = qy;
                out[2] = d;
            }
        }
        return along;
    }

    /**
     * Chaikin's corner cutting {@code iterations} times, the ends kept: a smooth curve within the polyline's hull
     * (each corner replaced by points a quarter and three quarters along its segments).
     */
    @AllowAllocation("the cut polyline")
    public static Polyline2 chaikin(Polyline2 line, int iterations) {
        double[] x = line.x;
        double[] y = line.y;
        for (int it = 0; it < iterations && x.length > 2; it++) {
            int m = x.length;
            double[] nx = new double[2 * (m - 1)];
            double[] ny = new double[2 * (m - 1)];
            int k = 0;
            nx[k] = x[0];
            ny[k++] = y[0];
            for (int i = 0; i + 1 < m; i++) {
                if (i > 0) {
                    nx[k] = 0.75 * x[i] + 0.25 * x[i + 1];
                    ny[k++] = 0.75 * y[i] + 0.25 * y[i + 1];
                }
                if (i + 2 < m) {
                    nx[k] = 0.25 * x[i] + 0.75 * x[i + 1];
                    ny[k++] = 0.25 * y[i] + 0.75 * y[i + 1];
                }
            }
            nx[k] = x[m - 1];
            ny[k] = y[m - 1];
            x = nx;
            y = ny;
        }
        return new Polyline2(x, y);
    }

    /** The polyline resampled evenly, its points {@code step} apart or a little less, its ends kept. */
    @AllowAllocation("the resampled polyline")
    public Polyline2 resample(double step) {
        double length = length();
        int segments = Math.max(1, (int) Math.ceil(length / step - 1e-9));
        double[] rx = new double[segments + 1];
        double[] ry = new double[segments + 1];
        double[] p = new double[4];
        for (int k = 0; k < segments; k++) {
            at(length * k / segments, p);
            rx[k] = p[0];
            ry[k] = p[1];
        }
        rx[segments] = x[x.length - 1];
        ry[segments] = y[y.length - 1];
        return new Polyline2(rx, ry);
    }

    /** Points {@code from} to {@code to} (inclusive), copied. */
    @AllowAllocation("the part")
    public Polyline2 part(int from, int to) {
        return new Polyline2(Arrays.copyOfRange(x, from, to + 1), Arrays.copyOfRange(y, from, to + 1));
    }
}
