package com.truesim.common.math.interpolation;

import com.truesim.common.math.Maths;

/** CSS {@code cubic-bezier(x1, y1, x2, y2)} from (0, 0) to (1, 1). */
record CubicBezierEasing(double x1, double y1, double x2, double y2) implements Easing {

    private static final int ITERATIONS = 52;

    CubicBezierEasing {
        if (!(x1 >= 0 && x1 <= 1 && x2 >= 0 && x2 <= 1)) {
            throw new IllegalArgumentException("x1 and x2 must be in [0, 1]: " + x1 + ", " + x2);
        }
    }

    @Override
    public double apply(double t) {
        if (t <= 0 || t >= 1) {
            return t <= 0 ? 0 : 1; // exact end points
        }
        double x = t;
        // x(s) increases monotonically for x1, x2 in [0, 1]: bisect for the curve parameter s with x(s) = x.
        double low = 0;
        double high = 1;
        for (int i = 0; i < ITERATIONS; i++) {
            double mid = 0.5 * (low + high);
            if (bezier(mid, x1, x2) < x) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return bezier(0.5 * (low + high), y1, y2);
    }

    private static double bezier(double s, double p1, double p2) {
        double u = 1 - s;
        return 3 * u * u * s * p1 + 3 * u * s * s * p2 + s * s * s;
    }
}
