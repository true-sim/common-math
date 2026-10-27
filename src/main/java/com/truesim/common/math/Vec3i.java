package com.truesim.common.math;

/**
 * Immutable 3D integer vector, e.g. voxel or chunk coordinates. Arithmetic wraps on overflow like {@code int}.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Vec3i(int x, int y, int z) {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    public static final Vec3i ZERO = new Vec3i(0, 0, 0);
    public static final Vec3i ONE = new Vec3i(1, 1, 1);
    public static final Vec3i UNIT_X = new Vec3i(1, 0, 0);
    public static final Vec3i UNIT_Y = new Vec3i(0, 1, 0);
    public static final Vec3i UNIT_Z = new Vec3i(0, 0, 1);

    public static Vec3i of(int x, int y, int z) {
        return new Vec3i(x, y, z);
    }

    public Vec3i add(Vec3i other) {
        return new Vec3i(x + other.x, y + other.y, z + other.z);
    }

    public Vec3i sub(Vec3i other) {
        return new Vec3i(x - other.x, y - other.y, z - other.z);
    }

    public Vec3i scale(int factor) {
        return new Vec3i(x * factor, y * factor, z * factor);
    }

    public Vec3i negate() {
        return new Vec3i(-x, -y, -z);
    }

    public long dot(Vec3i other) {
        return (long) x * other.x + (long) y * other.y + (long) z * other.z;
    }

    /** Grid distance moving along axes only. */
    public int manhattanDistance(Vec3i other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y) + Math.abs(z - other.z);
    }

    /** Grid distance when diagonal moves count as one step. */
    public int chebyshevDistance(Vec3i other) {
        return Math.max(Math.abs(x - other.x), Math.max(Math.abs(y - other.y), Math.abs(z - other.z)));
    }

    public Vec3i min(Vec3i other) {
        return new Vec3i(Math.min(x, other.x), Math.min(y, other.y), Math.min(z, other.z));
    }

    public Vec3i max(Vec3i other) {
        return new Vec3i(Math.max(x, other.x), Math.max(y, other.y), Math.max(z, other.z));
    }

    public Vec3 toVec3() {
        return new Vec3(x, y, z);
    }
}
