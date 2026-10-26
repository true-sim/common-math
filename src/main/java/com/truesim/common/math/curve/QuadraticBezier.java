package com.truesim.common.math.curve;

import com.truesim.common.math.Linear;

/**
 * Quadratic Bezier curve from {@code p0} to {@code p2}, pulled towards the control point {@code p1}.
 *
 * @param <T> the value type
 */
public record QuadraticBezier<T extends Linear<T>>(T p0, T p1, T p2) implements Curve<T> {

    @Override
    public T at(double t) {
        double u = 1 - t;
        return p0.scale(u * u).add(p1.scale(2 * u * t)).add(p2.scale(t * t));
    }

    @Override
    public T derivative(double t) {
        double u = 1 - t;
        return p1.sub(p0).scale(2 * u).add(p2.sub(p1).scale(2 * t));
    }
}
