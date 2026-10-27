package com.truesim.common.math.curve;

import com.truesim.common.math.Linear;
import com.truesim.common.math.Maths;
import java.util.Arrays;

/**
 * Arc-length table for a curve: move along it by distance instead of by parameter, e.g. at constant speed.
 *
 * <pre>{@code
 * ArcLength<Vec3> path = ArcLength.of(track, 1024);
 * Vec3 car = path.at(distanceDriven % path.length());
 * }</pre>
 *
 * @param <T> the value type
 */
public final class ArcLength<T extends Linear<T>> {

    private final Curve<T> curve;
    private final double[] lengths;
    private final Object[] points;

    private ArcLength(Curve<T> curve, double[] lengths, Object[] points) {
        this.curve = curve;
        this.lengths = lengths;
        this.points = points;
    }

    /**
     * Measures {@code curve} with {@code samples} straight pieces; more samples are more accurate.
     */
    public static <T extends Linear<T>> ArcLength<T> of(Curve<T> curve, int samples) {
        if (samples < 1) {
            throw new IllegalArgumentException("Need at least one sample, got " + samples);
        }
        double[] lengths = new double[samples + 1];
        Object[] points = new Object[samples + 1];
        T previous = curve.at(0);
        points[0] = previous;
        for (int i = 1; i <= samples; i++) {
            T next = curve.at((double) i / samples);
            lengths[i] = lengths[i - 1] + previous.distance(next);
            points[i] = next;
            previous = next;
        }
        return new ArcLength<>(curve, lengths, points);
    }

    public Curve<T> curve() {
        return curve;
    }

    /** Total length of the curve. */
    public double length() {
        return lengths[lengths.length - 1];
    }

    /** The curve parameter at {@code distance} from the start, clamped to the curve. */
    public double parameterAt(double distance) {
        int samples = lengths.length - 1;
        if (distance <= 0) {
            return 0;
        }
        if (distance >= length()) {
            return 1;
        }
        int index = Arrays.binarySearch(lengths, distance);
        if (index >= 0) {
            return (double) index / samples;
        }
        int next = -index - 1;
        int previous = next - 1;
        double fraction = (distance - lengths[previous]) / (lengths[next] - lengths[previous]);
        return (previous + fraction) / samples;
    }

    /**
     * Distance along the curve to the point nearest {@code point}, e.g. a car's progress along a track. Accurate to
     * the sampling: the curve is treated as straight between samples.
     */
    @SuppressWarnings("unchecked")
    public double nearestDistance(T point) {
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestAlong = 0;
        for (int i = 0; i + 1 < points.length; i++) {
            T a = (T) points[i];
            T chord = ((T) points[i + 1]).sub(a);
            double lengthSquared = chord.dot(chord);
            double s = lengthSquared == 0 ? 0 : Maths.clamp01(point.sub(a).dot(chord) / lengthSquared);
            double distance = a.add(chord.scale(s)).distance(point);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestAlong = lengths[i] + s * (lengths[i + 1] - lengths[i]);
            }
        }
        return bestAlong;
    }

    /** The point on the curve nearest {@code point}; see {@link #nearestDistance}. */
    public T nearestPoint(T point) {
        return at(nearestDistance(point));
    }

    /** The point at {@code distance} from the start, clamped to the curve. */
    public T at(double distance) {
        return curve.at(parameterAt(distance));
    }
}
