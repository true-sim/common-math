package com.truesim.common.math.numeric;

import com.truesim.common.math.Maths;

/** Real roots of polynomials up to degree three, e.g. for intersection tests and ballistic aiming. */
public final class Polynomials {

    private Polynomials() {
    }

    /** Real roots of {@code a x^2 + b x + c = 0} (or the linear equation if {@code a == 0}). */
    public static Roots solveQuadratic(double a, double b, double c) {
        if (a == 0) {
            return b == 0 ? Roots.NONE : Roots.of(-c / b);
        }
        double discriminant = b * b - 4 * a * c;
        if (discriminant < 0) {
            return Roots.NONE;
        }
        if (discriminant == 0) {
            return Roots.of(-b / (2 * a));
        }
        // Numerically stable form: avoids cancellation between -b and the square root.
        double q = -0.5 * (b + Math.copySign(Math.sqrt(discriminant), b));
        return Roots.of(q / a, c / q);
    }

    /** Real roots of {@code a x^3 + b x^2 + c x + d = 0} (or lower degree if leading coefficients are 0). */
    public static Roots solveCubic(double a, double b, double c, double d) {
        if (a == 0) {
            return solveQuadratic(b, c, d);
        }
        // Depressed cubic t^3 + p t + q = 0 with x = t - b / (3a).
        double na = b / a;
        double nb = c / a;
        double nc = d / a;
        double shift = -na / 3;
        double p = nb - na * na / 3;
        double q = 2 * na * na * na / 27 - na * nb / 3 + nc;
        double discriminant = q * q / 4 + p * p * p / 27;
        if (discriminant > 0) {
            double root = Math.sqrt(discriminant);
            return Roots.of(Maths.cbrt(-q / 2 + root) + Maths.cbrt(-q / 2 - root) + shift);
        }
        if (discriminant == 0) {
            if (p == 0) {
                return Roots.of(shift); // triple root
            }
            return Roots.of(3 * q / p + shift, -3 * q / (2 * p) + shift); // a simple and a double root
        }
        // Three real roots (trigonometric method), produced in descending order.
        double r = 2 * Math.sqrt(-p / 3);
        double phi = Maths.acos(Maths.clamp(3 * q / (p * r), -1, 1)) / 3;
        return Roots.of(
            r * Maths.cos(phi - 2 * Maths.TAU / 3) + shift,
            r * Maths.cos(phi - Maths.TAU / 3) + shift,
            r * Maths.cos(phi) + shift);
    }
}
