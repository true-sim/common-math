package com.truesim.common.math;

/**
 * A value that can be interpolated towards another value of the same type.
 *
 * @param <T> the implementing type
 */
public interface Interpolatable<T extends Interpolatable<T>> {

    /**
     * The value at {@code t} between this value ({@code t = 0}) and {@code to} ({@code t = 1}).
     * Vectors and colors interpolate linearly, quaternions spherically and matrices by translation, rotation and scale.
     */
    T interpolate(T to, double t);
}
