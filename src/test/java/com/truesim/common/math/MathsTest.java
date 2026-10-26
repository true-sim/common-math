package com.truesim.common.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.SplittableRandom;
import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;

class MathsTest {

    private static final double[] SPECIAL = {
        0.0, -0.0, Double.MIN_VALUE, -Double.MIN_VALUE, Double.MIN_NORMAL, 1e-300, 1e-20, 1e-9, 1e-5, 0.1, 0.25, 0.3,
        0.5, 0.75, 0.78125, 0.8, 1.0, 1.1875, 1.5, 2.0, 2.4375, 3.0, Math.PI / 4, Math.PI / 2, Math.PI, 3 * Math.PI / 2,
        2 * Math.PI, 10, 100, 1e5, 1e6, 1e10, 1e20, 1e300, Double.MAX_VALUE,
    };

    /** Asserts {@code actual} is within {@code ulps} units in the last place of StrictMath's result. */
    private static void assertUlps(double actual, double expected, double ulps, double x) {
        if (Double.isNaN(expected)) {
            assertThat(actual).as("f(%s)", x).isNaN();
        } else if (Double.isInfinite(expected) || expected == 0) {
            assertThat(actual).as("f(%s)", x).isEqualTo(expected);
        } else {
            assertThat(Math.abs(actual - expected)).as("f(%s) = %s, expected %s", x, actual, expected)
                .isLessThanOrEqualTo(ulps * Math.ulp(expected));
        }
    }

    private static void compare(DoubleUnaryOperator ours, DoubleUnaryOperator reference, double ulps, double range) {
        SplittableRandom random = new SplittableRandom(42);
        for (double x : SPECIAL) {
            assertUlps(ours.applyAsDouble(x), reference.applyAsDouble(x), ulps, x);
            assertUlps(ours.applyAsDouble(-x), reference.applyAsDouble(-x), ulps, -x);
        }
        for (int i = 0; i < 20_000; i++) {
            double x = random.nextDouble(-range, range);
            assertUlps(ours.applyAsDouble(x), reference.applyAsDouble(x), ulps, x);
        }
        for (double special : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertUlps(ours.applyAsDouble(special), reference.applyAsDouble(special), ulps, special);
        }
    }

    // ---------------------------------------------------------------- trigonometry

    @Test
    void sinMatchesStrictMath() {
        DoubleUnaryOperator ours = Maths::sin;
        compareTrig(ours, StrictMath::sin);
    }

    @Test
    void cosMatchesStrictMath() {
        compareTrig(Maths::cos, StrictMath::cos);
    }

    @Test
    void tanMatchesStrictMath() {
        compareTrig(Maths::tan, StrictMath::tan);
    }

    private static void compareTrig(DoubleUnaryOperator ours, DoubleUnaryOperator reference) {
        SplittableRandom random = new SplittableRandom(7);
        for (int i = 0; i < 20_000; i++) {
            double x = random.nextDouble(-100, 100);
            assertUlps(ours.applyAsDouble(x), reference.applyAsDouble(x), 2, x);
        }
        for (int i = 0; i < 2_000; i++) {
            double x = random.nextDouble(-8e5, 8e5);
            assertUlps(ours.applyAsDouble(x), reference.applyAsDouble(x), 2, x);
        }
        // Multiples of pi/2 need the second and third reduction steps.
        for (int n = -40; n <= 40; n++) {
            double x = n * (Math.PI / 2);
            assertUlps(ours.applyAsDouble(x), reference.applyAsDouble(x), 2, x);
        }
        for (double x : new double[] {0, -0.0, 1e-30, 0.1, 0.5, 0.78, 1, 2.5, 4, 6, -1, -2.5, -4, -6}) {
            assertUlps(ours.applyAsDouble(x), reference.applyAsDouble(x), 2, x);
        }
        assertThat(ours.applyAsDouble(Double.NaN)).isNaN();
        assertThat(ours.applyAsDouble(Double.POSITIVE_INFINITY)).isNaN();
        assertThat(ours.applyAsDouble(Double.NEGATIVE_INFINITY)).isNaN();
    }

    @Test
    void hugeTrigArgumentsAreDeterministicAndBounded() {
        for (double x : new double[] {1e7, -3.3e9, 1e15, 1e300, -Double.MAX_VALUE}) {
            assertThat(Maths.sin(x)).isBetween(-1.0, 1.0).isEqualTo(Maths.sin(x));
            assertThat(Maths.cos(x)).isBetween(-1.0, 1.0);
            assertThat(Maths.tan(x)).isNotNaN();
        }
        // Accurate while the multiple of 2*pi still fits a double's precision
        assertThat(Maths.sin(1e7)).isCloseTo(StrictMath.sin(1e7), within(1e-8));
    }

    @Test
    void inverseTrigMatchesStrictMath() {
        compare(Maths::atan, StrictMath::atan, 1, 1e3);
        compare(Maths::asin, StrictMath::asin, 2, 1);
        compare(Maths::acos, StrictMath::acos, 2, 1);
        assertThat(Maths.atan(1e30)).isEqualTo(StrictMath.atan(1e30));
        assertThat(Maths.atan(-1e30)).isEqualTo(StrictMath.atan(-1e30));
        assertThat(Maths.asin(1.5)).isNaN();
        assertThat(Maths.acos(-1.5)).isNaN();
    }

    @Test
    void atan2MatchesStrictMath() {
        double[] values = {0.0, -0.0, 1e-300, -1e-300, 0.5, -0.5, 1, -1, 3, -3, 1e300, -1e300,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN};
        for (double y : values) {
            for (double x : values) {
                assertUlps(Maths.atan2(y, x), StrictMath.atan2(y, x), 2, y);
            }
        }
        SplittableRandom random = new SplittableRandom(3);
        for (int i = 0; i < 20_000; i++) {
            double y = random.nextDouble(-10, 10);
            double x = random.nextDouble(-10, 10);
            assertUlps(Maths.atan2(y, x), StrictMath.atan2(y, x), 2, y);
        }
        assertThat(Maths.atan2(1e-300, -1e300)).isEqualTo(StrictMath.atan2(1e-300, -1e300));
    }

    @Test
    void degreesAndRadians() {
        assertThat(Maths.toRadians(180)).isEqualTo(Maths.PI);
        assertThat(Maths.toDegrees(Maths.HALF_PI)).isEqualTo(90.0);
        assertThat(Maths.TAU).isEqualTo(2 * Math.PI);
        assertThat(Maths.E).isEqualTo(Math.E);
    }

    // ---------------------------------------------------------------- exponential and logarithm

    @Test
    void expMatchesStrictMath() {
        compare(Maths::exp, StrictMath::exp, 1, 750);
        for (double x : new double[] {709.5, 709.79, 710, -745, -745.2, -746, -720, -1000, 0.3, -0.3, 0.5, -0.5, 1e-10}) {
            assertUlps(Maths.exp(x), StrictMath.exp(x), 1, x);
        }
    }

    @Test
    void expm1IsAccurateNearZero() {
        compare(Maths::expm1, StrictMath::expm1, 3, 50);
        assertThat(Maths.expm1(1e-20)).isEqualTo(1e-20);
        assertThat(Maths.expm1(-50)).isEqualTo(-1.0);
        assertThat(Maths.expm1(1000)).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    void logMatchesStrictMath() {
        compare(Maths::log, StrictMath::log, 1, 1e6);
        for (double x : new double[] {1, 2, 4, 1 + 1e-7, 2 * (1 + 1e-7), 0.5, 1.5, 0.7, Double.MIN_VALUE, 1e-310, -1, -1e-310}) {
            assertUlps(Maths.log(x), StrictMath.log(x), 1, x);
        }
        assertThat(Maths.log(0.0)).isEqualTo(Double.NEGATIVE_INFINITY);
        assertThat(Maths.log(-0.0)).isEqualTo(Double.NEGATIVE_INFINITY);
    }

    @Test
    void otherLogarithms() {
        compare(Maths::log1p, StrictMath::log1p, 3, 1e3);
        assertThat(Maths.log1p(1e-20)).isEqualTo(1e-20);
        compare(Maths::log10, StrictMath::log10, 3, 1e6);
        compare(Maths::log2, x -> StrictMath.log(x) / StrictMath.log(2), 3, 1e6);
        assertThat(Maths.log2(1024)).isEqualTo(10.0);
        assertThat(Maths.log2(0.125)).isEqualTo(-3.0);
        assertThat(Maths.log2(0)).isEqualTo(Double.NEGATIVE_INFINITY);
        assertThat(Maths.log2(Double.POSITIVE_INFINITY)).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    void cbrtMatchesStrictMath() {
        compare(Maths::cbrt, StrictMath::cbrt, 1, 1e9);
        assertThat(Maths.cbrt(27)).isEqualTo(3.0);
        assertThat(Maths.cbrt(-8)).isEqualTo(-2.0);
        assertUlps(Maths.cbrt(1e-310), StrictMath.cbrt(1e-310), 1, 1e-310);
    }

    @Test
    void powMatchesStrictMath() {
        double[] values = {0.0, -0.0, 0.5, -0.5, 1, -1, 2, -2, 3, -3, 0.25, 10, 1e10, 1e-10, 64, 65, -65, 1e20, -1e20,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN};
        for (double x : values) {
            for (double y : values) {
                double expected = StrictMath.pow(x, y);
                double actual = Maths.pow(x, y);
                if (Double.isNaN(expected)) {
                    assertThat(actual).as("pow(%s, %s)", x, y).isNaN();
                } else if (Double.isFinite(expected) && expected != 0) {
                    assertThat(actual).as("pow(%s, %s)", x, y).isCloseTo(expected, within(Math.abs(expected) * 1e-13));
                } else {
                    assertThat(actual).as("pow(%s, %s)", x, y).isEqualTo(expected);
                }
            }
        }
        SplittableRandom random = new SplittableRandom(11);
        for (int i = 0; i < 20_000; i++) {
            double x = random.nextDouble(0, 100);
            double y = random.nextDouble(-20, 20);
            assertThat(Maths.pow(x, y)).isCloseTo(StrictMath.pow(x, y), within(StrictMath.pow(x, y) * 1e-13));
        }
        assertThat(Maths.pow(2, 0.5)).isEqualTo(Math.sqrt(2));
        assertThat(Maths.pow(1e10, 100.5)).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(Maths.pow(1e-10, 100.5)).isEqualTo(0.0);
    }

    @Test
    void hypotAvoidsOverflow() {
        assertThat(Maths.hypot(3, 4)).isEqualTo(5.0);
        assertThat(Maths.hypot(-4, 3)).isEqualTo(5.0);
        assertThat(Maths.hypot(1e300, 1e300)).isCloseTo(StrictMath.hypot(1e300, 1e300), within(1e285));
        assertThat(Maths.hypot(1e-300, 1e-300)).isCloseTo(StrictMath.hypot(1e-300, 1e-300), within(1e-315));
        assertThat(Maths.hypot(0, 0)).isEqualTo(0.0);
        assertThat(Maths.hypot(Double.POSITIVE_INFINITY, Double.NaN)).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(Maths.hypot(Double.NaN, Double.NEGATIVE_INFINITY)).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(Maths.hypot(Double.NaN, 1)).isNaN();
        assertThat(Maths.hypot(1, Double.NaN)).isNaN();
        assertThat(Maths.sqrt(16)).isEqualTo(4.0);
    }

    @Test
    void hyperbolicMatchesStrictMath() {
        compare(Maths::sinh, StrictMath::sinh, 4, 30);
        compare(Maths::cosh, StrictMath::cosh, 4, 30);
        compare(Maths::tanh, StrictMath::tanh, 4, 30);
        for (double x : new double[] {100, -100, 709.5, -709.5, 710, -710, 1e-30, -1e-30}) {
            assertUlps(Maths.sinh(x), StrictMath.sinh(x), 4, x);
            assertUlps(Maths.cosh(x), StrictMath.cosh(x), 4, x);
            assertUlps(Maths.tanh(x), StrictMath.tanh(x), 4, x);
        }
    }

    // ---------------------------------------------------------------- scalar helpers

    @Test
    void clampAndLerp() {
        assertThat(Maths.clamp(-1, 0, 1)).isEqualTo(0.0);
        assertThat(Maths.clamp(2, 0, 1)).isEqualTo(1.0);
        assertThat(Maths.clamp(0.5, 0, 1)).isEqualTo(0.5);
        assertThat(Maths.clamp01(1.5)).isEqualTo(1.0);
        assertThat(Maths.lerp(0.1, 0.7, 0)).isEqualTo(0.1);
        assertThat(Maths.lerp(0.1, 0.7, 1)).isEqualTo(0.7);
        assertThat(Maths.lerp(0, 10, 0.25)).isEqualTo(2.5);
        assertThat(Maths.inverseLerp(10, 20, 15)).isEqualTo(0.5);
        assertThat(Maths.remap(5, 0, 10, 100, 200)).isEqualTo(150.0);
    }

    @Test
    void smoothing() {
        assertThat(Maths.smoothstep(0, 1, -1)).isEqualTo(0.0);
        assertThat(Maths.smoothstep(0, 1, 0.5)).isEqualTo(0.5);
        assertThat(Maths.smoothstep(0, 1, 2)).isEqualTo(1.0);
        assertThat(Maths.smootherstep(0, 1, 0.5)).isEqualTo(0.5);
        assertThat(Maths.smootherstep(0, 1, 1)).isEqualTo(1.0);
        double a = Maths.damp(0, 10, 2, 0.5);
        double b = Maths.damp(Maths.damp(0, 10, 2, 0.25), 10, 2, 0.25);
        assertThat(a).isCloseTo(b, within(1e-12));
        assertThat(Maths.moveTowards(0, 10, 3)).isEqualTo(3.0);
        assertThat(Maths.moveTowards(0, -10, 3)).isEqualTo(-3.0);
        assertThat(Maths.moveTowards(9, 10, 3)).isEqualTo(10.0);
        assertThat(Maths.approxEquals(1, 1.0001, 1e-3)).isTrue();
        assertThat(Maths.approxEquals(1, 1.1, 1e-3)).isFalse();
    }

    @Test
    void wrapping() {
        assertThat(Maths.wrap(370, 0, 360)).isEqualTo(10.0);
        assertThat(Maths.wrap(-10, 0, 360)).isEqualTo(350.0);
        assertThat(Maths.wrap(-1e-20, 0, 1)).isEqualTo(0.0);
        assertThat(Maths.wrapAngle(3 * Maths.PI / 2)).isCloseTo(-Maths.PI / 2, within(1e-15));
        assertThat(Maths.deltaAngle(Maths.toRadians(350), Maths.toRadians(10))).isCloseTo(Maths.toRadians(20), within(1e-12));
        assertThat(Maths.lerpAngle(Maths.toRadians(350), Maths.toRadians(10), 0.5)).isCloseTo(Maths.toRadians(360), within(1e-12));
    }
}
