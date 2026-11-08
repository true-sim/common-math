package com.truesim.common.math.random;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Maths;

/**
 * Poisson-disc sampling in a rectangle (Bridson 2007): points no closer to each other than a given distance, packed
 * until no more fit, with no grid pattern. Blue noise for scattering trees, rocks and grass clumps, for crown
 * attraction points and for sample patterns. Deterministic for a seeded {@link Rng}.
 *
 * <pre>{@code
 * double[] points = new double[2 * 4096];
 * int count = PoissonDisc.sample(Rng.seeded(7), 200, 200, 6, 30, points);   // trees at least 6 m apart
 * for (int i = 0; i < count; i++) plant(points[2 * i], points[2 * i + 1]);
 * }</pre>
 */
public final class PoissonDisc {

    private PoissonDisc() {
    }

    /**
     * Samples [0, {@code width}) x [0, {@code height}) with points at least {@code radius} apart, trying
     * {@code attempts} candidates around each point before it retires (30 is customary), starting from a random point.
     * Writes x, y pairs to {@code out} and returns how many points it wrote; it stops early when {@code out} is full.
     */
    @AllowAllocation("a grid and an active list per call")
    public static int sample(Rng rng, double width, double height, double radius, int attempts, double[] out) {
        if (!(width > 0 && height > 0 && radius > 0) || attempts < 1) {
            throw new IllegalArgumentException("Need a positive size, radius and number of attempts: " + width + ", "
                + height + ", " + radius + ", " + attempts);
        }
        double cell = radius / Math.sqrt(2);
        int columns = (int) Math.ceil(width / cell);
        int rows = (int) Math.ceil(height / cell);
        int[] grid = new int[columns * rows]; // point index + 1, 0 empty: one point at most per cell
        int capacity = out.length / 2;
        int[] active = new int[capacity];
        if (capacity == 0) {
            return 0;
        }
        int count = 0;
        int actives = 0;
        double r2 = radius * radius;
        count = add(out, grid, columns, cell, count, rng.nextDouble() * width, rng.nextDouble() * height);
        active[actives++] = 0;
        while (actives > 0 && count < capacity) {
            int pick = rng.nextInt(actives);
            int p = active[pick];
            double px = out[2 * p];
            double py = out[2 * p + 1];
            boolean placed = false;
            for (int k = 0; k < attempts && !placed; k++) {
                // A candidate in the annulus between radius and twice it, uniform by area
                double angle = rng.nextDouble() * Maths.TAU;
                double distance = radius * Math.sqrt(1 + 3 * rng.nextDouble());
                double x = px + distance * Maths.cos(angle);
                double y = py + distance * Maths.sin(angle);
                if (x >= 0 && x < width && y >= 0 && y < height && free(out, grid, columns, rows, cell, x, y, r2)) {
                    active[actives++] = count;
                    count = add(out, grid, columns, cell, count, x, y);
                    placed = true;
                }
            }
            if (!placed) {
                active[pick] = active[--actives];
            }
        }
        return count;
    }

    private static int add(double[] out, int[] grid, int columns, double cell, int count, double x, double y) {
        out[2 * count] = x;
        out[2 * count + 1] = y;
        grid[(int) (y / cell) * columns + (int) (x / cell)] = count + 1;
        return count + 1;
    }

    /** Whether no point lies within {@code sqrt(r2)} of (x, y): the two cells around it in each direction suffice. */
    private static boolean free(double[] out, int[] grid, int columns, int rows, double cell, double x, double y,
        double r2) {
        int cx = (int) (x / cell);
        int cy = (int) (y / cell);
        for (int gy = Math.max(cy - 2, 0); gy <= Math.min(cy + 2, rows - 1); gy++) {
            for (int gx = Math.max(cx - 2, 0); gx <= Math.min(cx + 2, columns - 1); gx++) {
                int q = grid[gy * columns + gx] - 1;
                if (q >= 0) {
                    double dx = out[2 * q] - x;
                    double dy = out[2 * q + 1] - y;
                    if (dx * dx + dy * dy < r2) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
