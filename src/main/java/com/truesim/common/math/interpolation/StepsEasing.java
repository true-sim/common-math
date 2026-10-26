package com.truesim.common.math.interpolation;

import com.truesim.common.math.Maths;

/** CSS {@code steps(count)}. */
record StepsEasing(int count) implements Easing {

    StepsEasing {
        if (count < 1) {
            throw new IllegalArgumentException("Step count must be at least 1: " + count);
        }
    }

    @Override
    public double apply(double t) {
        return t >= 1 ? 1 : Math.floor(Maths.clamp01(t) * count) / count;
    }
}

