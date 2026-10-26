package com.truesim.common.math;

/**
 * A {@code double} as a {@link Linear} value, so tweens, keyframes, curves and springs also work on plain numbers.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Scalar(double value) implements Linear<Scalar> {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    public static final Scalar ZERO = new Scalar(0);
    public static final Scalar ONE = new Scalar(1);

    public static Scalar of(double value) {
        return new Scalar(value);
    }

    @Override
    public Scalar add(Scalar other) {
        return new Scalar(value + other.value);
    }

    @Override
    public Scalar sub(Scalar other) {
        return new Scalar(value - other.value);
    }

    @Override
    public Scalar scale(double factor) {
        return new Scalar(value * factor);
    }

    @Override
    public double dot(Scalar other) {
        return value * other.value;
    }

    @Override
    public double distance(Scalar other) {
        return Math.abs(value - other.value);
    }

    @Override
    public Scalar interpolate(Scalar to, double t) {
        return new Scalar(Maths.lerp(value, to.value, t));
    }
}
