package com.truesim.common.math;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

/**
 * Immutable 3D vector. Coordinates are right-handed with +Y up and -Z forward.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Vec3(double x, double y, double z) implements Linear<Vec3> {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 ONE = new Vec3(1, 1, 1);
    public static final Vec3 UNIT_X = new Vec3(1, 0, 0);
    public static final Vec3 UNIT_Y = new Vec3(0, 1, 0);
    public static final Vec3 UNIT_Z = new Vec3(0, 0, 1);
    public static final Vec3 RIGHT = UNIT_X;
    public static final Vec3 UP = UNIT_Y;
    public static final Vec3 FORWARD = new Vec3(0, 0, -1);

    /** Memory layout: three doubles x, y, z (24 bytes). */
    public static final StructLayout LAYOUT = Memory.doubles("x", "y", "z");
    /** Memory layout for GPUs: three floats x, y, z (12 bytes). */
    public static final StructLayout FLOAT_LAYOUT = Memory.floats("x", "y", "z");

    public static Vec3 of(double x, double y, double z) {
        return new Vec3(x, y, z);
    }

    /** A vector with all components set to {@code value}. */
    public static Vec3 splat(double value) {
        return new Vec3(value, value, value);
    }

    // ---------------------------------------------------------------- arithmetic

    @Override
    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 add(double dx, double dy, double dz) {
        return new Vec3(x + dx, y + dy, z + dz);
    }

    @Override
    public Vec3 sub(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 sub(double dx, double dy, double dz) {
        return new Vec3(x - dx, y - dy, z - dz);
    }

    /** Component-wise product. */
    public Vec3 mul(Vec3 other) {
        return new Vec3(x * other.x, y * other.y, z * other.z);
    }

    /** Component-wise quotient. */
    public Vec3 div(Vec3 other) {
        return new Vec3(x / other.x, y / other.y, z / other.z);
    }

    @Override
    public Vec3 scale(double factor) {
        return new Vec3(x * factor, y * factor, z * factor);
    }

    public Vec3 div(double divisor) {
        return new Vec3(x / divisor, y / divisor, z / divisor);
    }

    public Vec3 negate() {
        return new Vec3(-x, -y, -z);
    }

    /** {@code this + other * factor}, e.g. {@code position.addScaled(velocity, dt)}. */
    public Vec3 addScaled(Vec3 other, double factor) {
        return new Vec3(Math.fma(other.x, factor, x), Math.fma(other.y, factor, y), Math.fma(other.z, factor, z));
    }

    // ---------------------------------------------------------------- products and lengths

    @Override
    public double dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3 cross(Vec3 other) {
        return new Vec3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    @Override
    public double distance(Vec3 other) {
        return Math.sqrt(distanceSquared(other));
    }

    public double distanceSquared(Vec3 other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** This vector scaled to length 1, or {@link #ZERO} if it has length 0. */
    public Vec3 normalize() {
        double length = length();
        return length == 0 ? ZERO : new Vec3(x / length, y / length, z / length);
    }

    /** This vector shortened to at most {@code maxLength}. */
    public Vec3 clampLength(double maxLength) {
        double lengthSquared = lengthSquared();
        if (lengthSquared <= maxLength * maxLength) {
            return this;
        }
        return scale(maxLength / Math.sqrt(lengthSquared));
    }

    /** Unsigned angle to {@code other} in radians, in [0, pi]. */
    public double angle(Vec3 other) {
        return Maths.atan2(cross(other).length(), dot(other));
    }

    // ---------------------------------------------------------------- geometry

    /** Reflection off a surface with the given unit {@code normal}. */
    public Vec3 reflect(Vec3 normal) {
        return addScaled(normal, -2 * dot(normal));
    }

    /** Projection onto {@code onto}, which does not need to be normalized. */
    public Vec3 project(Vec3 onto) {
        double lengthSquared = onto.lengthSquared();
        return lengthSquared == 0 ? ZERO : onto.scale(dot(onto) / lengthSquared);
    }

    /** The part of this vector perpendicular to {@code onto}. */
    public Vec3 reject(Vec3 onto) {
        return sub(project(onto));
    }

    @Override
    public Vec3 interpolate(Vec3 to, double t) {
        return new Vec3(Maths.lerp(x, to.x, t), Maths.lerp(y, to.y, t), Maths.lerp(z, to.z, t));
    }

    public Vec3 min(Vec3 other) {
        return new Vec3(Math.min(x, other.x), Math.min(y, other.y), Math.min(z, other.z));
    }

    public Vec3 max(Vec3 other) {
        return new Vec3(Math.max(x, other.x), Math.max(y, other.y), Math.max(z, other.z));
    }

    public Vec3 abs() {
        return new Vec3(Math.abs(x), Math.abs(y), Math.abs(z));
    }

    /** Component-wise clamp between {@code min} and {@code max}. */
    public Vec3 clamp(Vec3 min, Vec3 max) {
        return new Vec3(Maths.clamp(x, min.x, max.x), Maths.clamp(y, min.y, max.y), Maths.clamp(z, min.z, max.z));
    }

    // ---------------------------------------------------------------- components

    public Vec3 withX(double x) {
        return new Vec3(x, y, z);
    }

    public Vec3 withY(double y) {
        return new Vec3(x, y, z);
    }

    public Vec3 withZ(double z) {
        return new Vec3(x, y, z);
    }

    public Vec2 xy() {
        return new Vec2(x, y);
    }

    public Vec2 xz() {
        return new Vec2(x, z);
    }

    public Vec2 yz() {
        return new Vec2(y, z);
    }

    /** This vector with a fourth component, e.g. {@code w = 1} for points and {@code w = 0} for directions. */
    public Vec4 extend(double w) {
        return new Vec4(x, y, z, w);
    }

    public boolean approxEquals(Vec3 other, double epsilon) {
        return Maths.approxEquals(x, other.x, epsilon)
            && Maths.approxEquals(y, other.y, epsilon)
            && Maths.approxEquals(z, other.z, epsilon);
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    // ---------------------------------------------------------------- memory

    /** Writes x, y, z as doubles at {@code offset} bytes, in native byte order. */
    public void write(MemorySegment segment, long offset) {
        segment.set(Memory.DOUBLE, offset, x);
        segment.set(Memory.DOUBLE, offset + 8, y);
        segment.set(Memory.DOUBLE, offset + 16, z);
    }

    public static Vec3 read(MemorySegment segment, long offset) {
        return new Vec3(
            segment.get(Memory.DOUBLE, offset),
            segment.get(Memory.DOUBLE, offset + 8),
            segment.get(Memory.DOUBLE, offset + 16));
    }

    /** Writes x, y, z as floats at {@code offset} bytes, e.g. for vertex buffers. */
    public void writeFloats(MemorySegment segment, long offset) {
        segment.set(Memory.FLOAT, offset, (float) x);
        segment.set(Memory.FLOAT, offset + 4, (float) y);
        segment.set(Memory.FLOAT, offset + 8, (float) z);
    }

    public static Vec3 readFloats(MemorySegment segment, long offset) {
        return new Vec3(
            segment.get(Memory.FLOAT, offset),
            segment.get(Memory.FLOAT, offset + 4),
            segment.get(Memory.FLOAT, offset + 8));
    }

    /** Writes x, y, z as doubles at {@code byteOffset}, in the buffer's byte order. */
    public void write(ByteBuffer buffer, int byteOffset) {
        buffer.putDouble(byteOffset, x).putDouble(byteOffset + 8, y).putDouble(byteOffset + 16, z);
    }

    public static Vec3 read(ByteBuffer buffer, int byteOffset) {
        return new Vec3(buffer.getDouble(byteOffset), buffer.getDouble(byteOffset + 8), buffer.getDouble(byteOffset + 16));
    }

    /** Writes x, y, z at {@code index}, {@code index + 1} and {@code index + 2}. */
    public void write(DoubleBuffer buffer, int index) {
        buffer.put(index, x).put(index + 1, y).put(index + 2, z);
    }

    public static Vec3 read(DoubleBuffer buffer, int index) {
        return new Vec3(buffer.get(index), buffer.get(index + 1), buffer.get(index + 2));
    }
}
