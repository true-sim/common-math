package com.truesim.common.math;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.util.Objects;

/**
 * Immutable 3x3 matrix: 3D rotation and scale, or a 2D affine transform. Components are named {@code m<row><column>}
 * and given row by row; memory is column-major, as OpenGL and Vulkan expect.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Mat3(
    double m00, double m01, double m02,
    double m10, double m11, double m12,
    double m20, double m21, double m22) implements Interpolatable<Mat3> {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    public static final Mat3 IDENTITY = new Mat3(1, 0, 0, 0, 1, 0, 0, 0, 1);
    public static final Mat3 ZERO = new Mat3(0, 0, 0, 0, 0, 0, 0, 0, 0);

    /** Memory layout: nine doubles, column-major (72 bytes). */
    public static final StructLayout LAYOUT = Memory.doubles("m00", "m10", "m20", "m01", "m11", "m21", "m02", "m12", "m22");
    /** Memory layout for GPUs: nine floats, column-major, tightly packed (36 bytes). */
    public static final StructLayout FLOAT_LAYOUT =
        Memory.floats("m00", "m10", "m20", "m01", "m11", "m21", "m02", "m12", "m22");

    public static Mat3 fromColumns(Vec3 c0, Vec3 c1, Vec3 c2) {
        return new Mat3(c0.x(), c1.x(), c2.x(), c0.y(), c1.y(), c2.y(), c0.z(), c1.z(), c2.z());
    }

    public static Mat3 fromRows(Vec3 r0, Vec3 r1, Vec3 r2) {
        return new Mat3(r0.x(), r0.y(), r0.z(), r1.x(), r1.y(), r1.z(), r2.x(), r2.y(), r2.z());
    }

    // ---------------------------------------------------------------- 3D factories

    public static Mat3 rotation(Quat q) {
        double xx = q.x() * q.x();
        double yy = q.y() * q.y();
        double zz = q.z() * q.z();
        double xy = q.x() * q.y();
        double xz = q.x() * q.z();
        double yz = q.y() * q.z();
        double wx = q.w() * q.x();
        double wy = q.w() * q.y();
        double wz = q.w() * q.z();
        return new Mat3(
            1 - 2 * (yy + zz), 2 * (xy - wz), 2 * (xz + wy),
            2 * (xy + wz), 1 - 2 * (xx + zz), 2 * (yz - wx),
            2 * (xz - wy), 2 * (yz + wx), 1 - 2 * (xx + yy));
    }

    public static Mat3 rotationX(double radians) {
        double c = Maths.cos(radians);
        double s = Maths.sin(radians);
        return new Mat3(1, 0, 0, 0, c, -s, 0, s, c);
    }

    public static Mat3 rotationY(double radians) {
        double c = Maths.cos(radians);
        double s = Maths.sin(radians);
        return new Mat3(c, 0, s, 0, 1, 0, -s, 0, c);
    }

    public static Mat3 rotationZ(double radians) {
        double c = Maths.cos(radians);
        double s = Maths.sin(radians);
        return new Mat3(c, -s, 0, s, c, 0, 0, 0, 1);
    }

    public static Mat3 scaling(Vec3 scale) {
        return new Mat3(scale.x(), 0, 0, 0, scale.y(), 0, 0, 0, scale.z());
    }

    public static Mat3 scaling(double scale) {
        return new Mat3(scale, 0, 0, 0, scale, 0, 0, 0, scale);
    }

    // ---------------------------------------------------------------- 2D factories

    public static Mat3 translation2D(Vec2 offset) {
        return new Mat3(1, 0, offset.x(), 0, 1, offset.y(), 0, 0, 1);
    }

    public static Mat3 rotation2D(double radians) {
        return rotationZ(radians);
    }

    public static Mat3 scaling2D(Vec2 scale) {
        return new Mat3(scale.x(), 0, 0, 0, scale.y(), 0, 0, 0, 1);
    }

    // ---------------------------------------------------------------- operations

    /** {@code this * other}: applies {@code other} first, then this. */
    public Mat3 mul(Mat3 o) {
        return new Mat3(
            m00 * o.m00 + m01 * o.m10 + m02 * o.m20, m00 * o.m01 + m01 * o.m11 + m02 * o.m21, m00 * o.m02 + m01 * o.m12 + m02 * o.m22,
            m10 * o.m00 + m11 * o.m10 + m12 * o.m20, m10 * o.m01 + m11 * o.m11 + m12 * o.m21, m10 * o.m02 + m11 * o.m12 + m12 * o.m22,
            m20 * o.m00 + m21 * o.m10 + m22 * o.m20, m20 * o.m01 + m21 * o.m11 + m22 * o.m21, m20 * o.m02 + m21 * o.m12 + m22 * o.m22);
    }

    /** {@code this * v}. */
    public Vec3 transform(Vec3 v) {
        return new Vec3(
            m00 * v.x() + m01 * v.y() + m02 * v.z(),
            m10 * v.x() + m11 * v.y() + m12 * v.z(),
            m20 * v.x() + m21 * v.y() + m22 * v.z());
    }

    /** Transforms a 2D point (including translation). */
    public Vec2 transformPoint2D(Vec2 p) {
        return new Vec2(m00 * p.x() + m01 * p.y() + m02, m10 * p.x() + m11 * p.y() + m12);
    }

    /** Transforms a 2D direction (ignoring translation). */
    public Vec2 transformDirection2D(Vec2 d) {
        return new Vec2(m00 * d.x() + m01 * d.y(), m10 * d.x() + m11 * d.y());
    }

    public Mat3 transpose() {
        return new Mat3(m00, m10, m20, m01, m11, m21, m02, m12, m22);
    }

    public double determinant() {
        return m00 * (m11 * m22 - m12 * m21) + m01 * (m12 * m20 - m10 * m22) + m02 * (m10 * m21 - m11 * m20);
    }

    /**
     * The inverse matrix.
     *
     * @throws ArithmeticException if the matrix is singular
     */
    public Mat3 inverse() {
        double c00 = m11 * m22 - m12 * m21;
        double c01 = m12 * m20 - m10 * m22;
        double c02 = m10 * m21 - m11 * m20;
        double det = m00 * c00 + m01 * c01 + m02 * c02;
        if (det == 0) {
            throw new ArithmeticException("Matrix is singular");
        }
        double id = 1 / det;
        return new Mat3(
            c00 * id, (m02 * m21 - m01 * m22) * id, (m01 * m12 - m02 * m11) * id,
            c01 * id, (m00 * m22 - m02 * m20) * id, (m02 * m10 - m00 * m12) * id,
            c02 * id, (m01 * m20 - m00 * m21) * id, (m00 * m11 - m01 * m10) * id);
    }

    /** Component at {@code row}, {@code column}. */
    public double get(int row, int column) {
        return switch (Objects.checkIndex(row, 3) * 3 + Objects.checkIndex(column, 3)) {
            case 0 -> m00;
            case 1 -> m01;
            case 2 -> m02;
            case 3 -> m10;
            case 4 -> m11;
            case 5 -> m12;
            case 6 -> m20;
            case 7 -> m21;
            default -> m22;
        };
    }

    public Vec3 column(int index) {
        return new Vec3(get(0, index), get(1, index), get(2, index));
    }

    public Vec3 row(int index) {
        return new Vec3(get(index, 0), get(index, 1), get(index, 2));
    }

    /** Length of each column: the scale of a rotation-and-scale matrix. */
    public Vec3 extractScale() {
        return new Vec3(column(0).length(), column(1).length(), column(2).length());
    }

    /** The rotation of a rotation-and-scale matrix (scale removed). */
    public Quat extractRotation() {
        Vec3 c0 = column(0).normalize();
        Vec3 c1 = column(1).normalize();
        Vec3 c2 = column(2).normalize();
        return Quat.fromRotationMatrix(c0.x(), c1.x(), c2.x(), c0.y(), c1.y(), c2.y(), c0.z(), c1.z(), c2.z());
    }

    /** This matrix as the upper-left part of a 4x4 matrix. */
    public Mat4 toMat4() {
        return new Mat4(m00, m01, m02, 0, m10, m11, m12, 0, m20, m21, m22, 0, 0, 0, 0, 1);
    }

    /** Interpolates rotation (spherically) and scale (linearly) of rotation-and-scale matrices. */
    @Override
    public Mat3 interpolate(Mat3 to, double t) {
        Quat rotation = extractRotation().slerp(to.extractRotation(), t);
        Vec3 scale = extractScale().interpolate(to.extractScale(), t);
        return rotation(rotation).mul(scaling(scale));
    }

    public boolean approxEquals(Mat3 o, double epsilon) {
        return column(0).approxEquals(o.column(0), epsilon)
            && column(1).approxEquals(o.column(1), epsilon)
            && column(2).approxEquals(o.column(2), epsilon);
    }

    // ---------------------------------------------------------------- memory

    /** Writes the nine components as doubles, column-major. */
    public void write(MemorySegment segment, long offset) {
        for (int i = 0; i < 9; i++) {
            segment.set(Memory.DOUBLE, offset + 8L * i, get(i % 3, i / 3));
        }
    }

    public static Mat3 read(MemorySegment segment, long offset) {
        return new Mat3(
            segment.get(Memory.DOUBLE, offset), segment.get(Memory.DOUBLE, offset + 24), segment.get(Memory.DOUBLE, offset + 48),
            segment.get(Memory.DOUBLE, offset + 8), segment.get(Memory.DOUBLE, offset + 32), segment.get(Memory.DOUBLE, offset + 56),
            segment.get(Memory.DOUBLE, offset + 16), segment.get(Memory.DOUBLE, offset + 40), segment.get(Memory.DOUBLE, offset + 64));
    }

    /** Writes the nine components as floats, column-major and tightly packed. */
    public void writeFloats(MemorySegment segment, long offset) {
        for (int i = 0; i < 9; i++) {
            segment.set(Memory.FLOAT, offset + 4L * i, (float) get(i % 3, i / 3));
        }
    }

    public static Mat3 readFloats(MemorySegment segment, long offset) {
        return new Mat3(
            segment.get(Memory.FLOAT, offset), segment.get(Memory.FLOAT, offset + 12), segment.get(Memory.FLOAT, offset + 24),
            segment.get(Memory.FLOAT, offset + 4), segment.get(Memory.FLOAT, offset + 16), segment.get(Memory.FLOAT, offset + 28),
            segment.get(Memory.FLOAT, offset + 8), segment.get(Memory.FLOAT, offset + 20), segment.get(Memory.FLOAT, offset + 32));
    }

    public void write(ByteBuffer buffer, int byteOffset) {
        for (int i = 0; i < 9; i++) {
            buffer.putDouble(byteOffset + 8 * i, get(i % 3, i / 3));
        }
    }

    public static Mat3 read(ByteBuffer buffer, int byteOffset) {
        return new Mat3(
            buffer.getDouble(byteOffset), buffer.getDouble(byteOffset + 24), buffer.getDouble(byteOffset + 48),
            buffer.getDouble(byteOffset + 8), buffer.getDouble(byteOffset + 32), buffer.getDouble(byteOffset + 56),
            buffer.getDouble(byteOffset + 16), buffer.getDouble(byteOffset + 40), buffer.getDouble(byteOffset + 64));
    }

    public void write(DoubleBuffer buffer, int index) {
        for (int i = 0; i < 9; i++) {
            buffer.put(index + i, get(i % 3, i / 3));
        }
    }

    public static Mat3 read(DoubleBuffer buffer, int index) {
        return new Mat3(
            buffer.get(index), buffer.get(index + 3), buffer.get(index + 6),
            buffer.get(index + 1), buffer.get(index + 4), buffer.get(index + 7),
            buffer.get(index + 2), buffer.get(index + 5), buffer.get(index + 8));
    }
}
