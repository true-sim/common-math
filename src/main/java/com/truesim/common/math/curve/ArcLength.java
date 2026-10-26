package com.truesim.common.math.curve;

import com.truesim.common.math.Linear;
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

    private ArcLength(Curve<T> curve, double[] lengths) {
        this.curve = curve;
        this.lengths = lengths;
    }

    /**
     * Measures {@code curve} with {@code samples} straight pieces; more samples are more accurate.
     */
    public static <T extends Linear<T>> ArcLength<T> of(Curve<T> curve, int samples) {
        if (samples < 1) {
            throw new IllegalArgumentException("Need at least one sample, got " + samples);
        }
        double[] lengths = new double[samples + 1];
        T previous = curve.at(0);
        for (int i = 1; i <= samples; i++) {
            T next = curve.at((double) i / samples);
            lengths[i] = lengths[i - 1] + previous.distance(next);
            previous = next;
        }
        return new ArcLength<>(curve, lengths);
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

    /** The point at {@code distance} from the start, clamped to the curve. */
    public T at(double distance) {
        return curve.at(parameterAt(distance));
    }
}
