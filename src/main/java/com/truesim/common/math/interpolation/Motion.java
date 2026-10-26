package com.truesim.common.math.interpolation;

import com.truesim.common.math.Linear;

/**
 * A value and its velocity, as moved by a {@link Spring}.
 *
 * @param <T> any linear value: vectors, colors, {@link com.truesim.common.math.Scalar}
 */
public record Motion<T extends Linear<T>>(T value, T velocity) {

    /** At rest at {@code value}. */
    public static <T extends Linear<T>> Motion<T> at(T value) {
        return new Motion<>(value, value.scale(0));
    }
}
