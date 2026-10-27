package com.truesim.common.math.numeric;

import java.util.function.DoubleUnaryOperator;

/** Numerical root finding for functions without a closed-form solution. */
public final class RootFinding {

    private RootFinding() {
    }

    /**
     * A root of {@code f} in [{@code low}, {@code high}] by bisection: slow but always converges.
     *
     * @throws IllegalArgumentException if {@code f(low)} and {@code f(high)} have the same sign
     */
    public static double bisect(DoubleUnaryOperator f, double low, double high, double tolerance) {
        double fLow = f.applyAsDouble(low);
        double fHigh = f.applyAsDouble(high);
        if (fLow * fHigh > 0) {
            throw new IllegalArgumentException("f(low) and f(high) must have opposite signs");
        }
        while (high - low > tolerance) {
            double mid = 0.5 * (low + high);
            double fMid = f.applyAsDouble(mid);
            if (fLow * fMid <= 0) {
                high = mid;
            } else {
                low = mid;
                fLow = fMid;
            }
        }
        return 0.5 * (low + high);
    }

    /**
     * A root of {@code f} near {@code guess} by Newton's method, given its {@code derivative}: converges very fast
     * from a good guess.
     *
     * @throws ArithmeticException if it does not converge within {@code maxIterations} or the derivative vanishes
     */
    public static double newton(
        DoubleUnaryOperator f, DoubleUnaryOperator derivative, double guess, double tolerance, int maxIterations) {
        double x = guess;
        for (int i = 0; i < maxIterations; i++) {
            double slope = derivative.applyAsDouble(x);
            if (slope == 0) {
                throw new ArithmeticException("Derivative is zero at " + x);
            }
            double step = f.applyAsDouble(x) / slope;
            x -= step;
            if (Math.abs(step) <= tolerance) {
                return x;
            }
        }
        throw new ArithmeticException("No convergence after " + maxIterations + " iterations");
    }
}
