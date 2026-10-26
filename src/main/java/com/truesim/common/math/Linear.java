package com.truesim.common.math;

/**
 * A value in a vector space: it can be added, subtracted and scaled. Curves and springs work on any linear type.
 *
 * @param <T> the implementing type
 */
public interface Linear<T extends Linear<T>> extends Interpolatable<T> {

    T add(T other);

    T sub(T other);

    T scale(double factor);

    double dot(T other);

    /** Euclidean distance to {@code other}. */
    double distance(T other);
}
