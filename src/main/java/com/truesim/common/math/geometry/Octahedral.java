package com.truesim.common.math.geometry;

import com.truesim.common.math.Vec2;
import com.truesim.common.math.Vec3;

/**
 * Octahedral maps between directions and the unit square, with +y as the pole: the whole sphere (Engelhardt and
 * Dachsbacher 2008: the octahedron's lower half folded over the square's corners) and the upper hemisphere (the
 * octahedron's upper half turned 45 degrees to fill the square). Octahedral impostors keep one view per grid cell of
 * such a map; normals and environment directions are stored the same way. Each decode inverts its encode.
 *
 * <pre>{@code
 * Vec2 cell = Octahedral.encodeHemisphere(toCamera);                           // the impostor frame to show
 * Vec3 capture = Octahedral.decodeHemisphere((i + 0.5) / n, (j + 0.5) / n);    // where frame (i, j) looks from
 * }</pre>
 */
public final class Octahedral {

    private Octahedral() {
    }

    /** {@code direction} (any length but zero) as a point of [0, 1]², the whole sphere. */
    public static Vec2 encode(Vec3 direction) {
        double sum = Math.abs(direction.x()) + Math.abs(direction.y()) + Math.abs(direction.z());
        double x = direction.x() / sum;
        double z = direction.z() / sum;
        if (direction.y() < 0) {
            double fx = (1 - Math.abs(z)) * sign(x);
            z = (1 - Math.abs(x)) * sign(z);
            x = fx;
        }
        return new Vec2(x * 0.5 + 0.5, z * 0.5 + 0.5);
    }

    /** The unit direction at ({@code u}, {@code v}) in [0, 1]² of the whole-sphere map. */
    public static Vec3 decode(double u, double v) {
        double x = u * 2 - 1;
        double z = v * 2 - 1;
        double y = 1 - Math.abs(x) - Math.abs(z);
        if (y < 0) {
            double fx = (1 - Math.abs(z)) * sign(x);
            z = (1 - Math.abs(x)) * sign(z);
            x = fx;
        }
        return new Vec3(x, y, z).normalize();
    }

    /**
     * {@code direction} (any length but zero; one below the horizon is taken as its mirror above) as a point of
     * [0, 1]², the upper hemisphere.
     */
    public static Vec2 encodeHemisphere(Vec3 direction) {
        double sum = Math.abs(direction.x()) + Math.abs(direction.y()) + Math.abs(direction.z());
        double x = direction.x() / sum;
        double z = direction.z() / sum;
        return new Vec2((x + z) * 0.5 + 0.5, (x - z) * 0.5 + 0.5);
    }

    /** The unit direction at ({@code u}, {@code v}) in [0, 1]² of the hemisphere map; its y is at least 0. */
    public static Vec3 decodeHemisphere(double u, double v) {
        double a = u * 2 - 1;
        double b = v * 2 - 1;
        double x = (a + b) * 0.5;
        double z = (a - b) * 0.5;
        return new Vec3(x, 1 - Math.abs(x) - Math.abs(z), z).normalize();
    }

    private static double sign(double value) {
        return value >= 0 ? 1 : -1;
    }
}
