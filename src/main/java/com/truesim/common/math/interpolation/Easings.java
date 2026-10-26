package com.truesim.common.math.interpolation;

import com.truesim.common.math.Maths;

/**
 * The standard easing functions (the Penner set used by CSS and most animation tools) as static methods.
 * Each maps progress {@code t} in [0, 1] to eased progress, with {@code f(0) = 0} and {@code f(1) = 1}.
 * {@link Easing} has the same functions as constants.
 */
public final class Easings {

    private static final double BACK = 1.70158;
    private static final double BACK_IN_OUT = BACK * 1.525;
    private static final double ELASTIC = Maths.TAU / 3;
    private static final double ELASTIC_IN_OUT = Maths.TAU / 4.5;
    private static final double BOUNCE_N = 7.5625;
    private static final double BOUNCE_D = 2.75;

    private Easings() {
    }

    public static double linear(double t) {
        return t;
    }

    public static double quadIn(double t) {
        return t * t;
    }

    public static double quadOut(double t) {
        return 1 - power(1 - t, 2);
    }

    public static double quadInOut(double t) {
        return t < 0.5 ? 2 * t * t : 1 - power(-2 * t + 2, 2) / 2;
    }

    public static double cubicIn(double t) {
        return t * t * t;
    }

    public static double cubicOut(double t) {
        return 1 - power(1 - t, 3);
    }

    public static double cubicInOut(double t) {
        return t < 0.5 ? 4 * t * t * t : 1 - power(-2 * t + 2, 3) / 2;
    }

    public static double quartIn(double t) {
        return power(t, 4);
    }

    public static double quartOut(double t) {
        return 1 - power(1 - t, 4);
    }

    public static double quartInOut(double t) {
        return t < 0.5 ? 8 * power(t, 4) : 1 - power(-2 * t + 2, 4) / 2;
    }

    public static double quintIn(double t) {
        return power(t, 5);
    }

    public static double quintOut(double t) {
        return 1 - power(1 - t, 5);
    }

    public static double quintInOut(double t) {
        return t < 0.5 ? 16 * power(t, 5) : 1 - power(-2 * t + 2, 5) / 2;
    }

    public static double sineIn(double t) {
        return 1 - Maths.cos(t * Maths.HALF_PI);
    }

    public static double sineOut(double t) {
        return Maths.sin(t * Maths.HALF_PI);
    }

    public static double sineInOut(double t) {
        return -(Maths.cos(Maths.PI * t) - 1) / 2;
    }

    public static double expoIn(double t) {
        return t == 0 ? 0 : Maths.pow(2, 10 * t - 10);
    }

    public static double expoOut(double t) {
        return t == 1 ? 1 : 1 - Maths.pow(2, -10 * t);
    }

    public static double expoInOut(double t) {
        if (t == 0 || t == 1) {
            return t;
        }
        return t < 0.5 ? Maths.pow(2, 20 * t - 10) / 2 : (2 - Maths.pow(2, -20 * t + 10)) / 2;
    }

    public static double circIn(double t) {
        return 1 - Math.sqrt(1 - t * t);
    }

    public static double circOut(double t) {
        return Math.sqrt(1 - power(t - 1, 2));
    }

    public static double circInOut(double t) {
        return t < 0.5
            ? (1 - Math.sqrt(1 - power(2 * t, 2))) / 2
            : (Math.sqrt(1 - power(-2 * t + 2, 2)) + 1) / 2;
    }

    /** Pulls back slightly before moving forward. */
    public static double backIn(double t) {
        return (BACK + 1) * t * t * t - BACK * t * t;
    }

    /** Overshoots slightly before settling. */
    public static double backOut(double t) {
        double u = t - 1;
        return 1 + (BACK + 1) * u * u * u + BACK * u * u;
    }

    public static double backInOut(double t) {
        return t < 0.5
            ? (power(2 * t, 2) * ((BACK_IN_OUT + 1) * 2 * t - BACK_IN_OUT)) / 2
            : (power(2 * t - 2, 2) * ((BACK_IN_OUT + 1) * (t * 2 - 2) + BACK_IN_OUT) + 2) / 2;
    }

    public static double elasticIn(double t) {
        if (t == 0 || t == 1) {
            return t;
        }
        return -Maths.pow(2, 10 * t - 10) * Maths.sin((t * 10 - 10.75) * ELASTIC);
    }

    /** Springs past the target and oscillates into place. */
    public static double elasticOut(double t) {
        if (t == 0 || t == 1) {
            return t;
        }
        return Maths.pow(2, -10 * t) * Maths.sin((t * 10 - 0.75) * ELASTIC) + 1;
    }

    public static double elasticInOut(double t) {
        if (t == 0 || t == 1) {
            return t;
        }
        double s = Maths.sin((20 * t - 11.125) * ELASTIC_IN_OUT);
        return t < 0.5 ? -(Maths.pow(2, 20 * t - 10) * s) / 2 : (Maths.pow(2, -20 * t + 10) * s) / 2 + 1;
    }

    public static double bounceIn(double t) {
        return 1 - bounceOut(1 - t);
    }

    /** Bounces like a ball dropped onto the target. */
    public static double bounceOut(double t) {
        if (t < 1 / BOUNCE_D) {
            return BOUNCE_N * t * t;
        }
        if (t < 2 / BOUNCE_D) {
            double u = t - 1.5 / BOUNCE_D;
            return BOUNCE_N * u * u + 0.75;
        }
        if (t < 2.5 / BOUNCE_D) {
            double u = t - 2.25 / BOUNCE_D;
            return BOUNCE_N * u * u + 0.9375;
        }
        double u = t - 2.625 / BOUNCE_D;
        return BOUNCE_N * u * u + 0.984375;
    }

    public static double bounceInOut(double t) {
        return t < 0.5 ? (1 - bounceOut(1 - 2 * t)) / 2 : (1 + bounceOut(2 * t - 1)) / 2;
    }

    private static double power(double x, int n) {
        double r = x;
        for (int i = 1; i < n; i++) {
            r *= x;
        }
        return r;
    }
}
