package com.truesim.common.math.geometry;

/**
 * Plane geometry on plain doubles: distances to segments, segments crossing, polygons' signed area, convex polygons'
 * containment, depth and overlap (separating axes). Coordinates are (x, y) in the plane (a ground plan's x and z,
 * say); polygons are parallel coordinate arrays of either winding. Square roots only (correctly rounded), so the same
 * on every platform; nothing is allocated.
 *
 * <pre>{@code
 * double d = Polygons.segmentDistance(px, py, ax, ay, bx, by);
 * boolean overlap = Polygons.convexOverlap(plotX, plotY, otherX, otherY, 0.01);
 * double inside = Polygons.convexDepth(px, py, plotX, plotY);        // negative outside
 * }</pre>
 */
public final class Polygons {

    private Polygons() {
    }

    /** The length of ({@code dx}, {@code dy}). */
    public static double length(double dx, double dy) {
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** Where ({@code px}, {@code py}) projects on segment a-b: 0 at a to 1 at b, clamped (0 for a point segment). */
    public static double project(double px, double py, double ax, double ay, double bx, double by) {
        double ex = bx - ax;
        double ey = by - ay;
        double l = ex * ex + ey * ey;
        return l == 0 ? 0 : Math.clamp(((px - ax) * ex + (py - ay) * ey) / l, 0, 1);
    }

    /** The distance from ({@code px}, {@code py}) to segment a-b. */
    public static double segmentDistance(double px, double py, double ax, double ay, double bx, double by) {
        double t = project(px, py, ax, ay, bx, by);
        return length(ax + t * (bx - ax) - px, ay + t * (by - ay) - py);
    }

    /** The z component of (b - a) x (p - a): positive when p lies to the left of a-b (counter-clockwise). */
    public static double cross(double ax, double ay, double bx, double by, double px, double py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }

    /** Whether segments a-b and c-d cross or touch. */
    public static boolean crosses(double ax, double ay, double bx, double by, double cx, double cy, double dx,
        double dy) {
        double d1 = cross(cx, cy, dx, dy, ax, ay);
        double d2 = cross(cx, cy, dx, dy, bx, by);
        double d3 = cross(ax, ay, bx, by, cx, cy);
        double d4 = cross(ax, ay, bx, by, dx, dy);
        if (opposite(d1, d2) && opposite(d3, d4)) {
            return true;
        }
        return d1 == 0 && within(cx, cy, dx, dy, ax, ay) || d2 == 0 && within(cx, cy, dx, dy, bx, by)
            || d3 == 0 && within(ax, ay, bx, by, cx, cy) || d4 == 0 && within(ax, ay, bx, by, dx, dy);
    }

    private static boolean opposite(double a, double b) {
        return a > 0 && b < 0 || a < 0 && b > 0;
    }

    /** Whether point r, on the line through p and q, lies between them. */
    private static boolean within(double px, double py, double qx, double qy, double rx, double ry) {
        return Math.min(px, qx) <= rx && rx <= Math.max(px, qx) && Math.min(py, qy) <= ry && ry <= Math.max(py, qy);
    }

    /** The distance between segments a-b and c-d (0 where they cross or touch). */
    public static double segmentSegmentDistance(double ax, double ay, double bx, double by, double cx, double cy,
        double dx, double dy) {
        if (crosses(ax, ay, bx, by, cx, cy, dx, dy)) {
            return 0;
        }
        return Math.min(Math.min(segmentDistance(ax, ay, cx, cy, dx, dy), segmentDistance(bx, by, cx, cy, dx, dy)),
            Math.min(segmentDistance(cx, cy, ax, ay, bx, by), segmentDistance(dx, dy, ax, ay, bx, by)));
    }

    /** A polygon's signed area: positive when its points turn counter-clockwise (x right, y up). */
    public static double signedArea(double[] x, double[] y) {
        double a = 0;
        for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
            a += x[j] * y[i] - x[i] * y[j];
        }
        return a / 2;
    }

    /** Whether ({@code px}, {@code py}) lies in a convex polygon of either winding (its boundary included). */
    public static boolean insideConvex(double px, double py, double[] x, double[] y) {
        boolean left = false;
        boolean right = false;
        for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
            double c = cross(x[j], y[j], x[i], y[i], px, py);
            left |= c > 0;
            right |= c < 0;
        }
        return !(left && right);
    }

    /**
     * How deep ({@code px}, {@code py}) lies in a convex polygon of either winding: the least distance to its edges'
     * lines, negative outside (there the largest distance outside an edge's line: the true distance where the
     * nearest feature is an edge, less near a corner).
     */
    public static double convexDepth(double px, double py, double[] x, double[] y) {
        double sign = signedArea(x, y) >= 0 ? 1 : -1;
        double depth = Double.MAX_VALUE;
        for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
            double l = length(x[i] - x[j], y[i] - y[j]);
            if (l > 0) {
                depth = Math.min(depth, sign * cross(x[j], y[j], x[i], y[i], px, py) / l);
            }
        }
        return depth;
    }

    /** The distance from segment a-b to a convex polygon (0 where it touches or enters it). */
    public static double segmentPolygonDistance(double ax, double ay, double bx, double by, double[] x, double[] y) {
        if (insideConvex(ax, ay, x, y) || insideConvex(bx, by, x, y)) {
            return 0;
        }
        double best = Double.MAX_VALUE;
        for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
            best = Math.min(best, segmentSegmentDistance(ax, ay, bx, by, x[j], y[j], x[i], y[i]));
        }
        return best;
    }

    /** Whether two convex polygons overlap by more than {@code tolerance} (separating axes; touching is not). */
    public static boolean convexOverlap(double[] ax, double[] ay, double[] bx, double[] by, double tolerance) {
        return !separated(ax, ay, bx, by, tolerance) && !separated(bx, by, ax, ay, tolerance);
    }

    /** Whether one of polygon a's edge normals separates the two by at least -tolerance. */
    private static boolean separated(double[] ax, double[] ay, double[] bx, double[] by, double tolerance) {
        for (int i = 0, j = ax.length - 1; i < ax.length; j = i++) {
            double nx = ay[i] - ay[j];
            double ny = ax[j] - ax[i];
            double l = length(nx, ny);
            if (l == 0) {
                continue;
            }
            nx /= l;
            ny /= l;
            double minA = Double.MAX_VALUE;
            double maxA = -Double.MAX_VALUE;
            for (int k = 0; k < ax.length; k++) {
                double d = ax[k] * nx + ay[k] * ny;
                minA = Math.min(minA, d);
                maxA = Math.max(maxA, d);
            }
            double minB = Double.MAX_VALUE;
            double maxB = -Double.MAX_VALUE;
            for (int k = 0; k < bx.length; k++) {
                double d = bx[k] * nx + by[k] * ny;
                minB = Math.min(minB, d);
                maxB = Math.max(maxB, d);
            }
            if (maxA <= minB + tolerance || maxB <= minA + tolerance) {
                return true;
            }
        }
        return false;
    }
}
