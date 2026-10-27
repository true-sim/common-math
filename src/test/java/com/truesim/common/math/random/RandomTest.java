package com.truesim.common.math.random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RandomTest {

    @Test
    void algorithmIsPinned() {
        // xoshiro256++ reference: state {1, 2, 3, 4} gives rotl(1 + 4, 23) + 1 first.
        assertThat(Rng.fromState(new long[] {1, 2, 3, 4}).nextLong()).isEqualTo(41943041L);
        // SplitMix64 seeding: the first output for seed 0 is the published reference value.
        assertThat(Rng.seeded(0).state()[0]).isEqualTo(0xE220A8397B1DCDAFL);
    }

    @Test
    void sameSeedSameSequence() {
        Rng a = Rng.seeded(42);
        Rng b = Rng.seeded(42);
        for (int i = 0; i < 100; i++) {
            assertThat(a.nextLong()).isEqualTo(b.nextLong());
        }
        assertThat(Rng.seeded(1).nextLong()).isNotEqualTo(Rng.seeded(2).nextLong());
    }

    @Test
    void stateCopyAndFork() {
        Rng rng = Rng.seeded(7);
        rng.nextLong();
        Rng restored = Rng.fromState(rng.state());
        Rng copy = rng.copy();
        long next = rng.nextLong();
        assertThat(restored.nextLong()).isEqualTo(next);
        assertThat(copy.nextLong()).isEqualTo(next);
        assertThat(rng.fork().nextLong()).isNotEqualTo(rng.nextLong());
        assertThatThrownBy(() -> Rng.fromState(new long[3])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Rng.fromState(new long[4])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void integersAreInRangeAndUniform() {
        Rng rng = Rng.seeded(3);
        int[] counts = new int[6];
        for (int i = 0; i < 60_000; i++) {
            counts[rng.nextInt(6)]++;
        }
        for (int count : counts) {
            assertThat(count).isBetween(9_400, 10_600);
        }
        for (int i = 0; i < 1_000; i++) {
            assertThat(rng.nextInt(8)).isBetween(0, 7); // power of two
            assertThat(rng.nextInt(1_500_000_000)).isBetween(0, 1_499_999_999); // exercises rejection
            assertThat(rng.nextInt(-5, 5)).isBetween(-5, 4);
        }
        rng.nextInt();
        assertThatThrownBy(() -> rng.nextInt(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doublesBooleansAndGaussians() {
        Rng rng = Rng.seeded(11);
        double sum = 0;
        double sumSquares = 0;
        int trues = 0;
        for (int i = 0; i < 50_000; i++) {
            assertThat(rng.nextDouble()).isGreaterThanOrEqualTo(0).isLessThan(1);
            assertThat(rng.nextDouble(-2, 3)).isGreaterThanOrEqualTo(-2).isLessThan(3);
            double g = rng.nextGaussian();
            sum += g;
            sumSquares += g * g;
            trues += rng.nextBoolean() ? 1 : 0;
        }
        assertThat(sum / 50_000).isCloseTo(0, within(0.02));
        assertThat(sumSquares / 50_000).isCloseTo(1, within(0.03));
        assertThat(trues).isBetween(24_000, 26_000);
        assertThat(rng.nextGaussian(100, 0)).isEqualTo(100.0);
    }

    @Test
    void geometry() {
        Rng rng = Rng.seeded(5);
        for (int i = 0; i < 1_000; i++) {
            assertThat(rng.nextVec2OnCircle().length()).isCloseTo(1, within(1e-12));
            assertThat(rng.nextVec2InCircle().length()).isLessThanOrEqualTo(1);
            assertThat(rng.nextVec3OnSphere().length()).isCloseTo(1, within(1e-12));
            assertThat(rng.nextVec3InSphere().length()).isLessThanOrEqualTo(1);
            assertThat(rng.nextRotation().length()).isCloseTo(1, within(1e-12));
        }
    }

    @Test
    void collections() {
        Rng rng = Rng.seeded(9);
        int[] values = {0, 1, 2, 3, 4, 5, 6, 7};
        rng.shuffle(values);
        assertThat(Arrays.stream(values).sorted().toArray()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
        assertThat(values).isNotEqualTo(new int[] {0, 1, 2, 3, 4, 5, 6, 7});
        List<String> list = new ArrayList<>(List.of("a", "b", "c", "d", "e"));
        rng.shuffle(list);
        assertThat(list).containsExactlyInAnyOrder("a", "b", "c", "d", "e");
        assertThat(rng.pick(list)).isIn(list);
    }

    @Test
    void noiseIsDeterministicSmoothAndBounded() {
        Noise noise = Noise.seeded(1);
        Noise same = Noise.seeded(1);
        Noise other = Noise.seeded(2);
        Rng rng = Rng.seeded(1);
        double differences = 0;
        for (int i = 0; i < 20_000; i++) {
            double x = rng.nextDouble(-100, 100);
            double y = rng.nextDouble(-100, 100);
            double z = rng.nextDouble(-100, 100);
            for (double value : new double[] {
                noise.perlin(x, y), noise.perlin(x, y, z), noise.simplex(x, y), noise.simplex(x, y, z)}) {
                assertThat(value).isBetween(-1.05, 1.05);
            }
            assertThat(noise.simplex(x, y, z)).isEqualTo(same.simplex(x, y, z));
            differences += Math.abs(noise.perlin(x, y) - other.perlin(x, y));
            assertThat(Math.abs(noise.simplex(x, y) - noise.simplex(x + 1e-6, y))).isLessThan(1e-3);
            assertThat(Math.abs(noise.perlin(x, y, z) - noise.perlin(x, y, z + 1e-6))).isLessThan(1e-3);
        }
        assertThat(differences).isGreaterThan(1_000);
        assertThat(noise.perlin(3, 4)).isEqualTo(0.0); // zero on the integer lattice
        assertThat(noise.perlin(3, 4, 5)).isEqualTo(0.0);
    }

    @Test
    void fractalNoise() {
        Noise noise = Noise.seeded(3);
        assertThat(noise.fractal(1.3, 2.7, 1)).isEqualTo(noise.simplex(1.3, 2.7));
        assertThat(noise.fractal(1.3, 2.7, 0.4, 1)).isEqualTo(noise.simplex(1.3, 2.7, 0.4));
        assertThat(noise.fractal(1.3, 2.7, 5)).isBetween(-1.0, 1.0);
        assertThat(noise.fractal(1.3, 2.7, 0.4, 5)).isBetween(-1.0, 1.0);
        assertThatThrownBy(() -> noise.fractal(0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> noise.fractal(0, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
