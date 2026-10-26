package com.truesim.common.math.interpolation;

/** {@code f} on the first half, its reverse on the second. */
record MirroredEasing(Easing easing) implements Easing {

    @Override
    public double apply(double t) {
        return t < 0.5 ? easing.apply(2 * t) / 2 : 1 - easing.apply(2 - 2 * t) / 2;
    }
}

