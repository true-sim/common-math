package com.truesim.common.math.geometry;

import com.truesim.common.math.Quat;
import com.truesim.common.math.Vec3;

/**
 * Intersection and overlap tests at three levels, all computing exactly the same thing:
 * <ol>
 *   <li>on the shape records, e.g. {@code sphere.intersects(box)};</li>
 *   <li>here, on vectors and scalars, e.g. {@code sphereAabb(center, radius, min, max)};</li>
 *   <li>here, on plain doubles, e.g. {@code sphereAabb(cx, cy, cz, r, minX, ..., maxZ)}: for data in arrays or
 *       memory, these never create objects.</li>
 * </ol>
 * Ray tests return the ray parameter of the first hit, or {@link Double#POSITIVE_INFINITY} if there is none.
 */
public final class Intersections {

    private static final double MISS = Double.POSITIVE_INFINITY;

    private Intersections() {
    }

    // ---------------------------------------------------------------- point in box

    public static boolean pointAabb(Vec3 p, Vec3 min, Vec3 max) {
        return pointAabb(p.x(), p.y(), p.z(), min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    public static boolean pointAabb(
        double px, double py, double pz,
        double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return px >= minX && px <= maxX && py >= minY && py <= maxY && pz >= minZ && pz <= maxZ;
    }

    // ---------------------------------------------------------------- box and box

    public static boolean aabbAabb(Vec3 minA, Vec3 maxA, Vec3 minB, Vec3 maxB) {
        return aabbAabb(minA.x(), minA.y(), minA.z(), maxA.x(), maxA.y(), maxA.z(),
            minB.x(), minB.y(), minB.z(), maxB.x(), maxB.y(), maxB.z());
    }

    public static boolean aabbAabb(
        double minAx, double minAy, double minAz, double maxAx, double maxAy, double maxAz,
        double minBx, double minBy, double minBz, double maxBx, double maxBy, double maxBz) {
        return minAx <= maxBx && maxAx >= minBx && minAy <= maxBy && maxAy >= minBy && minAz <= maxBz && maxAz >= minBz;
    }

    // ---------------------------------------------------------------- spheres

    public static boolean pointSphere(Vec3 p, Vec3 center, double radius) {
        return pointSphere(p.x(), p.y(), p.z(), center.x(), center.y(), center.z(), radius);
    }

    public static boolean pointSphere(double px, double py, double pz, double cx, double cy, double cz, double r) {
        double dx = px - cx;
        double dy = py - cy;
        double dz = pz - cz;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    public static boolean sphereSphere(Vec3 centerA, double radiusA, Vec3 centerB, double radiusB) {
        return sphereSphere(centerA.x(), centerA.y(), centerA.z(), radiusA, centerB.x(), centerB.y(), centerB.z(), radiusB);
    }

    public static boolean sphereSphere(
        double ax, double ay, double az, double ar, double bx, double by, double bz, double br) {
        return pointSphere(ax, ay, az, bx, by, bz, ar + br);
    }

    public static boolean sphereAabb(Vec3 center, double radius, Vec3 min, Vec3 max) {
        return sphereAabb(center.x(), center.y(), center.z(), radius, min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    public static boolean sphereAabb(
        double cx, double cy, double cz, double r,
        double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double dx = cx - Math.max(minX, Math.min(cx, maxX));
        double dy = cy - Math.max(minY, Math.min(cy, maxY));
        double dz = cz - Math.max(minZ, Math.min(cz, maxZ));
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    // ---------------------------------------------------------------- planes

    /** Whether any part of the box is on the front side of the plane {@code normal . p = distance}. */
    public static boolean aabbPlane(Vec3 min, Vec3 max, Vec3 normal, double distance) {
        return aabbPlane(min.x(), min.y(), min.z(), max.x(), max.y(), max.z(), normal.x(), normal.y(), normal.z(), distance);
    }

    public static boolean aabbPlane(
        double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
        double nx, double ny, double nz, double distance) {
        // The corner furthest along the normal decides.
        double x = nx >= 0 ? maxX : minX;
        double y = ny >= 0 ? maxY : minY;
        double z = nz >= 0 ? maxZ : minZ;
        return nx * x + ny * y + nz * z - distance >= 0;
    }

    /** Whether any part of the sphere is on the front side of the plane {@code normal . p = distance}. */
    public static boolean spherePlane(Vec3 center, double radius, Vec3 normal, double distance) {
        return spherePlane(center.x(), center.y(), center.z(), radius, normal.x(), normal.y(), normal.z(), distance);
    }

    public static boolean spherePlane(
        double cx, double cy, double cz, double r, double nx, double ny, double nz, double distance) {
        return nx * cx + ny * cy + nz * cz - distance >= -r;
    }

    // ---------------------------------------------------------------- rays

    public static double rayPlane(Vec3 origin, Vec3 direction, Vec3 normal, double distance) {
        return rayPlane(origin.x(), origin.y(), origin.z(), direction.x(), direction.y(), direction.z(),
            normal.x(), normal.y(), normal.z(), distance);
    }

    public static double rayPlane(
        double ox, double oy, double oz, double dx, double dy, double dz,
        double nx, double ny, double nz, double distance) {
        double denominator = nx * dx + ny * dy + nz * dz;
        if (Math.abs(denominator) < 1e-12) {
            return MISS; // parallel
        }
        double t = (distance - (nx * ox + ny * oy + nz * oz)) / denominator;
        return t >= 0 ? t : MISS;
    }

    /** Where the ray enters the box (0 if it starts inside). */
    public static double rayAabb(Vec3 origin, Vec3 direction, Vec3 min, Vec3 max) {
        return rayAabb(origin.x(), origin.y(), origin.z(), direction.x(), direction.y(), direction.z(),
            min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    public static double rayAabb(
        double ox, double oy, double oz, double dx, double dy, double dz,
        double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double t1 = (minX - ox) / dx;
        double t2 = (maxX - ox) / dx;
        double near = Math.min(t1, t2);
        double far = Math.max(t1, t2);
        t1 = (minY - oy) / dy;
        t2 = (maxY - oy) / dy;
        near = Math.max(near, Math.min(t1, t2));
        far = Math.min(far, Math.max(t1, t2));
        t1 = (minZ - oz) / dz;
        t2 = (maxZ - oz) / dz;
        near = Math.max(Math.max(near, Math.min(t1, t2)), 0);
        far = Math.min(far, Math.max(t1, t2));
        return near <= far ? near : MISS;
    }

    /** Where the ray hits the sphere's surface (where it exits, if it starts inside). */
    public static double raySphere(Vec3 origin, Vec3 direction, Vec3 center, double radius) {
        return raySphere(origin.x(), origin.y(), origin.z(), direction.x(), direction.y(), direction.z(),
            center.x(), center.y(), center.z(), radius);
    }

    public static double raySphere(
        double ox, double oy, double oz, double dx, double dy, double dz,
        double cx, double cy, double cz, double r) {
        double px = ox - cx;
        double py = oy - cy;
        double pz = oz - cz;
        double a = dx * dx + dy * dy + dz * dz;
        double b = px * dx + py * dy + pz * dz;
        double c = px * px + py * py + pz * pz - r * r;
        double discriminant = b * b - a * c;
        if (discriminant < 0) {
            return MISS;
        }
        double root = Math.sqrt(discriminant);
        double near = (-b - root) / a;
        if (near >= 0) {
            return near;
        }
        double far = (-b + root) / a;
        return far >= 0 ? far : MISS;
    }

    /** Where the ray hits the triangle from either side (Moeller-Trumbore). */
    public static double rayTriangle(Vec3 origin, Vec3 direction, Vec3 a, Vec3 b, Vec3 c) {
        return rayTriangle(origin.x(), origin.y(), origin.z(), direction.x(), direction.y(), direction.z(),
            a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), c.x(), c.y(), c.z());
    }

    public static double rayTriangle(
        double ox, double oy, double oz, double dx, double dy, double dz,
        double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz) {
        double e1x = bx - ax;
        double e1y = by - ay;
        double e1z = bz - az;
        double e2x = cx - ax;
        double e2y = cy - ay;
        double e2z = cz - az;
        double px = dy * e2z - dz * e2y;
        double py = dz * e2x - dx * e2z;
        double pz = dx * e2y - dy * e2x;
        double determinant = e1x * px + e1y * py + e1z * pz;
        if (Math.abs(determinant) < 1e-12) {
            return MISS; // parallel to the triangle
        }
        double inverse = 1 / determinant;
        double sx = ox - ax;
        double sy = oy - ay;
        double sz = oz - az;
        double u = (sx * px + sy * py + sz * pz) * inverse;
        if (u < 0 || u > 1) {
            return MISS;
        }
        double qx = sy * e1z - sz * e1y;
        double qy = sz * e1x - sx * e1z;
        double qz = sx * e1y - sy * e1x;
        double v = (dx * qx + dy * qy + dz * qz) * inverse;
        if (v < 0 || u + v > 1) {
            return MISS;
        }
        double t = (e2x * qx + e2y * qy + e2z * qz) * inverse;
        return t >= 0 ? t : MISS;
    }

    // ---------------------------------------------------------------- oriented boxes
    //
    // An oriented box is center (c), half extents (h) and rotation quaternion (q). Its axes are the columns of the
    // rotation matrix of q.

    public static boolean pointObb(Vec3 p, Vec3 center, Vec3 halfExtents, Quat rotation) {
        return pointObb(p.x(), p.y(), p.z(), center.x(), center.y(), center.z(),
            halfExtents.x(), halfExtents.y(), halfExtents.z(), rotation.x(), rotation.y(), rotation.z(), rotation.w());
    }

    public static boolean pointObb(
        double px, double py, double pz,
        double cx, double cy, double cz, double hx, double hy, double hz,
        double qx, double qy, double qz, double qw) {
        double dx = px - cx;
        double dy = py - cy;
        double dz = pz - cz;
        return Math.abs(dx * axisXx(qy, qz) + dy * axisXy(qx, qy, qz, qw) + dz * axisXz(qx, qy, qz, qw)) <= hx
            && Math.abs(dx * axisYx(qx, qy, qz, qw) + dy * axisYy(qx, qz) + dz * axisYz(qx, qy, qz, qw)) <= hy
            && Math.abs(dx * axisZx(qx, qy, qz, qw) + dy * axisZy(qx, qy, qz, qw) + dz * axisZz(qx, qy)) <= hz;
    }

    /** Where the ray enters the oriented box (0 if it starts inside). */
    public static double rayObb(Vec3 origin, Vec3 direction, Vec3 center, Vec3 halfExtents, Quat rotation) {
        return rayObb(origin.x(), origin.y(), origin.z(), direction.x(), direction.y(), direction.z(),
            center.x(), center.y(), center.z(), halfExtents.x(), halfExtents.y(), halfExtents.z(),
            rotation.x(), rotation.y(), rotation.z(), rotation.w());
    }

    public static double rayObb(
        double ox, double oy, double oz, double dx, double dy, double dz,
        double cx, double cy, double cz, double hx, double hy, double hz,
        double qx, double qy, double qz, double qw) {
        // Express the ray in the box's frame, where the box is axis-aligned; rotation keeps distances.
        double ux = axisXx(qy, qz);
        double uy = axisXy(qx, qy, qz, qw);
        double uz = axisXz(qx, qy, qz, qw);
        double vx = axisYx(qx, qy, qz, qw);
        double vy = axisYy(qx, qz);
        double vz = axisYz(qx, qy, qz, qw);
        double wx = axisZx(qx, qy, qz, qw);
        double wy = axisZy(qx, qy, qz, qw);
        double wz = axisZz(qx, qy);
        double rx = ox - cx;
        double ry = oy - cy;
        double rz = oz - cz;
        return rayAabb(
            rx * ux + ry * uy + rz * uz, rx * vx + ry * vy + rz * vz, rx * wx + ry * wy + rz * wz,
            dx * ux + dy * uy + dz * uz, dx * vx + dy * vy + dz * vz, dx * wx + dy * wy + dz * wz,
            -hx, -hy, -hz, hx, hy, hz);
    }

    /** Whether any part of the oriented box is on the front side of the plane {@code normal . p = distance}. */
    public static boolean obbPlane(Vec3 center, Vec3 halfExtents, Quat rotation, Vec3 normal, double distance) {
        return obbPlane(center.x(), center.y(), center.z(), halfExtents.x(), halfExtents.y(), halfExtents.z(),
            rotation.x(), rotation.y(), rotation.z(), rotation.w(), normal.x(), normal.y(), normal.z(), distance);
    }

    public static boolean obbPlane(
        double cx, double cy, double cz, double hx, double hy, double hz,
        double qx, double qy, double qz, double qw,
        double nx, double ny, double nz, double distance) {
        double radius = hx * Math.abs(nx * axisXx(qy, qz) + ny * axisXy(qx, qy, qz, qw) + nz * axisXz(qx, qy, qz, qw))
            + hy * Math.abs(nx * axisYx(qx, qy, qz, qw) + ny * axisYy(qx, qz) + nz * axisYz(qx, qy, qz, qw))
            + hz * Math.abs(nx * axisZx(qx, qy, qz, qw) + ny * axisZy(qx, qy, qz, qw) + nz * axisZz(qx, qy));
        return spherePlane(cx, cy, cz, radius, nx, ny, nz, distance);
    }

    public static boolean obbObb(
        Vec3 centerA, Vec3 halfExtentsA, Quat rotationA, Vec3 centerB, Vec3 halfExtentsB, Quat rotationB) {
        return obbObb(
            centerA.x(), centerA.y(), centerA.z(), halfExtentsA.x(), halfExtentsA.y(), halfExtentsA.z(),
            rotationA.x(), rotationA.y(), rotationA.z(), rotationA.w(),
            centerB.x(), centerB.y(), centerB.z(), halfExtentsB.x(), halfExtentsB.y(), halfExtentsB.z(),
            rotationB.x(), rotationB.y(), rotationB.z(), rotationB.w());
    }

    /**
     * Separating axis test over the 15 candidate axes (Ericson, Real-Time Collision Detection 4.4.1). Branch-free:
     * the largest separation over all axes decides.
     */
    public static boolean obbObb(
        double acx, double acy, double acz, double ahx, double ahy, double ahz,
        double aqx, double aqy, double aqz, double aqw,
        double bcx, double bcy, double bcz, double bhx, double bhy, double bhz,
        double bqx, double bqy, double bqz, double bqw) {
        double a0x = axisXx(aqy, aqz);
        double a0y = axisXy(aqx, aqy, aqz, aqw);
        double a0z = axisXz(aqx, aqy, aqz, aqw);
        double a1x = axisYx(aqx, aqy, aqz, aqw);
        double a1y = axisYy(aqx, aqz);
        double a1z = axisYz(aqx, aqy, aqz, aqw);
        double a2x = axisZx(aqx, aqy, aqz, aqw);
        double a2y = axisZy(aqx, aqy, aqz, aqw);
        double a2z = axisZz(aqx, aqy);
        double b0x = axisXx(bqy, bqz);
        double b0y = axisXy(bqx, bqy, bqz, bqw);
        double b0z = axisXz(bqx, bqy, bqz, bqw);
        double b1x = axisYx(bqx, bqy, bqz, bqw);
        double b1y = axisYy(bqx, bqz);
        double b1z = axisYz(bqx, bqy, bqz, bqw);
        double b2x = axisZx(bqx, bqy, bqz, bqw);
        double b2y = axisZy(bqx, bqy, bqz, bqw);
        double b2z = axisZz(bqx, bqy);
        // Rotation of B in A's frame, and its absolute values (with an epsilon against parallel edges).
        double r00 = a0x * b0x + a0y * b0y + a0z * b0z;
        double r01 = a0x * b1x + a0y * b1y + a0z * b1z;
        double r02 = a0x * b2x + a0y * b2y + a0z * b2z;
        double r10 = a1x * b0x + a1y * b0y + a1z * b0z;
        double r11 = a1x * b1x + a1y * b1y + a1z * b1z;
        double r12 = a1x * b2x + a1y * b2y + a1z * b2z;
        double r20 = a2x * b0x + a2y * b0y + a2z * b0z;
        double r21 = a2x * b1x + a2y * b1y + a2z * b1z;
        double r22 = a2x * b2x + a2y * b2y + a2z * b2z;
        double e = 1e-12;
        double s00 = Math.abs(r00) + e;
        double s01 = Math.abs(r01) + e;
        double s02 = Math.abs(r02) + e;
        double s10 = Math.abs(r10) + e;
        double s11 = Math.abs(r11) + e;
        double s12 = Math.abs(r12) + e;
        double s20 = Math.abs(r20) + e;
        double s21 = Math.abs(r21) + e;
        double s22 = Math.abs(r22) + e;
        // Translation in A's frame.
        double dx = bcx - acx;
        double dy = bcy - acy;
        double dz = bcz - acz;
        double t0 = dx * a0x + dy * a0y + dz * a0z;
        double t1 = dx * a1x + dy * a1y + dz * a1z;
        double t2 = dx * a2x + dy * a2y + dz * a2z;

        double gap = Math.abs(t0) - ahx - (bhx * s00 + bhy * s01 + bhz * s02); // A's axes
        gap = Math.max(gap, Math.abs(t1) - ahy - (bhx * s10 + bhy * s11 + bhz * s12));
        gap = Math.max(gap, Math.abs(t2) - ahz - (bhx * s20 + bhy * s21 + bhz * s22));
        gap = Math.max(gap, Math.abs(t0 * r00 + t1 * r10 + t2 * r20) - (ahx * s00 + ahy * s10 + ahz * s20) - bhx); // B's
        gap = Math.max(gap, Math.abs(t0 * r01 + t1 * r11 + t2 * r21) - (ahx * s01 + ahy * s11 + ahz * s21) - bhy);
        gap = Math.max(gap, Math.abs(t0 * r02 + t1 * r12 + t2 * r22) - (ahx * s02 + ahy * s12 + ahz * s22) - bhz);
        gap = Math.max(gap, Math.abs(t2 * r10 - t1 * r20) - (ahy * s20 + ahz * s10) - (bhy * s02 + bhz * s01)); // edges
        gap = Math.max(gap, Math.abs(t2 * r11 - t1 * r21) - (ahy * s21 + ahz * s11) - (bhx * s02 + bhz * s00));
        gap = Math.max(gap, Math.abs(t2 * r12 - t1 * r22) - (ahy * s22 + ahz * s12) - (bhx * s01 + bhy * s00));
        gap = Math.max(gap, Math.abs(t0 * r20 - t2 * r00) - (ahx * s20 + ahz * s00) - (bhy * s12 + bhz * s11));
        gap = Math.max(gap, Math.abs(t0 * r21 - t2 * r01) - (ahx * s21 + ahz * s01) - (bhx * s12 + bhz * s10));
        gap = Math.max(gap, Math.abs(t0 * r22 - t2 * r02) - (ahx * s22 + ahz * s02) - (bhx * s11 + bhy * s10));
        gap = Math.max(gap, Math.abs(t1 * r00 - t0 * r10) - (ahx * s10 + ahy * s00) - (bhy * s22 + bhz * s21));
        gap = Math.max(gap, Math.abs(t1 * r01 - t0 * r11) - (ahx * s11 + ahy * s01) - (bhx * s22 + bhz * s20));
        gap = Math.max(gap, Math.abs(t1 * r02 - t0 * r12) - (ahx * s12 + ahy * s02) - (bhx * s21 + bhy * s20));
        return gap <= 0;
    }

    // Columns of the rotation matrix of quaternion (x, y, z, w): the box's local X, Y and Z axes in world space.

    private static double axisXx(double y, double z) {
        return 1 - 2 * (y * y + z * z);
    }

    private static double axisXy(double x, double y, double z, double w) {
        return 2 * (x * y + w * z);
    }

    private static double axisXz(double x, double y, double z, double w) {
        return 2 * (x * z - w * y);
    }

    private static double axisYx(double x, double y, double z, double w) {
        return 2 * (x * y - w * z);
    }

    private static double axisYy(double x, double z) {
        return 1 - 2 * (x * x + z * z);
    }

    private static double axisYz(double x, double y, double z, double w) {
        return 2 * (y * z + w * x);
    }

    private static double axisZx(double x, double y, double z, double w) {
        return 2 * (x * z + w * y);
    }

    private static double axisZy(double x, double y, double z, double w) {
        return 2 * (y * z - w * x);
    }

    private static double axisZz(double x, double y) {
        return 1 - 2 * (x * x + y * y);
    }

    // ---------------------------------------------------------------- segments

    /** The {@code t} in [0, 1] of the point on segment start-end closest to {@code p}. */
    public static double segmentClosestParameter(Vec3 start, Vec3 end, Vec3 p) {
        return segmentClosestParameter(start.x(), start.y(), start.z(), end.x(), end.y(), end.z(), p.x(), p.y(), p.z());
    }

    public static double segmentClosestParameter(
        double sx, double sy, double sz, double ex, double ey, double ez, double px, double py, double pz) {
        double dx = ex - sx;
        double dy = ey - sy;
        double dz = ez - sz;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared == 0) {
            return 0;
        }
        double t = ((px - sx) * dx + (py - sy) * dy + (pz - sz) * dz) / lengthSquared;
        return Math.max(0, Math.min(1, t));
    }
}
