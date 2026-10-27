package com.truesim.common.math.numeric;

import com.truesim.common.math.JitInlining;
import java.util.Objects;

/**
 * Up to three real roots in ascending order; {@code count} says how many of {@code r0}, {@code r1}, {@code r2} are
 * valid (unused ones are NaN).
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Roots(int count, double r0, double r1, double r2) {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    public static final Roots NONE = new Roots(0, Double.NaN, Double.NaN, Double.NaN);

    static Roots of(double r0) {
        return new Roots(1, r0, Double.NaN, Double.NaN);
    }

    static Roots of(double r0, double r1) {
        return new Roots(2, Math.min(r0, r1), Math.max(r0, r1), Double.NaN);
    }

    static Roots of(double r0, double r1, double r2) {
        return new Roots(3, r0, r1, r2);
    }

    /** Root {@code index}, from 0 to {@code count - 1}. */
    public double get(int index) {
        return switch (Objects.checkIndex(index, count)) {
            case 0 -> r0;
            case 1 -> r1;
            default -> r2;
        };
    }

    /** The smallest root that is at least {@code min}, or {@link Double#POSITIVE_INFINITY} if there is none. */
    public double smallestAtLeast(double min) {
        for (int i = 0; i < count; i++) {
            if (get(i) >= min) {
                return get(i);
            }
        }
        return Double.POSITIVE_INFINITY;
    }
}
