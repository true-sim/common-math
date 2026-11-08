package com.truesim.common.math.curve;

/**
 * Rotation-minimizing frames along a polyline given as plain doubles (double reflection, Wang et al. 2008, as
 * {@link RotationMinimizingFrames} does for curves): for each point a unit tangent and a unit normal perpendicular to
 * it that turns as little as possible from point to point. This is parallel transport of the first normal along the
 * polyline, the frame for sweeping tubes along branches, cables, roots and paths without twisting. Works in place on
 * arrays, so meshing many polylines allocates nothing.
 *
 * <pre>{@code
 * double[] tangents = new double[3 * count];
 * double[] normals = new double[3 * count];
 * PolylineFrames.compute(points, count, 1, 0, 0, tangents, normals);   // first normal near +x
 * // binormal i = tangent i x normal i
 * }</pre>
 */
public final class PolylineFrames {

    private PolylineFrames() {
    }

    /**
     * Frames at the {@code count} points (x, y, z triples) of {@code points}: tangents along the polyline (the
     * normalized sum of the unit directions of the segments meeting at a point; at the ends, the end segment's), and
     * normals transported from ({@code upX}, {@code upY}, {@code upZ}) made perpendicular to the first tangent (another
     * perpendicular if it is parallel to it). Repeated points take the previous point's frame; without any segment, the
     * tangent is +y. Writes {@code 3 * count} values to each output array.
     */
    public static void compute(double[] points, int count, double upX, double upY, double upZ, double[] tangents,
        double[] normals) {
        if (count < 1) {
            throw new IllegalArgumentException("Need at least one point, got " + count);
        }
        tangents(points, count, tangents);
        // The first normal: up without its part along the first tangent
        double tx = tangents[0];
        double ty = tangents[1];
        double tz = tangents[2];
        double along = upX * tx + upY * ty + upZ * tz;
        double rx = upX - along * tx;
        double ry = upY - along * ty;
        double rz = upZ - along * tz;
        double length = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (length < 1e-12) {
            // up was parallel to the tangent: the axis least aligned with it, made perpendicular
            boolean useX = Math.abs(tx) < 0.9;
            rx = useX ? 1 - tx * tx : -tx * ty;
            ry = useX ? -tx * ty : 1 - ty * ty;
            rz = useX ? -tx * tz : -ty * tz;
            length = Math.sqrt(rx * rx + ry * ry + rz * rz);
        }
        normals[0] = rx / length;
        normals[1] = ry / length;
        normals[2] = rz / length;
        for (int i = 1; i < count; i++) {
            transport(points, tangents, normals, 3 * i);
        }
    }

    /** The normal at point index {@code p / 3} from the one before it. */
    private static void transport(double[] points, double[] tangents, double[] normals, int p) {
        int q = p - 3;
        double vx = points[p] - points[q];
        double vy = points[p + 1] - points[q + 1];
        double vz = points[p + 2] - points[q + 2];
        double c1 = vx * vx + vy * vy + vz * vz;
        double nx = normals[q];
        double ny = normals[q + 1];
        double nz = normals[q + 2];
        if (c1 > 0) {
            // Reflect across the plane bisecting the step, then across the one taking the tangent to the next
            double k = 2 / c1 * (vx * nx + vy * ny + vz * nz);
            double lx = nx - k * vx;
            double ly = ny - k * vy;
            double lz = nz - k * vz;
            double kt = 2 / c1 * (vx * tangents[q] + vy * tangents[q + 1] + vz * tangents[q + 2]);
            double wx = tangents[p] - (tangents[q] - kt * vx);
            double wy = tangents[p + 1] - (tangents[q + 1] - kt * vy);
            double wz = tangents[p + 2] - (tangents[q + 2] - kt * vz);
            double c2 = wx * wx + wy * wy + wz * wz;
            double k2 = c2 > 0 ? 2 / c2 * (wx * lx + wy * ly + wz * lz) : 0;
            nx = lx - k2 * wx;
            ny = ly - k2 * wy;
            nz = lz - k2 * wz;
            // Remove what rounding left along the tangent, and renormalize
            double t = nx * tangents[p] + ny * tangents[p + 1] + nz * tangents[p + 2];
            nx -= t * tangents[p];
            ny -= t * tangents[p + 1];
            nz -= t * tangents[p + 2];
            double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= n;
            ny /= n;
            nz /= n;
        }
        normals[p] = nx;
        normals[p + 1] = ny;
        normals[p + 2] = nz;
    }

    private static void tangents(double[] points, int count, double[] tangents) {
        // The unit direction of each segment, added to both its points
        for (int i = 0; i < 3 * count; i++) {
            tangents[i] = 0;
        }
        for (int p = 3; p < 3 * count; p += 3) {
            double dx = points[p] - points[p - 3];
            double dy = points[p + 1] - points[p - 2];
            double dz = points[p + 2] - points[p - 1];
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length > 0) {
                dx /= length;
                dy /= length;
                dz /= length;
                tangents[p - 3] += dx;
                tangents[p - 2] += dy;
                tangents[p - 1] += dz;
                tangents[p] += dx;
                tangents[p + 1] += dy;
                tangents[p + 2] += dz;
            }
        }
        // Normalized; where segments cancel or are missing, the tangent before (+y at the start)
        double px = 0;
        double py = 1;
        double pz = 0;
        for (int p = 0; p < 3 * count; p += 3) {
            double length = Math.sqrt(tangents[p] * tangents[p] + tangents[p + 1] * tangents[p + 1]
                + tangents[p + 2] * tangents[p + 2]);
            if (length > 1e-12) {
                px = tangents[p] / length;
                py = tangents[p + 1] / length;
                pz = tangents[p + 2] / length;
            }
            tangents[p] = px;
            tangents[p + 1] = py;
            tangents[p + 2] = pz;
        }
    }
}
