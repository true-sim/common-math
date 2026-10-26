package com.truesim.common.math;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

/**
 * Immutable 4D vector, e.g. homogeneous coordinates.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Vec4(double x, double y, double z, double w) implements Linear<Vec4> {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    public static final Vec4 ZERO = new Vec4(0, 0, 0, 0);
    public static final Vec4 ONE = new Vec4(1, 1, 1, 1);
    public static final Vec4 UNIT_X = new Vec4(1, 0, 0, 0);
    public static final Vec4 UNIT_Y = new Vec4(0, 1, 0, 0);
    public static final Vec4 UNIT_Z = new Vec4(0, 0, 1, 0);
    public static final Vec4 UNIT_W = new Vec4(0, 0, 0, 1);

    /** Memory layout: four doubles x, y, z, w (32 bytes). */
    public static final StructLayout LAYOUT = Memory.doubles("x", "y", "z", "w");
    /** Memory layout for GPUs: four floats x, y, z, w (16 bytes). */
    public static final StructLayout FLOAT_LAYOUT = Memory.floats("x", "y", "z", "w");

    public static Vec4 of(double x, double y, double z, double w) {
        return new Vec4(x, y, z, w);
    }

    public static Vec4 splat(double value) {
        return new Vec4(value, value, value, value);
    }

    @Override
    public Vec4 add(Vec4 other) {
        return new Vec4(x + other.x, y + other.y, z + other.z, w + other.w);
    }

    @Override
    public Vec4 sub(Vec4 other) {
        return new Vec4(x - other.x, y - other.y, z - other.z, w - other.w);
    }

    /** Component-wise product. */
    public Vec4 mul(Vec4 other) {
        return new Vec4(x * other.x, y * other.y, z * other.z, w * other.w);
    }

    /** Component-wise quotient. */
    public Vec4 div(Vec4 other) {
        return new Vec4(x / other.x, y / other.y, z / other.z, w / other.w);
    }

    @Override
    public Vec4 scale(double factor) {
        return new Vec4(x * factor, y * factor, z * factor, w * factor);
    }

    public Vec4 div(double divisor) {
        return new Vec4(x / divisor, y / divisor, z / divisor, w / divisor);
    }

    public Vec4 negate() {
        return new Vec4(-x, -y, -z, -w);
    }

    @Override
    public double dot(Vec4 other) {
        return x * other.x + y * other.y + z * other.z + w * other.w;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double lengthSquared() {
        return x * x + y * y + z * z + w * w;
    }

    @Override
    public double distance(Vec4 other) {
        return sub(other).length();
    }

    /** This vector scaled to length 1, or {@link #ZERO} if it has length 0. */
    public Vec4 normalize() {
        double length = length();
        return length == 0 ? ZERO : new Vec4(x / length, y / length, z / length, w / length);
    }

    @Override
    public Vec4 interpolate(Vec4 to, double t) {
        return new Vec4(Maths.lerp(x, to.x, t), Maths.lerp(y, to.y, t), Maths.lerp(z, to.z, t), Maths.lerp(w, to.w, t));
    }

    public Vec4 min(Vec4 other) {
        return new Vec4(Math.min(x, other.x), Math.min(y, other.y), Math.min(z, other.z), Math.min(w, other.w));
    }

    public Vec4 max(Vec4 other) {
        return new Vec4(Math.max(x, other.x), Math.max(y, other.y), Math.max(z, other.z), Math.max(w, other.w));
    }

    public Vec4 abs() {
        return new Vec4(Math.abs(x), Math.abs(y), Math.abs(z), Math.abs(w));
    }

    public Vec4 withX(double x) {
        return new Vec4(x, y, z, w);
    }

    public Vec4 withY(double y) {
        return new Vec4(x, y, z, w);
    }

    public Vec4 withZ(double z) {
        return new Vec4(x, y, z, w);
    }

    public Vec4 withW(double w) {
        return new Vec4(x, y, z, w);
    }

    public Vec3 xyz() {
        return new Vec3(x, y, z);
    }

    /** {@code (x/w, y/w, z/w)}: from homogeneous coordinates to a 3D point. */
    public Vec3 perspectiveDivide() {
        return new Vec3(x / w, y / w, z / w);
    }

    public boolean approxEquals(Vec4 other, double epsilon) {
        return Maths.approxEquals(x, other.x, epsilon)
            && Maths.approxEquals(y, other.y, epsilon)
            && Maths.approxEquals(z, other.z, epsilon)
            && Maths.approxEquals(w, other.w, epsilon);
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Double.isFinite(w);
    }

    // ---------------------------------------------------------------- memory

    public void write(MemorySegment segment, long offset) {
        segment.set(Memory.DOUBLE, offset, x);
        segment.set(Memory.DOUBLE, offset + 8, y);
        segment.set(Memory.DOUBLE, offset + 16, z);
        segment.set(Memory.DOUBLE, offset + 24, w);
    }

    public static Vec4 read(MemorySegment segment, long offset) {
        return new Vec4(
            segment.get(Memory.DOUBLE, offset),
            segment.get(Memory.DOUBLE, offset + 8),
            segment.get(Memory.DOUBLE, offset + 16),
            segment.get(Memory.DOUBLE, offset + 24));
    }

    public void writeFloats(MemorySegment segment, long offset) {
        segment.set(Memory.FLOAT, offset, (float) x);
        segment.set(Memory.FLOAT, offset + 4, (float) y);
        segment.set(Memory.FLOAT, offset + 8, (float) z);
        segment.set(Memory.FLOAT, offset + 12, (float) w);
    }

    public static Vec4 readFloats(MemorySegment segment, long offset) {
        return new Vec4(
            segment.get(Memory.FLOAT, offset),
            segment.get(Memory.FLOAT, offset + 4),
            segment.get(Memory.FLOAT, offset + 8),
            segment.get(Memory.FLOAT, offset + 12));
    }

    public void write(ByteBuffer buffer, int byteOffset) {
        buffer.putDouble(byteOffset, x).putDouble(byteOffset + 8, y)
            .putDouble(byteOffset + 16, z).putDouble(byteOffset + 24, w);
    }

    public static Vec4 read(ByteBuffer buffer, int byteOffset) {
        return new Vec4(buffer.getDouble(byteOffset), buffer.getDouble(byteOffset + 8),
            buffer.getDouble(byteOffset + 16), buffer.getDouble(byteOffset + 24));
    }

    public void write(DoubleBuffer buffer, int index) {
        buffer.put(index, x).put(index + 1, y).put(index + 2, z).put(index + 3, w);
    }

    public static Vec4 read(DoubleBuffer buffer, int index) {
        return new Vec4(buffer.get(index), buffer.get(index + 1), buffer.get(index + 2), buffer.get(index + 3));
    }
}
