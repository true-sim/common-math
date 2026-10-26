package com.truesim.common.math.interpolation;

/** {@code 1 - f(1 - t)}. */
record ReversedEasing(Easing easing) implements Easing {

    @Override
    public double apply(double t) {
        return 1 - easing.apply(1 - t);
    }
}

