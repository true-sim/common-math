package com.truesim.common.math.curve;

import com.truesim.common.math.Linear;

/**
 * Cubic Bezier curve from {@code p0} to {@code p3} with control points {@code p1} and {@code p2}.
 *
 * @param <T> the value type
 */
public record CubicBezier<T extends Linear<T>>(T p0, T p1, T p2, T p3) implements Curve<T> {

    @Override
    public T at(double t) {
        double u = 1 - t;
        return p0.scale(u * u * u)
            .add(p1.scale(3 * u * u * t))
            .add(p2.scale(3 * u * t * t))
            .add(p3.scale(t * t * t));
    }

    @Override
    public T derivative(double t) {
        double u = 1 - t;
        return p1.sub(p0).scale(3 * u * u)
            .add(p2.sub(p1).scale(6 * u * t))
            .add(p3.sub(p2).scale(3 * t * t));
    }

    /** The part of this curve before {@code t}, as its own Bezier curve (de Casteljau). */
    public CubicBezier<T> left(double t) {
        T a = p0.interpolate(p1, t);
        T b = p1.interpolate(p2, t);
        T c = p2.interpolate(p3, t);
        T ab = a.interpolate(b, t);
        T bc = b.interpolate(c, t);
        return new CubicBezier<>(p0, a, ab, ab.interpolate(bc, t));
    }

    /** The part of this curve after {@code t}. */
    public CubicBezier<T> right(double t) {
        T a = p0.interpolate(p1, t);
        T b = p1.interpolate(p2, t);
        T c = p2.interpolate(p3, t);
        T ab = a.interpolate(b, t);
        T bc = b.interpolate(c, t);
        return new CubicBezier<>(ab.interpolate(bc, t), bc, c, p3);
    }
}
