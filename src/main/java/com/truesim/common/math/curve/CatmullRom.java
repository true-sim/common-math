package com.truesim.common.math.curve;

import com.truesim.common.math.Linear;
import com.truesim.common.math.Maths;
import java.util.List;

/**
 * Smooth spline through all of its points, e.g. a race track or camera path. {@code t = 0} is the first point and
 * {@code t = 1} the last (or the first again, if closed); each segment gets an equal share of {@code t}. Use
 * {@link ArcLength} to move along it at constant speed.
 *
 * <pre>{@code
 * CatmullRom<Vec3> track = CatmullRom.of(points, Parametrization.CENTRIPETAL, true);
 * }</pre>
 *
 * @param <T> the value type
 */
public final class CatmullRom<T extends Linear<T>> implements Curve<T> {

    private final Object[] starts;
    private final Object[] startTangents;
    private final Object[] ends;
    private final Object[] endTangents;
    private final int segments;

    private CatmullRom(Object[] starts, Object[] startTangents, Object[] ends, Object[] endTangents) {
        this.starts = starts;
        this.startTangents = startTangents;
        this.ends = ends;
        this.endTangents = endTangents;
        this.segments = starts.length;
    }

    /**
     * Spline through {@code points}.
     *
     * @param closed whether the spline loops back from the last point to the first
     */
    public static <T extends Linear<T>> CatmullRom<T> of(List<T> points, Parametrization parametrization, boolean closed) {
        int n = points.size();
        if (n < 2) {
            throw new IllegalArgumentException("A spline needs at least two points, got " + n);
        }
        int segments = closed ? n : n - 1;
        Object[] starts = new Object[segments];
        Object[] startTangents = new Object[segments];
        Object[] ends = new Object[segments];
        Object[] endTangents = new Object[segments];
        double alpha = parametrization.alpha();
        for (int i = 0; i < segments; i++) {
            T p0 = point(points, i - 1, closed);
            T p1 = points.get(i);
            T p2 = point(points, i + 1, closed);
            T p3 = point(points, i + 2, closed);
            double d01 = knot(p0, p1, alpha);
            double d12 = knot(p1, p2, alpha);
            double d23 = knot(p2, p3, alpha);
            // Tangents of the non-uniform Catmull-Rom segment, rescaled to the segment's [0, 1] parameter.
            T m1 = p1.sub(p0).scale(1 / d01).sub(p2.sub(p0).scale(1 / (d01 + d12))).add(p2.sub(p1).scale(1 / d12)).scale(d12);
            T m2 = p2.sub(p1).scale(1 / d12).sub(p3.sub(p1).scale(1 / (d12 + d23))).add(p3.sub(p2).scale(1 / d23)).scale(d12);
            starts[i] = p1;
            startTangents[i] = m1;
            ends[i] = p2;
            endTangents[i] = m2;
        }
        return new CatmullRom<>(starts, startTangents, ends, endTangents);
    }

    /** Point {@code i}, wrapping around for closed splines and mirroring the end points for open ones. */
    private static <T extends Linear<T>> T point(List<T> points, int i, boolean closed) {
        int n = points.size();
        if (closed) {
            return points.get(Math.floorMod(i, n));
        }
        if (i < 0) {
            return points.get(0).scale(2).sub(points.get(1));
        }
        if (i >= n) {
            return points.get(n - 1).scale(2).sub(points.get(n - 2));
        }
        return points.get(i);
    }

    private static <T extends Linear<T>> double knot(T a, T b, double alpha) {
        double d = Maths.pow(a.distance(b), alpha);
        return d > 0 ? d : 1; // coincident points: fall back to uniform spacing
    }

    /** Number of segments: one fewer than the points for open splines, as many as the points for closed ones. */
    public int segments() {
        return segments;
    }

    @Override
    public T at(double t) {
        double u = Maths.clamp01(t) * segments;
        int i = Math.min((int) u, segments - 1);
        return segment(i).at(u - i);
    }

    @Override
    public T derivative(double t) {
        double u = Maths.clamp01(t) * segments;
        int i = Math.min((int) u, segments - 1);
        return segment(i).derivative(u - i).scale(segments);
    }

    /** Segment {@code index} as a Hermite curve over its own [0, 1]. */
    @SuppressWarnings("unchecked")
    public Hermite<T> segment(int index) {
        return new Hermite<>((T) starts[index], (T) startTangents[index], (T) ends[index], (T) endTangents[index]);
    }
}
