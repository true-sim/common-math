package com.truesim.common.math.interpolation;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Interpolatable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An immutable animation track: values at points in time, interpolated in between. Each key can have its own
 * {@link Easing} for the segment that ends at it.
 *
 * <pre>{@code
 * Keyframes<Color> sky = Keyframes.<Color>builder()
 *     .key(0, night)
 *     .key(6, dawn, Easing.SINE_IN_OUT)
 *     .key(12, noon)
 *     .build();
 * Color now = sky.valueAt(hour);
 * }</pre>
 *
 * @param <T> any interpolatable value
 */
public final class Keyframes<T extends Interpolatable<T>> {

    private final double[] times;
    private final Object[] values;
    private final Easing[] easings;

    private Keyframes(double[] times, Object[] values, Easing[] easings) {
        this.times = times;
        this.values = values;
        this.easings = easings;
    }

    public static <T extends Interpolatable<T>> Builder<T> builder() {
        return new Builder<>();
    }

    /** The interpolated value at {@code time}; the first or last value outside the track. */
    public T valueAt(double time) {
        int last = times.length - 1;
        if (time <= times[0]) {
            return value(0);
        }
        if (time >= times[last]) {
            return value(last);
        }
        int index = Arrays.binarySearch(times, time);
        if (index >= 0) {
            return value(index);
        }
        int next = -index - 1;
        int previous = next - 1;
        double t = (time - times[previous]) / (times[next] - times[previous]);
        return value(previous).interpolate(value(next), easings[next].apply(t));
    }

    public int size() {
        return times.length;
    }

    public double startTime() {
        return times[0];
    }

    public double endTime() {
        return times[times.length - 1];
    }

    public double time(int index) {
        return times[index];
    }

    @SuppressWarnings("unchecked")
    public T value(int index) {
        return (T) values[index];
    }

    /** Collects keys in increasing time order. */
    @AllowAllocation("setup code")
    public static final class Builder<T extends Interpolatable<T>> {

        private final List<Key<T>> keys = new ArrayList<>();

        private Builder() {
        }

        /** Adds a key reached with linear interpolation. */
        public Builder<T> key(double time, T value) {
            return key(time, value, Easing.LINEAR);
        }

        /** Adds a key; {@code easing} shapes the segment from the previous key to this one. */
        public Builder<T> key(double time, T value, Easing easing) {
            if (!keys.isEmpty() && !(time > keys.getLast().time())) {
                throw new IllegalArgumentException("Key times must increase: " + time + " after " + keys.getLast().time());
            }
            keys.add(new Key<>(time, value, easing));
            return this;
        }

        public Keyframes<T> build() {
            if (keys.isEmpty()) {
                throw new IllegalStateException("Keyframes need at least one key");
            }
            double[] times = new double[keys.size()];
            Object[] values = new Object[keys.size()];
            Easing[] easings = new Easing[keys.size()];
            for (int i = 0; i < keys.size(); i++) {
                times[i] = keys.get(i).time();
                values[i] = keys.get(i).value();
                easings[i] = keys.get(i).easing();
            }
            return new Keyframes<>(times, values, easings);
        }
    }

    private record Key<T>(double time, T value, Easing easing) {
    }
}
