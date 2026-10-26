package com.truesim.common.math.curve;

/** How a {@link CatmullRom} spline spaces its knots. */
public enum Parametrization {
    /** Equal spacing: simplest, but can form loops and cusps where points are unevenly spaced. */
    UNIFORM(0),
    /** Spacing by the square root of the distance: no loops or cusps. The usual choice. */
    CENTRIPETAL(0.5),
    /** Spacing by distance: smoothest around sharp corners, but bulges more. */
    CHORDAL(1);

    private final double alpha;

    Parametrization(double alpha) {
        this.alpha = alpha;
    }

    /** Exponent applied to the distance between neighboring points. */
    public double alpha() {
        return alpha;
    }
}
