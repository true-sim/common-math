package com.truesim.common.math.numeric;

import com.truesim.common.math.Linear;

/**
 * The acceleration of a body at a position and velocity: forces divided by mass, e.g. gravity, drag and springs.
 *
 * @param <T> the space: {@link com.truesim.common.math.Vec3}, {@link com.truesim.common.math.Vec2}, ...
 */
@FunctionalInterface
public interface Acceleration<T extends Linear<T>> {

    T at(T position, T velocity, double time);
}
