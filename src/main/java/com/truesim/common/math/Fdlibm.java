/*
 * Algorithms and coefficients derived from fdlibm 5.3:
 *
 * Copyright (C) 1993 by Sun Microsystems, Inc. All rights reserved.
 * Developed at SunSoft, a Sun Microsystems, Inc. business.
 * Permission to use, copy, modify, and distribute this software is freely granted,
 * provided that this notice is preserved.
 */
package com.truesim.common.math;

/**
 * Deterministic transcendental functions in pure Java. Only IEEE 754 arithmetic and bit manipulation are used,
 * which Java specifies exactly on every platform, so results are bit-identical everywhere.
 */
final class Fdlibm {

    private Fdlibm() {
    }

    // ---------------------------------------------------------------- bit helpers

    private static int high(double x) {
        return (int) (Double.doubleToRawLongBits(x) >>> 32);
    }

    private static double withHigh(double x, int high) {
        return Double.longBitsToDouble(((long) high << 32) | (Double.doubleToRawLongBits(x) & 0xffffffffL));
    }

    private static double fromWords(int high, int low) {
        return Double.longBitsToDouble(((long) high << 32) | (low & 0xffffffffL));
    }

    // ---------------------------------------------------------------- sin, cos, tan

    private static final double S1 = -1.66666666666666324348e-01;
    private static final double S2 = 8.33333333332248946124e-03;
    private static final double S3 = -1.98412698298579493134e-04;
    private static final double S4 = 2.75573137070700676789e-06;
    private static final double S5 = -2.50507602534068634195e-08;
    private static final double S6 = 1.58969099521155010221e-10;

    private static final double C1 = 4.16666666666666019037e-02;
    private static final double C2 = -1.38888888888741095749e-03;
    private static final double C3 = 2.48015872894767294178e-05;
    private static final double C4 = -2.75573143513906633035e-07;
    private static final double C5 = 2.08757232129817482790e-09;
    private static final double C6 = -1.13596475577881948265e-11;

    private static final double INV_PIO2 = 6.36619772367581382433e-01;
    private static final double PIO2_1 = 1.57079632673412561417e+00;
    private static final double PIO2_1T = 6.07710050650619224932e-11;
    private static final double PIO2_2 = 6.07710050630396597660e-11;
    private static final double PIO2_2T = 2.02226624879595063154e-21;
    private static final double PIO2_3 = 2.02226624871116645580e-21;
    private static final double PIO2_3T = 8.47842766036889956997e-32;
    private static final double TWO_PI_HI = 6.28318530717958623200e+00;
    private static final double TWO_PI_LO = 2.44929359829470635445e-16;

    private static final int SIN = 0;
    private static final int COS = 1;
    private static final int TAN = 2;

    static double sin(double x) {
        return trig(x, SIN);
    }

    static double cos(double x) {
        return trig(x, COS);
    }

    static double tan(double x) {
        return trig(x, TAN);
    }

    /** Shared range reduction, so the reduced argument (two doubles) never needs a holder object. */
    private static double trig(double x, int function) {
        int ix = high(x) & 0x7fffffff;
        if (ix <= 0x3fe921fb) { // |x| <= pi/4
            return switch (function) {
                case SIN -> kernelSin(x, 0, false);
                case COS -> kernelCos(x, 0);
                default -> kernelSin(x, 0, false) / kernelCos(x, 0);
            };
        }
        if (ix >= 0x7ff00000) {
            return Double.NaN;
        }
        if (ix > 0x413921fb) { // |x| > 2^19 * pi/2: reduce by 2*pi first (deterministic, less precise)
            double remainder = Math.IEEEremainder(x, TWO_PI_HI); // exact: x - n * TWO_PI_HI
            double n = Math.rint((x - remainder) / TWO_PI_HI);
            x = Math.abs(n) < 0x1p52 ? remainder - n * TWO_PI_LO : remainder;
            return trig(x, function);
        }

        // Cody-Waite reduction by pi/2 with up to three-part constants (fdlibm __ieee754_rem_pio2, medium case).
        int hx = high(x);
        double t = Math.abs(x);
        int n = (int) (t * INV_PIO2 + 0.5);
        double fn = n;
        double r = t - fn * PIO2_1;
        double w = fn * PIO2_1T;
        int j = ix >> 20;
        double y0 = r - w;
        int i = j - ((high(y0) >> 20) & 0x7ff);
        if (i > 16) {
            t = r;
            w = fn * PIO2_2;
            r = t - w;
            w = fn * PIO2_2T - ((t - r) - w);
            y0 = r - w;
            i = j - ((high(y0) >> 20) & 0x7ff);
            if (i > 49) {
                t = r;
                w = fn * PIO2_3;
                r = t - w;
                w = fn * PIO2_3T - ((t - r) - w);
                y0 = r - w;
            }
        }
        double y1 = (r - y0) - w;
        if (hx < 0) {
            y0 = -y0;
            y1 = -y1;
            n = -n;
        }

        return switch (function) {
            case SIN -> switch (n & 3) {
                case 0 -> kernelSin(y0, y1, true);
                case 1 -> kernelCos(y0, y1);
                case 2 -> -kernelSin(y0, y1, true);
                default -> -kernelCos(y0, y1);
            };
            case COS -> switch (n & 3) {
                case 0 -> kernelCos(y0, y1);
                case 1 -> -kernelSin(y0, y1, true);
                case 2 -> -kernelCos(y0, y1);
                default -> kernelSin(y0, y1, true);
            };
            default -> {
                double s = kernelSin(y0, y1, true);
                double c = kernelCos(y0, y1);
                yield (n & 1) == 0 ? s / c : -c / s;
            }
        };
    }

    /** sin(x + y) for |x| <= pi/4, where y is the tail of x. */
    private static double kernelSin(double x, double y, boolean hasTail) {
        int ix = high(x) & 0x7fffffff;
        if (ix < 0x3e400000) { // |x| < 2^-27
            return x;
        }
        double z = x * x;
        double v = z * x;
        double r = S2 + z * (S3 + z * (S4 + z * (S5 + z * S6)));
        if (!hasTail) {
            return x + v * (S1 + z * r);
        }
        return x - ((z * (0.5 * y - v * r) - y) - v * S1);
    }

    /** cos(x + y) for |x| <= pi/4, where y is the tail of x. */
    private static double kernelCos(double x, double y) {
        int ix = high(x) & 0x7fffffff;
        if (ix < 0x3e400000) { // |x| < 2^-27
            return 1.0;
        }
        double z = x * x;
        double r = z * (C1 + z * (C2 + z * (C3 + z * (C4 + z * (C5 + z * C6)))));
        if (ix < 0x3fd33333) { // |x| < 0.3
            return 1.0 - (0.5 * z - (z * r - x * y));
        }
        double qx = ix > 0x3fe90000 ? 0.28125 : fromWords(ix - 0x00200000, 0);
        double hz = 0.5 * z - qx;
        double a = 1.0 - qx;
        return a - (hz - (z * r - x * y));
    }

    // ---------------------------------------------------------------- atan, atan2, asin, acos

    private static final double[] ATAN_HI = {
        4.63647609000806093515e-01, 7.85398163397448278999e-01, 9.82793723247329054082e-01, 1.57079632679489655800e+00,
    };
    private static final double[] ATAN_LO = {
        2.26987774529616870924e-17, 3.06161699786838301793e-17, 1.39033110312309984516e-17, 6.12323399573676603587e-17,
    };
    private static final double AT0 = 3.33333333333329318027e-01;
    private static final double AT1 = -1.99999999998764832476e-01;
    private static final double AT2 = 1.42857142725034663711e-01;
    private static final double AT3 = -1.11111104054623557880e-01;
    private static final double AT4 = 9.09088713343650656196e-02;
    private static final double AT5 = -7.69187620504482999495e-02;
    private static final double AT6 = 6.66107313738753120669e-02;
    private static final double AT7 = -5.83357013379057348645e-02;
    private static final double AT8 = 4.97687799461593236017e-02;
    private static final double AT9 = -3.65315727442169155270e-02;
    private static final double AT10 = 1.62858201153657823623e-02;

    private static final double PI = 3.1415926535897931160e+00;
    private static final double PI_LO = 1.2246467991473531772e-16;
    private static final double PI_O_2 = 1.5707963267948965580e+00;
    private static final double PI_O_4 = 7.8539816339744827900e-01;

    static double atan(double x) {
        int hx = high(x);
        int ix = hx & 0x7fffffff;
        int id;
        if (ix >= 0x44100000) { // |x| >= 2^66
            if (Double.isNaN(x)) {
                return x + x; // NaN
            }
            return hx > 0 ? ATAN_HI[3] + ATAN_LO[3] : -ATAN_HI[3] - ATAN_LO[3];
        }
        if (ix < 0x3fdc0000) { // |x| < 0.4375
            if (ix < 0x3e200000) { // |x| < 2^-29
                return x;
            }
            id = -1;
        } else {
            x = Math.abs(x);
            if (ix < 0x3ff30000) { // |x| < 1.1875
                if (ix < 0x3fe60000) { // 7/16 <= |x| < 11/16
                    id = 0;
                    x = (2.0 * x - 1.0) / (2.0 + x);
                } else { // 11/16 <= |x| < 19/16
                    id = 1;
                    x = (x - 1.0) / (x + 1.0);
                }
            } else if (ix < 0x40038000) { // |x| < 2.4375
                id = 2;
                x = (x - 1.5) / (1.0 + 1.5 * x);
            } else { // 2.4375 <= |x| < 2^66
                id = 3;
                x = -1.0 / x;
            }
        }
        double z = x * x;
        double w = z * z;
        double s1 = z * (AT0 + w * (AT2 + w * (AT4 + w * (AT6 + w * (AT8 + w * AT10)))));
        double s2 = w * (AT1 + w * (AT3 + w * (AT5 + w * (AT7 + w * AT9))));
        if (id < 0) {
            return x - x * (s1 + s2);
        }
        z = ATAN_HI[id] - ((x * (s1 + s2) - ATAN_LO[id]) - x);
        return hx < 0 ? -z : z;
    }

    static double atan2(double y, double x) {
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return x + y;
        }
        if (x == 1.0) {
            return atan(y);
        }
        boolean xNegative = (high(x) & 0x80000000) != 0;
        boolean yNegative = (high(y) & 0x80000000) != 0;
        if (y == 0.0) {
            return xNegative ? (yNegative ? -PI : PI) : y;
        }
        if (x == 0.0) {
            return yNegative ? -PI_O_2 : PI_O_2;
        }
        if (Double.isInfinite(x)) {
            if (Double.isInfinite(y)) {
                double a = xNegative ? 3.0 * PI_O_4 : PI_O_4;
                return yNegative ? -a : a;
            }
            double a = xNegative ? PI : 0.0;
            return yNegative ? -a : a;
        }
        if (Double.isInfinite(y)) {
            return yNegative ? -PI_O_2 : PI_O_2;
        }

        int k = ((high(y) & 0x7fffffff) - (high(x) & 0x7fffffff)) >> 20;
        double z;
        if (k > 60) { // |y/x| > 2^60
            z = PI_O_2 + 0.5 * PI_LO;
        } else if (xNegative && k < -60) { // |y|/x < -2^-60
            z = 0.0;
        } else {
            z = atan(Math.abs(y / x));
        }
        if (!xNegative) {
            return yNegative ? -z : z;
        }
        return yNegative ? (z - PI_LO) - PI : PI - (z - PI_LO);
    }

    static double asin(double x) {
        if (!(Math.abs(x) <= 1.0)) {
            return Double.NaN;
        }
        return atan2(x, Math.sqrt((1.0 - x) * (1.0 + x)));
    }

    static double acos(double x) {
        if (!(Math.abs(x) <= 1.0)) {
            return Double.NaN;
        }
        return atan2(Math.sqrt((1.0 - x) * (1.0 + x)), x);
    }

    // ---------------------------------------------------------------- exp, log

    private static final double LN2_HI = 6.93147180369123816490e-01;
    private static final double LN2_LO = 1.90821492927058770002e-10;
    private static final double INV_LN2 = 1.44269504088896338700e+00;
    private static final double P1 = 1.66666666666666019037e-01;
    private static final double P2 = -2.77777777770155933842e-03;
    private static final double P3 = 6.61375632143793436117e-05;
    private static final double P4 = -1.65339022054652515390e-06;
    private static final double P5 = 4.13813679705723846039e-08;
    private static final double EXP_OVERFLOW = 7.09782712893383973096e+02;
    private static final double EXP_UNDERFLOW = -7.45133219101941108420e+02;
    private static final double TWO_M1000 = 9.33263618503218878990e-302;

    static double exp(double x) {
        int hx = high(x);
        boolean negative = hx < 0;
        hx &= 0x7fffffff;

        if (hx >= 0x40862e42) { // |x| >= 709.78
            if (hx >= 0x7ff00000) {
                if (Double.isNaN(x)) {
                    return x + x; // NaN
                }
                return negative ? 0.0 : x; // exp(+-inf) = (inf, 0)
            }
            if (x > EXP_OVERFLOW) {
                return Double.POSITIVE_INFINITY;
            }
            if (x < EXP_UNDERFLOW) {
                return 0.0;
            }
        }

        double hi = 0;
        double lo = 0;
        int k = 0;
        if (hx > 0x3fd62e42) { // |x| > 0.5 ln2
            if (hx < 0x3ff0a2b2) { // |x| < 1.5 ln2
                hi = negative ? x + LN2_HI : x - LN2_HI;
                lo = negative ? -LN2_LO : LN2_LO;
                k = negative ? -1 : 1;
            } else {
                k = (int) (INV_LN2 * x + (negative ? -0.5 : 0.5));
                double t = k;
                hi = x - t * LN2_HI;
                lo = t * LN2_LO;
            }
            x = hi - lo;
        } else if (hx < 0x3e300000) { // |x| < 2^-28
            return 1.0 + x;
        }

        double t = x * x;
        double c = x - t * (P1 + t * (P2 + t * (P3 + t * (P4 + t * P5))));
        if (k == 0) {
            return 1.0 - ((x * c) / (c - 2.0) - x);
        }
        double y = 1.0 - ((lo - (x * c) / (2.0 - c)) - hi);
        if (k >= -1021) {
            return Double.longBitsToDouble(Double.doubleToRawLongBits(y) + ((long) k << 52));
        }
        return Double.longBitsToDouble(Double.doubleToRawLongBits(y) + ((long) (k + 1000) << 52)) * TWO_M1000;
    }

    private static final double TWO54 = 1.80143985094819840000e+16;
    private static final double LG1 = 6.666666666666735130e-01;
    private static final double LG2 = 3.999999999940941908e-01;
    private static final double LG3 = 2.857142874366239149e-01;
    private static final double LG4 = 2.222219843214978396e-01;
    private static final double LG5 = 1.818357216161805012e-01;
    private static final double LG6 = 1.531383769920937332e-01;
    private static final double LG7 = 1.479819860511658591e-01;

    static double log(double x) {
        int hx = high(x);
        int k = 0;
        if (hx < 0x00100000) { // x < 2^-1022
            if (x == 0.0) {
                return Double.NEGATIVE_INFINITY;
            }
            if (hx < 0) {
                return Double.NaN;
            }
            k -= 54;
            x *= TWO54; // scale up subnormal
            hx = high(x);
        }
        if (hx >= 0x7ff00000) {
            return x + x;
        }
        k += (hx >> 20) - 1023;
        hx &= 0x000fffff;
        int i = (hx + 0x95f64) & 0x100000;
        x = withHigh(x, hx | (i ^ 0x3ff00000)); // normalize x or x/2
        k += i >> 20;
        double f = x - 1.0;
        double dk = k;
        if ((0x000fffff & (2 + hx)) < 3) { // |f| < 2^-20
            if (f == 0.0) {
                return k == 0 ? 0.0 : dk * LN2_HI + dk * LN2_LO;
            }
            double r = f * f * (0.5 - 0.33333333333333333 * f);
            return k == 0 ? f - r : dk * LN2_HI - ((r - dk * LN2_LO) - f);
        }
        double s = f / (2.0 + f);
        double z = s * s;
        i = hx - 0x6147a;
        double w = z * z;
        int j = 0x6b851 - hx;
        double t1 = w * (LG2 + w * (LG4 + w * LG6));
        double t2 = z * (LG1 + w * (LG3 + w * (LG5 + w * LG7)));
        i |= j;
        double r = t2 + t1;
        if (i > 0) {
            double hfsq = 0.5 * f * f;
            return k == 0 ? f - (hfsq - s * (hfsq + r)) : dk * LN2_HI - ((hfsq - (s * (hfsq + r) + dk * LN2_LO)) - f);
        }
        return k == 0 ? f - s * (f - r) : dk * LN2_HI - ((s * (f - r) - dk * LN2_LO) - f);
    }

    // ---------------------------------------------------------------- cbrt

    private static final int CBRT_B1 = 715094163;
    private static final int CBRT_B2 = 696219795;
    private static final double CBRT_C = 5.42857142857142815906e-01;
    private static final double CBRT_D = -7.05306122448979611050e-01;
    private static final double CBRT_E = 1.41428571428571436819e+00;
    private static final double CBRT_F = 1.60714285714285720630e+00;
    private static final double CBRT_G = 3.57142857142857150787e-01;

    static double cbrt(double x) {
        int hx = high(x);
        int sign = hx & 0x80000000;
        hx ^= sign;
        if (hx >= 0x7ff00000) {
            return x + x; // NaN, inf
        }
        if (x == 0.0) {
            return x; // +-0
        }
        x = withHigh(x, hx); // |x|

        double t;
        if (hx < 0x00100000) { // subnormal
            t = fromWords(0x43500000, 0) * x; // * 2^54
            t = withHigh(t, high(t) / 3 + CBRT_B2);
        } else {
            t = fromWords(hx / 3 + CBRT_B1, 0);
        }

        // New cbrt to 23 bits
        double r = t * t / x;
        double s = CBRT_C + r * t;
        t *= CBRT_G + CBRT_F / (s + CBRT_E + CBRT_D / s);

        // Chop to 20 bits and make it larger than cbrt(x)
        t = fromWords(high(t) + 1, 0);

        // One step Newton iteration to 53 bits with error less than 0.667 ulps
        s = t * t;
        r = x / s;
        double w = t + t;
        r = (r - t) / (w + r);
        t = t + t * r;
        return sign != 0 ? -t : t;
    }
}
