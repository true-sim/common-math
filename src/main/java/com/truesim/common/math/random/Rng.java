package com.truesim.common.math.random;

import com.truesim.common.math.JitInlining;
import com.truesim.common.math.Maths;
import com.truesim.common.math.Quat;
import com.truesim.common.math.Vec2;
import com.truesim.common.math.Vec3;
import java.util.List;

/**
 * Deterministic pseudo-random numbers: the same seed gives the same sequence on every platform and Java version,
 * e.g. for replays and lockstep simulation. Uses xoshiro256++ (seeded with SplitMix64); fast, with a period of
 * 2^256 - 1. Not cryptographically secure, and not thread-safe: give each thread or system its own {@link #fork()}.
 */
public final class Rng {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    private long s0;
    private long s1;
    private long s2;
    private long s3;

    private Rng(long s0, long s1, long s2, long s3) {
        this.s0 = s0;
        this.s1 = s1;
        this.s2 = s2;
        this.s3 = s3;
    }

    /** A generator whose sequence is fully determined by {@code seed}. */
    public static Rng seeded(long seed) {
        long a = splitMix(seed += 0x9E3779B97F4A7C15L);
        long b = splitMix(seed += 0x9E3779B97F4A7C15L);
        long c = splitMix(seed += 0x9E3779B97F4A7C15L);
        long d = splitMix(seed + 0x9E3779B97F4A7C15L);
        return new Rng(a, b, c, d);
    }

    /** Restores a generator from {@link #state()}, e.g. when loading a saved game. */
    public static Rng fromState(long[] state) {
        if (state.length != 4 || (state[0] | state[1] | state[2] | state[3]) == 0) {
            throw new IllegalArgumentException("State must be four longs, not all zero");
        }
        return new Rng(state[0], state[1], state[2], state[3]);
    }

    private static long splitMix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** The current state, for {@link #fromState}. */
    public long[] state() {
        return new long[] {s0, s1, s2, s3};
    }

    /** An independent copy that continues with the same sequence. */
    public Rng copy() {
        return new Rng(s0, s1, s2, s3);
    }

    /** A new generator seeded from this one, for an independent stream (per entity, per system, ...). */
    public Rng fork() {
        return seeded(nextLong());
    }

    // ---------------------------------------------------------------- numbers

    public long nextLong() {
        long result = Long.rotateLeft(s0 + s3, 23) + s0;
        long t = s1 << 17;
        s2 ^= s0;
        s3 ^= s1;
        s1 ^= s2;
        s0 ^= s3;
        s2 ^= t;
        s3 = Long.rotateLeft(s3, 45);
        return result;
    }

    public int nextInt() {
        return (int) (nextLong() >>> 32);
    }

    /** Uniform in [0, bound), without bias. */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive: " + bound);
        }
        int r = (int) (nextLong() >>> 33);
        int m = bound - 1;
        if ((bound & m) == 0) {
            return (int) ((bound * (long) r) >> 31); // power of two
        }
        for (int u = r; u - (r = u % bound) + m < 0; u = (int) (nextLong() >>> 33)) {
            // reject values from the incomplete last block
        }
        return r;
    }

    /** Uniform in [origin, bound). */
    public int nextInt(int origin, int bound) {
        return origin + nextInt(bound - origin);
    }

    /** Uniform in [0, 1), with 53 random bits. */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** Uniform in [min, max). */
    public double nextDouble(double min, double max) {
        return min + (max - min) * nextDouble();
    }

    public boolean nextBoolean() {
        return nextLong() < 0;
    }

    /** Normally distributed with mean 0 and standard deviation 1 (Marsaglia's polar method). */
    public double nextGaussian() {
        double u;
        double v;
        double s;
        do {
            u = 2 * nextDouble() - 1;
            v = 2 * nextDouble() - 1;
            s = u * u + v * v;
        } while (!(Math.abs(s - 0.5) < 0.5)); // keep s in (0, 1)
        return u * Math.sqrt(-2 * Maths.log(s) / s);
    }

    /** Normally distributed with the given mean and standard deviation. */
    public double nextGaussian(double mean, double standardDeviation) {
        return mean + standardDeviation * nextGaussian();
    }

    // ---------------------------------------------------------------- geometry

    /** Uniform on the unit circle. */
    public Vec2 nextVec2OnCircle() {
        return Vec2.fromAngle(Maths.TAU * nextDouble());
    }

    /** Uniform inside the unit circle. */
    public Vec2 nextVec2InCircle() {
        double x;
        double y;
        do {
            x = 2 * nextDouble() - 1;
            y = 2 * nextDouble() - 1;
        } while (x * x + y * y > 1);
        return new Vec2(x, y);
    }

    /** Uniform on the unit sphere, e.g. a random direction. */
    public Vec3 nextVec3OnSphere() {
        double z = 2 * nextDouble() - 1;
        double angle = Maths.TAU * nextDouble();
        double r = Math.sqrt(1 - z * z);
        return new Vec3(r * Maths.cos(angle), r * Maths.sin(angle), z);
    }

    /** Uniform inside the unit sphere. */
    public Vec3 nextVec3InSphere() {
        double x;
        double y;
        double z;
        do {
            x = 2 * nextDouble() - 1;
            y = 2 * nextDouble() - 1;
            z = 2 * nextDouble() - 1;
        } while (x * x + y * y + z * z > 1);
        return new Vec3(x, y, z);
    }

    /** Uniform random rotation (Shoemake's method). */
    public Quat nextRotation() {
        double u1 = nextDouble();
        double a = Maths.TAU * nextDouble();
        double b = Maths.TAU * nextDouble();
        double s1 = Math.sqrt(1 - u1);
        double s2 = Math.sqrt(u1);
        return new Quat(s1 * Maths.sin(a), s1 * Maths.cos(a), s2 * Maths.sin(b), s2 * Maths.cos(b));
    }

    // ---------------------------------------------------------------- collections

    /** Shuffles {@code values} in place (Fisher-Yates). */
    public void shuffle(int[] values) {
        for (int i = values.length - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            int t = values[i];
            values[i] = values[j];
            values[j] = t;
        }
    }

    /** Shuffles a mutable {@code list} in place (Fisher-Yates). */
    public <T> void shuffle(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            list.set(j, list.set(i, list.get(j)));
        }
    }

    /** A uniformly chosen element of {@code list}. */
    public <T> T pick(List<T> list) {
        return list.get(nextInt(list.size()));
    }
}
