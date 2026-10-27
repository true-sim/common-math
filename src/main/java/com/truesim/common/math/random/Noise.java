package com.truesim.common.math.random;

/**
 * Deterministic gradient noise for procedural content: improved Perlin noise (2D, 3D), simplex noise (2D, 3D) and
 * fractal sums of octaves. Values are roughly in [-1, 1], vary smoothly, and are identical on every platform for
 * the same seed. Instances are immutable and thread-safe.
 */
public final class Noise {

    private static final double F2 = 0.5 * (Math.sqrt(3) - 1);
    private static final double G2 = (3 - Math.sqrt(3)) / 6;
    private static final double F3 = 1.0 / 3;
    private static final double G3 = 1.0 / 6;

    /** The 12 edge directions of a cube, as (x, y, z) triples. */
    private static final int[] GRADIENTS = {
        1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1, 0,
        1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, -1,
        0, 1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1,
    };

    private final int[] perm = new int[512];

    private Noise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        Rng.seeded(seed).shuffle(p);
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
        }
    }

    /** Noise whose values are fully determined by {@code seed}. */
    public static Noise seeded(long seed) {
        return new Noise(seed);
    }

    // ---------------------------------------------------------------- Perlin

    /** 2D improved Perlin noise. */
    public double perlin(double x, double y) {
        double fx = Math.floor(x);
        double fy = Math.floor(y);
        int xi = (int) fx & 255;
        int yi = (int) fy & 255;
        x -= fx;
        y -= fy;
        double u = fade(x);
        double v = fade(y);
        int a = perm[xi] + yi;
        int b = perm[xi + 1] + yi;
        return lerp(v,
            lerp(u, gradient(perm[a], x, y), gradient(perm[b], x - 1, y)),
            lerp(u, gradient(perm[a + 1], x, y - 1), gradient(perm[b + 1], x - 1, y - 1)));
    }

    /** 3D improved Perlin noise. */
    public double perlin(double x, double y, double z) {
        double fx = Math.floor(x);
        double fy = Math.floor(y);
        double fz = Math.floor(z);
        int xi = (int) fx & 255;
        int yi = (int) fy & 255;
        int zi = (int) fz & 255;
        x -= fx;
        y -= fy;
        z -= fz;
        double u = fade(x);
        double v = fade(y);
        double w = fade(z);
        int a = perm[xi] + yi;
        int aa = perm[a] + zi;
        int ab = perm[a + 1] + zi;
        int b = perm[xi + 1] + yi;
        int ba = perm[b] + zi;
        int bb = perm[b + 1] + zi;
        return lerp(w,
            lerp(v,
                lerp(u, gradient(perm[aa], x, y, z), gradient(perm[ba], x - 1, y, z)),
                lerp(u, gradient(perm[ab], x, y - 1, z), gradient(perm[bb], x - 1, y - 1, z))),
            lerp(v,
                lerp(u, gradient(perm[aa + 1], x, y, z - 1), gradient(perm[ba + 1], x - 1, y, z - 1)),
                lerp(u, gradient(perm[ab + 1], x, y - 1, z - 1), gradient(perm[bb + 1], x - 1, y - 1, z - 1))));
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double gradient(int hash, double x, double y) {
        int g = (hash % 12) * 3;
        return GRADIENTS[g] * x + GRADIENTS[g + 1] * y;
    }

    private static double gradient(int hash, double x, double y, double z) {
        int g = (hash % 12) * 3;
        return GRADIENTS[g] * x + GRADIENTS[g + 1] * y + GRADIENTS[g + 2] * z;
    }

    // ---------------------------------------------------------------- simplex

    /** 2D simplex noise: cheaper than Perlin, with fewer directional artifacts. */
    public double simplex(double x, double y) {
        double s = (x + y) * F2;
        double fi = Math.floor(x + s);
        double fj = Math.floor(y + s);
        double t = (fi + fj) * G2;
        double x0 = x - (fi - t);
        double y0 = y - (fj - t);
        int i1 = x0 > y0 ? 1 : 0;
        int j1 = 1 - i1;
        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1 + 2 * G2;
        double y2 = y0 - 1 + 2 * G2;
        int ii = (int) fi & 255;
        int jj = (int) fj & 255;
        double n = corner(perm[ii + perm[jj]], x0, y0)
            + corner(perm[ii + i1 + perm[jj + j1]], x1, y1)
            + corner(perm[ii + 1 + perm[jj + 1]], x2, y2);
        return 70 * n;
    }

    private static double corner(int hash, double x, double y) {
        double t = 0.5 - x * x - y * y;
        return t < 0 ? 0 : t * t * t * t * gradient(hash, x, y);
    }

    /** 3D simplex noise. */
    public double simplex(double x, double y, double z) {
        double s = (x + y + z) * F3;
        double fi = Math.floor(x + s);
        double fj = Math.floor(y + s);
        double fk = Math.floor(z + s);
        double t = (fi + fj + fk) * G3;
        double x0 = x - (fi - t);
        double y0 = y - (fj - t);
        double z0 = z - (fk - t);
        // Which simplex of the cube: offsets of the second and third corner.
        int i1;
        int j1;
        int k1;
        int i2;
        int j2;
        int k2;
        if (x0 >= y0) {
            if (y0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
            } else if (x0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1;
            } else {
                i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1;
            }
        } else if (y0 < z0) {
            i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1;
        } else if (x0 < z0) {
            i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1;
        } else {
            i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
        }
        int ii = (int) fi & 255;
        int jj = (int) fj & 255;
        int kk = (int) fk & 255;
        double n = corner(perm[ii + perm[jj + perm[kk]]], x0, y0, z0)
            + corner(perm[ii + i1 + perm[jj + j1 + perm[kk + k1]]], x0 - i1 + G3, y0 - j1 + G3, z0 - k1 + G3)
            + corner(perm[ii + i2 + perm[jj + j2 + perm[kk + k2]]], x0 - i2 + 2 * G3, y0 - j2 + 2 * G3, z0 - k2 + 2 * G3)
            + corner(perm[ii + 1 + perm[jj + 1 + perm[kk + 1]]], x0 - 1 + 3 * G3, y0 - 1 + 3 * G3, z0 - 1 + 3 * G3);
        return 32 * n;
    }

    private static double corner(int hash, double x, double y, double z) {
        double t = 0.6 - x * x - y * y - z * z;
        return t < 0 ? 0 : t * t * t * t * gradient(hash, x, y, z);
    }

    // ---------------------------------------------------------------- fractal

    /**
     * Fractal (fBm) sum of {@code octaves} layers of 2D simplex noise, each at twice the frequency and half the
     * amplitude of the previous one; normalized to roughly [-1, 1].
     */
    public double fractal(double x, double y, int octaves) {
        checkOctaves(octaves);
        double sum = 0;
        double amplitude = 1;
        double norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amplitude * simplex(x, y);
            norm += amplitude;
            amplitude *= 0.5;
            x *= 2;
            y *= 2;
        }
        return sum / norm;
    }

    /** Fractal sum of {@code octaves} layers of 3D simplex noise; see {@link #fractal(double, double, int)}. */
    public double fractal(double x, double y, double z, int octaves) {
        checkOctaves(octaves);
        double sum = 0;
        double amplitude = 1;
        double norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amplitude * simplex(x, y, z);
            norm += amplitude;
            amplitude *= 0.5;
            x *= 2;
            y *= 2;
            z *= 2;
        }
        return sum / norm;
    }

    private static void checkOctaves(int octaves) {
        if (octaves < 1) {
            throw new IllegalArgumentException("Need at least one octave, got " + octaves);
        }
    }
}
