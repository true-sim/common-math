package com.truesim.common.math.numeric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.truesim.common.math.Maths;
import com.truesim.common.math.Scalar;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.interpolation.Motion;
import org.junit.jupiter.api.Test;

class NumericTest {

    private static final Acceleration<Vec3> GRAVITY = (position, velocity, time) -> Vec3.of(0, -9.81, 0);
    private static final Acceleration<Scalar> SPRING = (position, velocity, time) -> position.scale(-1); // x'' = -x

    @Test
    void integratorsUnderConstantAcceleration() {
        Motion<Vec3> start = new Motion<>(Vec3.ZERO, Vec3.of(10, 10, 0));
        Vec3 exact = Vec3.of(10, 10 - 0.5 * 9.81, 0); // after 1 s
        for (Integrator integrator : new Integrator[] {Integrator.VELOCITY_VERLET, Integrator.RK4}) {
            Motion<Vec3> m = start;
            for (int i = 0; i < 100; i++) {
                m = integrator.step(m, GRAVITY, i * 0.01, 0.01);
            }
            assertThat(m.value().approxEquals(exact, 1e-9)).as(integrator.name()).isTrue();
        }
        Motion<Vec3> euler = start;
        for (int i = 0; i < 100; i++) {
            euler = Integrator.SEMI_IMPLICIT_EULER.step(euler, GRAVITY, i * 0.01, 0.01);
        }
        assertThat(euler.value().approxEquals(exact, 0.06)).isTrue(); // first order: close, not exact
        assertThat(euler.velocity().approxEquals(Vec3.of(10, 10 - 9.81, 0), 1e-9)).isTrue();
    }

    @Test
    void integratorsOnAnOscillator() {
        for (Integrator integrator : Integrator.values()) {
            Motion<Scalar> m = new Motion<>(Scalar.ONE, Scalar.ZERO);
            double dt = 0.001;
            int steps = (int) Math.round(Maths.TAU / dt); // one full period
            for (int i = 0; i < steps; i++) {
                m = integrator.step(m, SPRING, i * dt, dt);
            }
            assertThat(m.value().value()).as(integrator.name()).isCloseTo(1, within(0.01));
        }
    }

    @Test
    void quadraticEquations() {
        assertThat(Polynomials.solveQuadratic(0, 0, 1)).isEqualTo(Roots.NONE);
        assertThat(Polynomials.solveQuadratic(0, 2, -4).r0()).isEqualTo(2.0);
        assertThat(Polynomials.solveQuadratic(1, 0, 1).count()).isZero();
        Roots double1 = Polynomials.solveQuadratic(1, -2, 1);
        assertThat(double1.count()).isEqualTo(1);
        assertThat(double1.r0()).isEqualTo(1.0);
        Roots two = Polynomials.solveQuadratic(1, -3, 2);
        assertThat(two.count()).isEqualTo(2);
        assertThat(two.r0()).isEqualTo(1.0);
        assertThat(two.r1()).isEqualTo(2.0);
        Roots negativeB = Polynomials.solveQuadratic(1, 3, 2);
        assertThat(negativeB.r0()).isEqualTo(-2.0);
        assertThat(negativeB.r1()).isEqualTo(-1.0);
        // Stable even when roots differ by many orders of magnitude.
        Roots wide = Polynomials.solveQuadratic(1, -1e8, 1);
        assertThat(wide.r0()).isCloseTo(1e-8, within(1e-20));
    }

    @Test
    void cubicEquations() {
        assertThat(Polynomials.solveCubic(0, 1, -3, 2).count()).isEqualTo(2);
        Roots three = Polynomials.solveCubic(1, -6, 11, -6); // (x-1)(x-2)(x-3)
        assertThat(three.count()).isEqualTo(3);
        assertThat(three.r0()).isCloseTo(1, within(1e-12));
        assertThat(three.r1()).isCloseTo(2, within(1e-12));
        assertThat(three.r2()).isCloseTo(3, within(1e-12));
        Roots one = Polynomials.solveCubic(1, 0, 0, -8); // x^3 = 8
        assertThat(one.count()).isEqualTo(1);
        assertThat(one.r0()).isCloseTo(2, within(1e-12));
        Roots triple = Polynomials.solveCubic(2, 0, 0, 0);
        assertThat(triple.count()).isEqualTo(1);
        assertThat(triple.r0()).isEqualTo(0.0);
        Roots doubleRoot = Polynomials.solveCubic(1, -3, 0, 0); // x^2 (x - 3)
        assertThat(doubleRoot.count()).isEqualTo(2);
        assertThat(doubleRoot.r0()).isCloseTo(0, within(1e-12));
        assertThat(doubleRoot.r1()).isCloseTo(3, within(1e-12));
    }

    @Test
    void roots() {
        Roots three = Roots.of(-1, 2, 5);
        assertThat(three.get(0)).isEqualTo(-1.0);
        assertThat(three.get(1)).isEqualTo(2.0);
        assertThat(three.get(2)).isEqualTo(5.0);
        assertThatThrownBy(() -> Roots.of(1).get(1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThat(three.smallestAtLeast(0)).isEqualTo(2.0);
        assertThat(three.smallestAtLeast(6)).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(Roots.NONE.smallestAtLeast(0)).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    void rootFinding() {
        assertThat(RootFinding.bisect(x -> x * x - 2, 0, 2, 1e-12)).isCloseTo(Math.sqrt(2), within(1e-11));
        assertThat(RootFinding.bisect(x -> 2 - x * x, 0, 2, 1e-12)).isCloseTo(Math.sqrt(2), within(1e-11));
        assertThatThrownBy(() -> RootFinding.bisect(x -> x * x + 1, -1, 1, 1e-9))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(RootFinding.newton(x -> x * x - 2, x -> 2 * x, 1, 1e-14, 50)).isCloseTo(Math.sqrt(2), within(1e-14));
        assertThatThrownBy(() -> RootFinding.newton(x -> x * x + 1, x -> 2 * x, 0, 1e-9, 50))
            .isInstanceOf(ArithmeticException.class).hasMessageContaining("zero");
        assertThatThrownBy(() -> RootFinding.newton(x -> x * x + 1, x -> 2 * x, 3, 1e-9, 5))
            .isInstanceOf(ArithmeticException.class).hasMessageContaining("convergence");
    }
}
