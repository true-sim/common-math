package com.truesim.common.math;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

/**
 * Immutable 2D vector. Angles are counter-clockwise from +X.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Vec2(double x, double y) implements Linear<Vec2> {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    public static final Vec2 ZERO = new Vec2(0, 0);
    public static final Vec2 ONE = new Vec2(1, 1);
    public static final Vec2 UNIT_X = new Vec2(1, 0);
    public static final Vec2 UNIT_Y = new Vec2(0, 1);

    /** Memory layout: two doubles x, y (16 bytes). */
    public static final StructLayout LAYOUT = Memory.doubles("x", "y");
    /** Memory layout for GPUs: two floats x, y (8 bytes). */
    public static final StructLayout FLOAT_LAYOUT = Memory.floats("x", "y");

    public static Vec2 of(double x, double y) {
        return new Vec2(x, y);
    }

    public static Vec2 splat(double value) {
        return new Vec2(value, value);
    }

    /** Unit vector at {@code radians} counter-clockwise from +X. */
    public static Vec2 fromAngle(double radians) {
        return new Vec2(Maths.cos(radians), Maths.sin(radians));
    }

    // ---------------------------------------------------------------- arithmetic

    @Override
    public Vec2 add(Vec2 other) {
        return new Vec2(x + other.x, y + other.y);
    }

    public Vec2 add(double dx, double dy) {
        return new Vec2(x + dx, y + dy);
    }

    @Override
    public Vec2 sub(Vec2 other) {
        return new Vec2(x - other.x, y - other.y);
    }

    public Vec2 sub(double dx, double dy) {
        return new Vec2(x - dx, y - dy);
    }

    /** Component-wise product. */
    public Vec2 mul(Vec2 other) {
        return new Vec2(x * other.x, y * other.y);
    }

    /** Component-wise quotient. */
    public Vec2 div(Vec2 other) {
        return new Vec2(x / other.x, y / other.y);
    }

    @Override
    public Vec2 scale(double factor) {
        return new Vec2(x * factor, y * factor);
    }

    public Vec2 div(double divisor) {
        return new Vec2(x / divisor, y / divisor);
    }

    public Vec2 negate() {
        return new Vec2(-x, -y);
    }

    /** {@code this + other * factor}. */
    public Vec2 addScaled(Vec2 other, double factor) {
        return new Vec2(Math.fma(other.x, factor, x), Math.fma(other.y, factor, y));
    }

    // ---------------------------------------------------------------- products and lengths

    @Override
    public double dot(Vec2 other) {
        return x * other.x + y * other.y;
    }

    /** The z component of the 3D cross product: positive if {@code other} is counter-clockwise from this. */
    public double cross(Vec2 other) {
        return x * other.y - y * other.x;
    }

    /** This vector rotated 90 degrees counter-clockwise. */
    public Vec2 perpendicular() {
        return new Vec2(-y, x);
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double lengthSquared() {
        return x * x + y * y;
    }

    @Override
    public double distance(Vec2 other) {
        return Math.sqrt(distanceSquared(other));
    }

    public double distanceSquared(Vec2 other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return dx * dx + dy * dy;
    }

    /** This vector scaled to length 1, or {@link #ZERO} if it has length 0. */
    public Vec2 normalize() {
        double length = length();
        double inverse = length == 0 ? 0 : 1 / length;
        return new Vec2(x * inverse, y * inverse);
    }

    public Vec2 clampLength(double maxLength) {
        double lengthSquared = lengthSquared();
        return scale(lengthSquared <= maxLength * maxLength ? 1 : maxLength / Math.sqrt(lengthSquared));
    }

    /** Direction of this vector in radians, in [-pi, pi]. */
    public double angle() {
        return Maths.atan2(y, x);
    }

    /** Signed angle from this vector to {@code other} in radians, positive counter-clockwise. */
    public double angleTo(Vec2 other) {
        return Maths.atan2(cross(other), dot(other));
    }

    /** This vector rotated counter-clockwise by {@code radians}. */
    public Vec2 rotate(double radians) {
        double cos = Maths.cos(radians);
        double sin = Maths.sin(radians);
        return new Vec2(x * cos - y * sin, x * sin + y * cos);
    }

    /** Moves towards {@code target} by at most {@code maxDistance}, arriving exactly. */
    public Vec2 moveTowards(Vec2 target, double maxDistance) {
        double dx = target.x - x;
        double dy = target.y - y;
        double distance = Math.sqrt(dx * dx + dy * dy);
        boolean arrive = distance <= maxDistance;
        double f = arrive ? 0 : maxDistance / distance;
        return new Vec2(arrive ? target.x : Math.fma(dx, f, x), arrive ? target.y : Math.fma(dy, f, y));
    }

    /** Component-wise floor to integer coordinates, e.g. the grid cell containing this point. */
    public Vec2i floor() {
        return new Vec2i((int) Math.floor(x), (int) Math.floor(y));
    }

    // ---------------------------------------------------------------- geometry

    /** Reflection off a surface with the given unit {@code normal}. */
    public Vec2 reflect(Vec2 normal) {
        return addScaled(normal, -2 * dot(normal));
    }

    public Vec2 project(Vec2 onto) {
        double lengthSquared = onto.lengthSquared();
        return onto.scale(lengthSquared == 0 ? 0 : dot(onto) / lengthSquared);
    }

    public Vec2 reject(Vec2 onto) {
        return sub(project(onto));
    }

    @Override
    public Vec2 interpolate(Vec2 to, double t) {
        return new Vec2(Maths.lerp(x, to.x, t), Maths.lerp(y, to.y, t));
    }

    public Vec2 min(Vec2 other) {
        return new Vec2(Math.min(x, other.x), Math.min(y, other.y));
    }

    public Vec2 max(Vec2 other) {
        return new Vec2(Math.max(x, other.x), Math.max(y, other.y));
    }

    public Vec2 abs() {
        return new Vec2(Math.abs(x), Math.abs(y));
    }

    public Vec2 clamp(Vec2 min, Vec2 max) {
        return new Vec2(Maths.clamp(x, min.x, max.x), Maths.clamp(y, min.y, max.y));
    }

    // ---------------------------------------------------------------- components

    public Vec2 withX(double x) {
        return new Vec2(x, y);
    }

    public Vec2 withY(double y) {
        return new Vec2(x, y);
    }

    public Vec2 yx() {
        return new Vec2(y, x);
    }

    public Vec3 extend(double z) {
        return new Vec3(x, y, z);
    }

    public boolean approxEquals(Vec2 other, double epsilon) {
        return Maths.approxEquals(x, other.x, epsilon) && Maths.approxEquals(y, other.y, epsilon);
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y);
    }

    // ---------------------------------------------------------------- memory

    public void write(MemorySegment segment, long offset) {
        segment.set(Memory.DOUBLE, offset, x);
        segment.set(Memory.DOUBLE, offset + 8, y);
    }

    public static Vec2 read(MemorySegment segment, long offset) {
        return new Vec2(segment.get(Memory.DOUBLE, offset), segment.get(Memory.DOUBLE, offset + 8));
    }

    public void writeFloats(MemorySegment segment, long offset) {
        segment.set(Memory.FLOAT, offset, (float) x);
        segment.set(Memory.FLOAT, offset + 4, (float) y);
    }

    public static Vec2 readFloats(MemorySegment segment, long offset) {
        return new Vec2(segment.get(Memory.FLOAT, offset), segment.get(Memory.FLOAT, offset + 4));
    }

    public void write(ByteBuffer buffer, int byteOffset) {
        buffer.putDouble(byteOffset, x).putDouble(byteOffset + 8, y);
    }

    public static Vec2 read(ByteBuffer buffer, int byteOffset) {
        return new Vec2(buffer.getDouble(byteOffset), buffer.getDouble(byteOffset + 8));
    }

    public void write(DoubleBuffer buffer, int index) {
        buffer.put(index, x).put(index + 1, y);
    }

    public static Vec2 read(DoubleBuffer buffer, int index) {
        return new Vec2(buffer.get(index), buffer.get(index + 1));
    }
}
