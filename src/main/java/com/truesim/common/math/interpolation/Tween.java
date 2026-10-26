package com.truesim.common.math.interpolation;

import com.truesim.common.math.Interpolatable;
import com.truesim.common.math.Maths;

/**
 * Animation from one value to another over {@code duration} (any time unit), shaped by an {@link Easing}.
 * Immutable: ask it for the value at a point in time.
 *
 * <pre>{@code
 * Tween<Quat> turn = Tween.of(from, to, 0.5, Easing.BACK_OUT);
 * Quat now = turn.valueAt(elapsed);
 * }</pre>
 *
 * @param <T> any interpolatable value: vectors, quaternions, matrices, colors, {@link com.truesim.common.math.Scalar}
 */
public record Tween<T extends Interpolatable<T>>(T from, T to, double duration, Easing easing) {

    public Tween {
        if (!(duration >= 0)) {
            throw new IllegalArgumentException("Duration must not be negative: " + duration);
        }
    }

    /** Linear tween. */
    public static <T extends Interpolatable<T>> Tween<T> of(T from, T to, double duration) {
        return new Tween<>(from, to, duration, Easing.LINEAR);
    }

    public static <T extends Interpolatable<T>> Tween<T> of(T from, T to, double duration, Easing easing) {
        return new Tween<>(from, to, duration, easing);
    }

    /** Progress in [0, 1] at {@code time} (before easing). */
    public double progress(double time) {
        return duration == 0 ? 1 : Maths.clamp01(time / duration);
    }

    /** The value at {@code time}: {@code from} before the start, {@code to} after the end. */
    public T valueAt(double time) {
        return from.interpolate(to, easing.apply(progress(time)));
    }

    public boolean isFinished(double time) {
        return time >= duration;
    }

    /** The same animation played backwards. */
    public Tween<T> reversed() {
        return new Tween<>(to, from, duration, easing.reversed());
    }
}
