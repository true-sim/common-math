package com.truesim.common.math;

/**
 * Scalar math and deterministic replacements for {@code java.lang.Math}.
 *
 * <p>Every function gives bit-identical results on every platform. Transcendental functions are accurate to about
 * one ulp (sin, cos, atan, exp, log, cbrt) or a few ulps (tan, asin, acos, atan2, pow, hyperbolic functions); they are
 * not guaranteed to equal {@link StrictMath} bit for bit.
 */
public final class Maths {

    public static final double PI = 3.141592653589793;
    public static final double TAU = 2 * PI;
    public static final double HALF_PI = 0.5 * PI;
    public static final double E = 2.718281828459045;
    public static final double DEGREES_TO_RADIANS = 0.017453292519943295;
    public static final double RADIANS_TO_DEGREES = 57.29577951308232;

    private static final double INV_LN2 = 1.4426950408889634;
    private static final double INV_LN10 = 0.4342944819032518;

    private Maths() {
    }

    // ---------------------------------------------------------------- trigonometry

    public static double sin(double radians) {
        return Fdlibm.sin(radians);
    }

    public static double cos(double radians) {
        return Fdlibm.cos(radians);
    }

    public static double tan(double radians) {
        return Fdlibm.tan(radians);
    }

    public static double asin(double x) {
        return Fdlibm.asin(x);
    }

    public static double acos(double x) {
        return Fdlibm.acos(x);
    }

    public static double atan(double x) {
        return Fdlibm.atan(x);
    }

    /** Angle of the point (x, y) in radians, in [-pi, pi]. */
    public static double atan2(double y, double x) {
        return Fdlibm.atan2(y, x);
    }

    public static double toRadians(double degrees) {
        return degrees * DEGREES_TO_RADIANS;
    }

    public static double toDegrees(double radians) {
        return radians * RADIANS_TO_DEGREES;
    }

    // ---------------------------------------------------------------- exponential and logarithm

    public static double exp(double x) {
        return Fdlibm.exp(x);
    }

    /** {@code exp(x) - 1}, accurate for small x. */
    public static double expm1(double x) {
        double u = Fdlibm.exp(x);
        if (u == 1.0) {
            return x;
        }
        double um1 = u - 1.0;
        if (um1 == -1.0) {
            return -1.0;
        }
        if (u == Double.POSITIVE_INFINITY) {
            return u;
        }
        return um1 * (x / Fdlibm.log(u)); // Kahan: cancels the rounding error of exp
    }

    /** Natural logarithm. */
    public static double log(double x) {
        return Fdlibm.log(x);
    }

    /** {@code log(1 + x)}, accurate for small x. */
    public static double log1p(double x) {
        double u = 1.0 + x;
        if (u == 1.0 || u == Double.POSITIVE_INFINITY) {
            return u == 1.0 ? x : u;
        }
        return Fdlibm.log(u) * (x / (u - 1.0)); // Kahan: cancels the rounding error of 1 + x
    }

    /** Base-2 logarithm; exact for powers of two. */
    public static double log2(double x) {
        if (x > 0 && x < Double.POSITIVE_INFINITY && (Double.doubleToRawLongBits(x) & 0x000fffffffffffffL) == 0) {
            return Math.getExponent(x);
        }
        return Fdlibm.log(x) * INV_LN2;
    }

    public static double log10(double x) {
        return Fdlibm.log(x) * INV_LN10;
    }

    /** {@code x} raised to {@code y}, with the special cases of {@link Math#pow}. */
    public static double pow(double x, double y) {
        if (y == 0.0) {
            return 1.0;
        }
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return Double.NaN;
        }
        if (y == 1.0) {
            return x;
        }
        double ax = Math.abs(x);
        if (Double.isInfinite(y)) {
            if (ax == 1.0) {
                return Double.NaN;
            }
            return (ax > 1.0) == (y > 0) ? Double.POSITIVE_INFINITY : 0.0;
        }
        boolean yIsInteger = Math.rint(y) == y;
        boolean yIsOdd = yIsInteger && Math.abs(y) < 0x1p53 && ((long) y & 1L) != 0;
        if (x == 0.0 || Double.isInfinite(x)) {
            double magnitude = (x == 0.0) == (y > 0) ? 0.0 : Double.POSITIVE_INFINITY;
            return Double.doubleToRawLongBits(x) < 0 && yIsOdd ? -magnitude : magnitude;
        }
        if (x < 0) {
            if (!yIsInteger) {
                return Double.NaN;
            }
            double r = powPositive(ax, y, true);
            return yIsOdd ? -r : r;
        }
        return powPositive(x, y, yIsInteger);
    }

    private static double powPositive(double x, double y, boolean yIsInteger) {
        if (x == 1.0) {
            return 1.0;
        }
        if (y == 0.5) {
            return Math.sqrt(x);
        }
        if (yIsInteger && Math.abs(y) <= 64) {
            long n = (long) Math.abs(y);
            double result = 1.0;
            double base = x;
            while (n != 0) {
                if ((n & 1) != 0) {
                    result *= base;
                }
                base *= base;
                n >>= 1;
            }
            return y < 0 ? 1.0 / result : result;
        }
        double l = Fdlibm.log(x);
        double p = y * l;
        if (p > 709.8) {
            return Double.POSITIVE_INFINITY;
        }
        if (p < -745.2) {
            return 0.0;
        }
        double error = Math.fma(y, l, -p); // exact rounding error of the product
        double r = Fdlibm.exp(p);
        return r + r * error;
    }

    public static double sqrt(double x) {
        return Math.sqrt(x);
    }

    public static double cbrt(double x) {
        return Fdlibm.cbrt(x);
    }

    /** {@code sqrt(x^2 + y^2)} without intermediate overflow or underflow. */
    public static double hypot(double x, double y) {
        double a = Math.abs(x);
        double b = Math.abs(y);
        if (a == Double.POSITIVE_INFINITY || b == Double.POSITIVE_INFINITY) {
            return Double.POSITIVE_INFINITY;
        }
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return Double.NaN;
        }
        if (a < b) {
            double t = a;
            a = b;
            b = t;
        }
        if (a == 0.0) {
            return 0.0;
        }
        int exponent = Math.getExponent(a);
        double sa = Math.scalb(a, -exponent);
        double sb = Math.scalb(b, -exponent);
        return Math.scalb(Math.sqrt(Math.fma(sa, sa, sb * sb)), exponent);
    }

    // ---------------------------------------------------------------- hyperbolic

    public static double sinh(double x) {
        double a = Math.abs(x);
        double r;
        if (a < 0x1p-28) {
            return x;
        } else if (a < 22) {
            double t = expm1(a);
            r = 0.5 * (t + t / (t + 1.0));
        } else if (a < 709) {
            r = 0.5 * Fdlibm.exp(a);
        } else {
            double h = Fdlibm.exp(0.5 * a);
            r = (0.5 * h) * h;
        }
        return Math.copySign(r, x);
    }

    public static double cosh(double x) {
        double a = Math.abs(x);
        if (a < 22) {
            double t = Fdlibm.exp(a);
            return 0.5 * t + 0.5 / t;
        }
        if (a < 709) {
            return 0.5 * Fdlibm.exp(a);
        }
        double h = Fdlibm.exp(0.5 * a);
        return (0.5 * h) * h;
    }

    public static double tanh(double x) {
        double a = Math.abs(x);
        if (Double.isNaN(a)) {
            return x;
        }
        if (a < 0x1p-28) {
            return x;
        }
        if (a > 22) {
            return Math.copySign(1.0, x);
        }
        double t = expm1(2 * a);
        return Math.copySign(t / (t + 2.0), x);
    }

    // ---------------------------------------------------------------- scalar helpers

    public static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    /** Linear interpolation; exact at {@code t = 0} and {@code t = 1}. */
    public static double lerp(double from, double to, double t) {
        return Math.fma(t, to, Math.fma(-t, from, from));
    }

    /** The {@code t} for which {@code lerp(from, to, t) == value}. */
    public static double inverseLerp(double from, double to, double value) {
        return (value - from) / (to - from);
    }

    /** Maps {@code value} from [inMin, inMax] to [outMin, outMax]. */
    public static double remap(double value, double inMin, double inMax, double outMin, double outMax) {
        return lerp(outMin, outMax, inverseLerp(inMin, inMax, value));
    }

    /** Fractional part: {@code x - floor(x)}, in [0, 1). */
    public static double fract(double x) {
        return x - Math.floor(x);
    }

    /** 0 below {@code edge}, 1 at or above it (as in GLSL). */
    public static double step(double edge, double x) {
        return x < edge ? 0 : 1;
    }

    /** Bounces {@code t} back and forth between 0 and {@code length}. */
    public static double pingPong(double t, double length) {
        double r = wrap(t, 0, 2 * length);
        return length - Math.abs(r - length);
    }

    /** Hermite smoothing between 0 (at edge0) and 1 (at edge1). */
    public static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    /** Ken Perlin's smootherstep: zero first and second derivatives at the edges. */
    public static double smootherstep(double edge0, double edge1, double x) {
        double t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * t * (t * (6.0 * t - 15.0) + 10.0);
    }

    /** Wraps {@code value} into [min, max). */
    public static double wrap(double value, double min, double max) {
        double range = max - min;
        double r = (value - min) - range * Math.floor((value - min) / range);
        return r >= range ? min : min + r;
    }

    /** Wraps an angle into [-pi, pi). */
    public static double wrapAngle(double radians) {
        return wrap(radians, -PI, PI);
    }

    /** Shortest signed angle from {@code from} to {@code to}, in [-pi, pi). */
    public static double deltaAngle(double from, double to) {
        return wrapAngle(to - from);
    }

    /** Interpolates angles along the shortest arc. */
    public static double lerpAngle(double from, double to, double t) {
        return from + deltaAngle(from, to) * t;
    }

    /** Moves {@code current} towards {@code target} by at most {@code maxDelta}. */
    public static double moveTowards(double current, double target, double maxDelta) {
        double delta = target - current;
        return Math.abs(delta) <= maxDelta ? target : current + Math.copySign(maxDelta, delta);
    }

    /**
     * Frame-rate independent exponential smoothing: {@code current} approaches {@code target} with the given
     * {@code rate} (per second), regardless of how {@code dt} is split.
     */
    public static double damp(double current, double target, double rate, double dt) {
        return lerp(current, target, -expm1(-rate * dt));
    }

    public static boolean approxEquals(double a, double b, double epsilon) {
        return Math.abs(a - b) <= epsilon;
    }
}
