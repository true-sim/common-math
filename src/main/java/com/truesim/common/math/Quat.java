package com.truesim.common.math;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

/**
 * Immutable rotation quaternion {@code (x, y, z, w)}, where {@code w} is the scalar part.
 * {@code a.mul(b)} rotates by {@code b} first, then by {@code a}.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Quat(double x, double y, double z, double w) implements Interpolatable<Quat> {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    public static final Quat IDENTITY = new Quat(0, 0, 0, 1);

    /** Memory layout: four doubles x, y, z, w (32 bytes). */
    public static final StructLayout LAYOUT = Memory.doubles("x", "y", "z", "w");
    /** Memory layout for GPUs: four floats x, y, z, w (16 bytes). */
    public static final StructLayout FLOAT_LAYOUT = Memory.floats("x", "y", "z", "w");

    private static final double NLERP_THRESHOLD = 0.9995;

    public static Quat of(double x, double y, double z, double w) {
        return new Quat(x, y, z, w);
    }

    /** Rotation by {@code radians} counter-clockwise around {@code axis}, which does not need to be normalized. */
    public static Quat fromAxisAngle(Vec3 axis, double radians) {
        Vec3 n = axis.normalize();
        double half = 0.5 * radians;
        double s = Maths.sin(half);
        return new Quat(n.x() * s, n.y() * s, n.z() * s, Maths.cos(half));
    }

    /** Yaw around +Y, then pitch around the rotated +X, then roll around the rotated +Z (intrinsic Y-X-Z). */
    public static Quat fromYawPitchRoll(double yaw, double pitch, double roll) {
        return fromAxisAngle(Vec3.UNIT_Y, yaw)
            .mul(fromAxisAngle(Vec3.UNIT_X, pitch))
            .mul(fromAxisAngle(Vec3.UNIT_Z, roll));
    }

    /** The shortest rotation that turns direction {@code from} into direction {@code to}. */
    public static Quat fromTo(Vec3 from, Vec3 to) {
        Vec3 f = from.normalize();
        Vec3 t = to.normalize();
        double d = f.dot(t);
        if (d >= 1 - 1e-12) {
            return IDENTITY;
        }
        if (d <= -1 + 1e-12) {
            Vec3 axis = Vec3.UNIT_X.cross(f);
            if (axis.lengthSquared() < 1e-12) {
                axis = Vec3.UNIT_Y.cross(f);
            }
            return fromAxisAngle(axis, Maths.PI);
        }
        Vec3 c = f.cross(t);
        double s = Math.sqrt((1 + d) * 2);
        return new Quat(c.x() / s, c.y() / s, c.z() / s, 0.5 * s);
    }

    /**
     * The rotation that points {@link Vec3#FORWARD} (-Z) along {@code forward} and keeps {@link Vec3#UP} as close to
     * {@code up} as possible.
     */
    public static Quat lookRotation(Vec3 forward, Vec3 up) {
        Vec3 zAxis = forward.normalize().negate();
        Vec3 xAxis = up.cross(zAxis).normalize();
        if (xAxis.lengthSquared() == 0) { // forward parallel to up: pick any perpendicular right axis
            xAxis = Math.abs(zAxis.y()) < 0.9 ? Vec3.UNIT_Y.cross(zAxis).normalize() : Vec3.UNIT_X.cross(zAxis).normalize();
        }
        Vec3 yAxis = zAxis.cross(xAxis);
        return fromRotationMatrix(
            xAxis.x(), yAxis.x(), zAxis.x(),
            xAxis.y(), yAxis.y(), zAxis.y(),
            xAxis.z(), yAxis.z(), zAxis.z());
    }

    /** Rotation from an orthonormal rotation matrix, given row by row. */
    static Quat fromRotationMatrix(
        double m00, double m01, double m02,
        double m10, double m11, double m12,
        double m20, double m21, double m22) {
        double trace = m00 + m11 + m22;
        if (trace > 0) {
            double s = Math.sqrt(trace + 1) * 2;
            return new Quat((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25 * s);
        }
        if (m00 > m11 && m00 > m22) {
            double s = Math.sqrt(1 + m00 - m11 - m22) * 2;
            return new Quat(0.25 * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s);
        }
        if (m11 > m22) {
            double s = Math.sqrt(1 + m11 - m00 - m22) * 2;
            return new Quat((m01 + m10) / s, 0.25 * s, (m12 + m21) / s, (m02 - m20) / s);
        }
        double s = Math.sqrt(1 + m22 - m00 - m11) * 2;
        return new Quat((m02 + m20) / s, (m12 + m21) / s, 0.25 * s, (m10 - m01) / s);
    }

    // ---------------------------------------------------------------- algebra

    /** {@code this * other}: rotates by {@code other} first, then by this. */
    public Quat mul(Quat other) {
        return new Quat(
            w * other.x + x * other.w + y * other.z - z * other.y,
            w * other.y - x * other.z + y * other.w + z * other.x,
            w * other.z + x * other.y - y * other.x + z * other.w,
            w * other.w - x * other.x - y * other.y - z * other.z);
    }

    public Quat conjugate() {
        return new Quat(-x, -y, -z, w);
    }

    public Quat inverse() {
        double lengthSquared = lengthSquared();
        return new Quat(-x / lengthSquared, -y / lengthSquared, -z / lengthSquared, w / lengthSquared);
    }

    public double dot(Quat other) {
        return x * other.x + y * other.y + z * other.z + w * other.w;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double lengthSquared() {
        return x * x + y * y + z * z + w * w;
    }

    /** This quaternion scaled to length 1, or {@link #IDENTITY} if it has length 0. */
    public Quat normalize() {
        double length = length();
        return length == 0 ? IDENTITY : new Quat(x / length, y / length, z / length, w / length);
    }

    // ---------------------------------------------------------------- rotation

    /** Rotates {@code v}. */
    public Vec3 rotate(Vec3 v) {
        double tx = 2 * (y * v.z() - z * v.y());
        double ty = 2 * (z * v.x() - x * v.z());
        double tz = 2 * (x * v.y() - y * v.x());
        return new Vec3(
            v.x() + w * tx + (y * tz - z * ty),
            v.y() + w * ty + (z * tx - x * tz),
            v.z() + w * tz + (x * ty - y * tx));
    }

    /** Rotation angle in radians, in [0, pi]. */
    public double angle() {
        return 2 * Maths.atan2(Math.sqrt(x * x + y * y + z * z), Math.abs(w));
    }

    /** Rotation axis, or {@link Vec3#UNIT_X} for the identity rotation. */
    public Vec3 axis() {
        Vec3 axis = new Vec3(x, y, z).normalize();
        if (axis.lengthSquared() == 0) {
            return Vec3.UNIT_X;
        }
        return w < 0 ? axis.negate() : axis;
    }

    /** Angle of the rotation from this orientation to {@code other}, in [0, pi]. */
    public double angleTo(Quat other) {
        return conjugate().mul(other).angle();
    }

    /** The rotated {@link Vec3#RIGHT} (+X) axis. */
    public Vec3 right() {
        return rotate(Vec3.RIGHT);
    }

    /** The rotated {@link Vec3#UP} (+Y) axis. */
    public Vec3 up() {
        return rotate(Vec3.UP);
    }

    /** The rotated {@link Vec3#FORWARD} (-Z) axis. */
    public Vec3 forward() {
        return rotate(Vec3.FORWARD);
    }

    // ---------------------------------------------------------------- interpolation

    /** Spherical interpolation along the shortest arc, at constant angular speed. */
    public Quat slerp(Quat to, double t) {
        double cos = dot(to);
        double sign = 1;
        if (cos < 0) {
            cos = -cos;
            sign = -1;
        }
        if (cos > NLERP_THRESHOLD) {
            return nlerp(to, t);
        }
        double theta = Maths.acos(cos);
        double sin = Maths.sin(theta);
        double a = Maths.sin((1 - t) * theta) / sin;
        double b = sign * Maths.sin(t * theta) / sin;
        return new Quat(a * x + b * to.x, a * y + b * to.y, a * z + b * to.z, a * w + b * to.w);
    }

    /** Normalized linear interpolation along the shortest arc: faster than slerp, but not constant speed. */
    public Quat nlerp(Quat to, double t) {
        double b = dot(to) < 0 ? -t : t;
        double a = 1 - t;
        return new Quat(a * x + b * to.x, a * y + b * to.y, a * z + b * to.z, a * w + b * to.w).normalize();
    }

    /** Same as {@link #slerp}. */
    @Override
    public Quat interpolate(Quat to, double t) {
        return slerp(to, t);
    }

    public boolean approxEquals(Quat other, double epsilon) {
        return Maths.approxEquals(x, other.x, epsilon)
            && Maths.approxEquals(y, other.y, epsilon)
            && Maths.approxEquals(z, other.z, epsilon)
            && Maths.approxEquals(w, other.w, epsilon);
    }

    /** True if both represent the same rotation ({@code q} and {@code -q} do). */
    public boolean approxSameRotation(Quat other, double epsilon) {
        return approxEquals(other, epsilon) || approxEquals(new Quat(-other.x, -other.y, -other.z, -other.w), epsilon);
    }

    // ---------------------------------------------------------------- memory

    public void write(MemorySegment segment, long offset) {
        segment.set(Memory.DOUBLE, offset, x);
        segment.set(Memory.DOUBLE, offset + 8, y);
        segment.set(Memory.DOUBLE, offset + 16, z);
        segment.set(Memory.DOUBLE, offset + 24, w);
    }

    public static Quat read(MemorySegment segment, long offset) {
        return new Quat(
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

    public static Quat readFloats(MemorySegment segment, long offset) {
        return new Quat(
            segment.get(Memory.FLOAT, offset),
            segment.get(Memory.FLOAT, offset + 4),
            segment.get(Memory.FLOAT, offset + 8),
            segment.get(Memory.FLOAT, offset + 12));
    }

    public void write(ByteBuffer buffer, int byteOffset) {
        buffer.putDouble(byteOffset, x).putDouble(byteOffset + 8, y)
            .putDouble(byteOffset + 16, z).putDouble(byteOffset + 24, w);
    }

    public static Quat read(ByteBuffer buffer, int byteOffset) {
        return new Quat(buffer.getDouble(byteOffset), buffer.getDouble(byteOffset + 8),
            buffer.getDouble(byteOffset + 16), buffer.getDouble(byteOffset + 24));
    }

    public void write(DoubleBuffer buffer, int index) {
        buffer.put(index, x).put(index + 1, y).put(index + 2, z).put(index + 3, w);
    }

    public static Quat read(DoubleBuffer buffer, int index) {
        return new Quat(buffer.get(index), buffer.get(index + 1), buffer.get(index + 2), buffer.get(index + 3));
    }
}
