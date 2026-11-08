package com.truesim.common.math.random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Ridged and cellular noise, and Poisson-disc sampling. */
class ProceduralNoiseTest {

    @Test
    void ridgedNoiseIsInUnitRangeDeterministicAndHasCrests() {
        Noise noise = Noise.seeded(3);
        double max = 0;
        double min = 1;
        for (int i = 0; i < 2000; i++) {
            double x = i * 0.137;
            double v = noise.ridged(x, x * 0.71, -x * 0.29, 4);
            assertThat(v).isBetween(0.0, 1.0).isEqualTo(Noise.seeded(3).ridged(x, x * 0.71, -x * 0.29, 4));
            max = Math.max(max, v);
            min = Math.min(min, v);
        }
        assertThat(max).isGreaterThan(0.8); // crests where the noise crosses zero
        assertThat(min).isLessThan(0.5);
        assertThatThrownBy(() -> noise.ridged(0, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cellularIsTheDistanceToTheNearestFeaturePoint() {
        Noise noise = Noise.seeded(11);
        double max = 0;
        int nearZero = 0;
        for (int i = 0; i < 4000; i++) {
            double x = i * 0.0731 - 40;
            double y = (i % 37) * 0.31 - 3;
            double z = (i % 11) * 0.53 + 1;
            double f1 = noise.cellular(x, y, z);
            double edge = noise.cellularEdge(x, y, z);
            assertThat(f1).isBetween(0.0, Math.sqrt(3) * 2);
            assertThat(edge).isGreaterThanOrEqualTo(0);
            max = Math.max(max, f1);
            nearZero += f1 < 0.2 ? 1 : 0;
        }
        assertThat(max).isGreaterThan(0.5);
        assertThat(nearZero).isPositive();
        // Continuous: a small step changes the distance by at most the step
        double a = noise.cellular(1.3, 2.7, -0.4);
        double b = noise.cellular(1.3 + 1e-4, 2.7, -0.4);
        assertThat(Math.abs(a - b)).isLessThanOrEqualTo(1e-4 + 1e-12);
        assertThat(noise.cellular(5.5, 6.5, 7.5)).isEqualTo(Noise.seeded(11).cellular(5.5, 6.5, 7.5));
    }

    @Test
    void poissonDiscPointsKeepTheirDistanceAndFillTheArea() {
        double[] points = new double[2 * 5000];
        int count = PoissonDisc.sample(Rng.seeded(5), 100, 60, 3, 30, points);
        // Bridson packs to about 0.6-0.7 of the hexagonal density: area / (radius² · √3/2) · 0.65 ≈ 500
        assertThat(count).isBetween(380, 700);
        for (int i = 0; i < count; i++) {
            assertThat(points[2 * i]).isBetween(0.0, 100.0);
            assertThat(points[2 * i + 1]).isBetween(0.0, 60.0);
            for (int j = 0; j < i; j++) {
                double dx = points[2 * i] - points[2 * j];
                double dy = points[2 * i + 1] - points[2 * j + 1];
                assertThat(dx * dx + dy * dy).isGreaterThanOrEqualTo(9);
            }
        }
        double[] again = new double[2 * 5000];
        assertThat(PoissonDisc.sample(Rng.seeded(5), 100, 60, 3, 30, again)).isEqualTo(count);
        assertThat(again).isEqualTo(points);
    }

    @Test
    void poissonDiscStopsWhenTheOutputIsFull() {
        double[] few = new double[2 * 10];
        assertThat(PoissonDisc.sample(Rng.seeded(1), 100, 100, 1, 30, few)).isEqualTo(10);
        assertThat(PoissonDisc.sample(Rng.seeded(1), 100, 100, 1, 30, new double[1])).isZero();
        // A rectangle smaller than the radius holds one point
        assertThat(PoissonDisc.sample(Rng.seeded(1), 0.5, 0.5, 1, 30, new double[20])).isEqualTo(1);
    }

    @Test
    void poissonDiscRejectsBadArguments() {
        double[] out = new double[8];
        Rng rng = Rng.seeded(1);
        assertThatThrownBy(() -> PoissonDisc.sample(rng, 0, 1, 1, 1, out)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PoissonDisc.sample(rng, 1, 0, 1, 1, out)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PoissonDisc.sample(rng, 1, 1, 0, 1, out)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PoissonDisc.sample(rng, 1, 1, 1, 0, out)).isInstanceOf(IllegalArgumentException.class);
    }
}
