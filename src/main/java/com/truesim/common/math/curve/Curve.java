package com.truesim.common.math.curve;

import com.truesim.common.math.Linear;

/**
 * A parametric curve over {@code t} in [0, 1] through any linear space: {@link com.truesim.common.math.Vec2},
 * {@link com.truesim.common.math.Vec3}, {@link com.truesim.common.math.color.Color}, ...
 *
 * @param <T> the value type
 */
public interface Curve<T extends Linear<T>> {

    /** The point at {@code t}. */
    T at(double t);

    /** The first derivative (velocity) with respect to {@code t}. Its direction is the curve's tangent. */
    T derivative(double t);
}
