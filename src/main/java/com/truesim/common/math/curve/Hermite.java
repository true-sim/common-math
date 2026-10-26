package com.truesim.common.math.curve;

import com.truesim.common.math.Linear;

/**
 * Cubic Hermite curve from {@code p0} with tangent {@code m0} to {@code p1} with tangent {@code m1}.
 *
 * @param <T> the value type
 */
public record Hermite<T extends Linear<T>>(T p0, T m0, T p1, T m1) implements Curve<T> {

    @Override
    public T at(double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return p0.scale(2 * t3 - 3 * t2 + 1)
            .add(m0.scale(t3 - 2 * t2 + t))
            .add(p1.scale(-2 * t3 + 3 * t2))
            .add(m1.scale(t3 - t2));
    }

    @Override
    public T derivative(double t) {
        double t2 = t * t;
        return p0.scale(6 * t2 - 6 * t)
            .add(m0.scale(3 * t2 - 4 * t + 1))
            .add(p1.scale(-6 * t2 + 6 * t))
            .add(m1.scale(3 * t2 - 2 * t));
    }
}
