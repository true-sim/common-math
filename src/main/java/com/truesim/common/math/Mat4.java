package com.truesim.common.math;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.util.Objects;

/**
 * Immutable 4x4 matrix for 3D transforms and projections. Components are named {@code m<row><column>} and given row
 * by row; memory is column-major, as OpenGL and Vulkan expect. Vectors are column vectors: {@code m.transform(v)}
 * computes {@code M * v}.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Mat4(
    double m00, double m01, double m02, double m03,
    double m10, double m11, double m12, double m13,
    double m20, double m21, double m22, double m23,
    double m30, double m31, double m32, double m33) implements Interpolatable<Mat4> {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    public static final Mat4 IDENTITY = new Mat4(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1);
    public static final Mat4 ZERO = new Mat4(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    /** Memory layout: sixteen doubles, column-major (128 bytes). */
    public static final StructLayout LAYOUT = Memory.doubles(
        "m00", "m10", "m20", "m30", "m01", "m11", "m21", "m31",
        "m02", "m12", "m22", "m32", "m03", "m13", "m23", "m33");
    /** Memory layout for GPUs: sixteen floats, column-major (64 bytes). */
    public static final StructLayout FLOAT_LAYOUT = Memory.floats(
        "m00", "m10", "m20", "m30", "m01", "m11", "m21", "m31",
        "m02", "m12", "m22", "m32", "m03", "m13", "m23", "m33");

    public static Mat4 fromColumns(Vec4 c0, Vec4 c1, Vec4 c2, Vec4 c3) {
        return new Mat4(
            c0.x(), c1.x(), c2.x(), c3.x(),
            c0.y(), c1.y(), c2.y(), c3.y(),
            c0.z(), c1.z(), c2.z(), c3.z(),
            c0.w(), c1.w(), c2.w(), c3.w());
    }

    // ---------------------------------------------------------------- transforms

    public static Mat4 translation(Vec3 offset) {
        return new Mat4(1, 0, 0, offset.x(), 0, 1, 0, offset.y(), 0, 0, 1, offset.z(), 0, 0, 0, 1);
    }

    public static Mat4 rotation(Quat rotation) {
        return Mat3.rotation(rotation).toMat4();
    }

    public static Mat4 rotationX(double radians) {
        return Mat3.rotationX(radians).toMat4();
    }

    public static Mat4 rotationY(double radians) {
        return Mat3.rotationY(radians).toMat4();
    }

    public static Mat4 rotationZ(double radians) {
        return Mat3.rotationZ(radians).toMat4();
    }

    public static Mat4 scaling(Vec3 scale) {
        return new Mat4(scale.x(), 0, 0, 0, 0, scale.y(), 0, 0, 0, 0, scale.z(), 0, 0, 0, 0, 1);
    }

    public static Mat4 scaling(double scale) {
        return scaling(Vec3.splat(scale));
    }

    /** Translation * rotation * scale: scales first, then rotates, then translates. */
    public static Mat4 trs(Vec3 translation, Quat rotation, Vec3 scale) {
        Mat3 r = Mat3.rotation(rotation);
        return new Mat4(
            r.m00() * scale.x(), r.m01() * scale.y(), r.m02() * scale.z(), translation.x(),
            r.m10() * scale.x(), r.m11() * scale.y(), r.m12() * scale.z(), translation.y(),
            r.m20() * scale.x(), r.m21() * scale.y(), r.m22() * scale.z(), translation.z(),
            0, 0, 0, 1);
    }

    /** View matrix for a camera at {@code eye} looking at {@code target} (right-handed, -Z forward). */
    public static Mat4 lookAt(Vec3 eye, Vec3 target, Vec3 up) {
        Vec3 f = target.sub(eye).normalize();
        Vec3 s = f.cross(up).normalize();
        Vec3 u = s.cross(f);
        return new Mat4(
            s.x(), s.y(), s.z(), -s.dot(eye),
            u.x(), u.y(), u.z(), -u.dot(eye),
            -f.x(), -f.y(), -f.z(), f.dot(eye),
            0, 0, 0, 1);
    }

    // ---------------------------------------------------------------- projections

    /** Perspective projection with depth in [-1, 1] (OpenGL). */
    public static Mat4 perspective(double fovY, double aspect, double near, double far) {
        double f = 1 / Maths.tan(0.5 * fovY);
        double range = near - far;
        return new Mat4(
            f / aspect, 0, 0, 0,
            0, f, 0, 0,
            0, 0, (far + near) / range, 2 * far * near / range,
            0, 0, -1, 0);
    }

    /** Perspective projection with depth in [0, 1] (Vulkan, Direct3D, Metal). */
    public static Mat4 perspectiveZeroToOne(double fovY, double aspect, double near, double far) {
        double f = 1 / Maths.tan(0.5 * fovY);
        double range = near - far;
        return new Mat4(
            f / aspect, 0, 0, 0,
            0, f, 0, 0,
            0, 0, far / range, near * far / range,
            0, 0, -1, 0);
    }

    /**
     * Perspective projection with an infinite far plane and reversed depth: {@code near} maps to 1, infinity to 0.
     * With a floating-point depth buffer this gives the best depth precision (Vulkan, Direct3D, Metal).
     */
    public static Mat4 perspectiveInfiniteReversedZ(double fovY, double aspect, double near) {
        double f = 1 / Maths.tan(0.5 * fovY);
        return new Mat4(
            f / aspect, 0, 0, 0,
            0, f, 0, 0,
            0, 0, 0, near,
            0, 0, -1, 0);
    }

    /** Orthographic projection with depth in [-1, 1] (OpenGL). */
    public static Mat4 orthographic(double left, double right, double bottom, double top, double near, double far) {
        return new Mat4(
            2 / (right - left), 0, 0, -(right + left) / (right - left),
            0, 2 / (top - bottom), 0, -(top + bottom) / (top - bottom),
            0, 0, -2 / (far - near), -(far + near) / (far - near),
            0, 0, 0, 1);
    }

    /** Orthographic projection with depth in [0, 1] (Vulkan, Direct3D, Metal). */
    public static Mat4 orthographicZeroToOne(double left, double right, double bottom, double top, double near, double far) {
        return new Mat4(
            2 / (right - left), 0, 0, -(right + left) / (right - left),
            0, 2 / (top - bottom), 0, -(top + bottom) / (top - bottom),
            0, 0, -1 / (far - near), -near / (far - near),
            0, 0, 0, 1);
    }

    // ---------------------------------------------------------------- operations

    /** {@code this * other}: applies {@code other} first, then this. */
    public Mat4 mul(Mat4 o) {
        return new Mat4(
            m00 * o.m00 + m01 * o.m10 + m02 * o.m20 + m03 * o.m30,
            m00 * o.m01 + m01 * o.m11 + m02 * o.m21 + m03 * o.m31,
            m00 * o.m02 + m01 * o.m12 + m02 * o.m22 + m03 * o.m32,
            m00 * o.m03 + m01 * o.m13 + m02 * o.m23 + m03 * o.m33,
            m10 * o.m00 + m11 * o.m10 + m12 * o.m20 + m13 * o.m30,
            m10 * o.m01 + m11 * o.m11 + m12 * o.m21 + m13 * o.m31,
            m10 * o.m02 + m11 * o.m12 + m12 * o.m22 + m13 * o.m32,
            m10 * o.m03 + m11 * o.m13 + m12 * o.m23 + m13 * o.m33,
            m20 * o.m00 + m21 * o.m10 + m22 * o.m20 + m23 * o.m30,
            m20 * o.m01 + m21 * o.m11 + m22 * o.m21 + m23 * o.m31,
            m20 * o.m02 + m21 * o.m12 + m22 * o.m22 + m23 * o.m32,
            m20 * o.m03 + m21 * o.m13 + m22 * o.m23 + m23 * o.m33,
            m30 * o.m00 + m31 * o.m10 + m32 * o.m20 + m33 * o.m30,
            m30 * o.m01 + m31 * o.m11 + m32 * o.m21 + m33 * o.m31,
            m30 * o.m02 + m31 * o.m12 + m32 * o.m22 + m33 * o.m32,
            m30 * o.m03 + m31 * o.m13 + m32 * o.m23 + m33 * o.m33);
    }
    /** {@code this * v}. */
    public Vec4 transform(Vec4 v) {
        return new Vec4(
            m00 * v.x() + m01 * v.y() + m02 * v.z() + m03 * v.w(),
            m10 * v.x() + m11 * v.y() + m12 * v.z() + m13 * v.w(),
            m20 * v.x() + m21 * v.y() + m22 * v.z() + m23 * v.w(),
            m30 * v.x() + m31 * v.y() + m32 * v.z() + m33 * v.w());
    }

    /** Transforms a point by an affine matrix (w = 1, last row ignored). */
    public Vec3 transformPoint(Vec3 p) {
        return new Vec3(
            m00 * p.x() + m01 * p.y() + m02 * p.z() + m03,
            m10 * p.x() + m11 * p.y() + m12 * p.z() + m13,
            m20 * p.x() + m21 * p.y() + m22 * p.z() + m23);
    }

    /** Transforms a direction (w = 0): translation does not apply. */
    public Vec3 transformDirection(Vec3 d) {
        return new Vec3(
            m00 * d.x() + m01 * d.y() + m02 * d.z(),
            m10 * d.x() + m11 * d.y() + m12 * d.z(),
            m20 * d.x() + m21 * d.y() + m22 * d.z());
    }

    /** Transforms a point by a projection matrix, including the perspective divide. */
    public Vec3 project(Vec3 p) {
        return transform(p.extend(1)).perspectiveDivide();
    }

    public Mat4 transpose() {
        return new Mat4(m00, m10, m20, m30, m01, m11, m21, m31, m02, m12, m22, m32, m03, m13, m23, m33);
    }

    public double determinant() {
        double s0 = m00 * m11 - m10 * m01;
        double s1 = m00 * m12 - m10 * m02;
        double s2 = m00 * m13 - m10 * m03;
        double s3 = m01 * m12 - m11 * m02;
        double s4 = m01 * m13 - m11 * m03;
        double s5 = m02 * m13 - m12 * m03;
        double c5 = m22 * m33 - m32 * m23;
        double c4 = m21 * m33 - m31 * m23;
        double c3 = m21 * m32 - m31 * m22;
        double c2 = m20 * m33 - m30 * m23;
        double c1 = m20 * m32 - m30 * m22;
        double c0 = m20 * m31 - m30 * m21;
        return s0 * c5 - s1 * c4 + s2 * c3 + s3 * c2 - s4 * c1 + s5 * c0;
    }

    /**
     * The inverse matrix.
     *
     * @throws ArithmeticException if the matrix is singular
     */
    public Mat4 inverse() {
        double s0 = m00 * m11 - m10 * m01;
        double s1 = m00 * m12 - m10 * m02;
        double s2 = m00 * m13 - m10 * m03;
        double s3 = m01 * m12 - m11 * m02;
        double s4 = m01 * m13 - m11 * m03;
        double s5 = m02 * m13 - m12 * m03;
        double c5 = m22 * m33 - m32 * m23;
        double c4 = m21 * m33 - m31 * m23;
        double c3 = m21 * m32 - m31 * m22;
        double c2 = m20 * m33 - m30 * m23;
        double c1 = m20 * m32 - m30 * m22;
        double c0 = m20 * m31 - m30 * m21;
        double det = s0 * c5 - s1 * c4 + s2 * c3 + s3 * c2 - s4 * c1 + s5 * c0;
        if (det == 0) {
            throw new ArithmeticException("Matrix is singular");
        }
        double id = 1 / det;
        return new Mat4(
            (m11 * c5 - m12 * c4 + m13 * c3) * id,
            (-m01 * c5 + m02 * c4 - m03 * c3) * id,
            (m31 * s5 - m32 * s4 + m33 * s3) * id,
            (-m21 * s5 + m22 * s4 - m23 * s3) * id,
            (-m10 * c5 + m12 * c2 - m13 * c1) * id,
            (m00 * c5 - m02 * c2 + m03 * c1) * id,
            (-m30 * s5 + m32 * s2 - m33 * s1) * id,
            (m20 * s5 - m22 * s2 + m23 * s1) * id,
            (m10 * c4 - m11 * c2 + m13 * c0) * id,
            (-m00 * c4 + m01 * c2 - m03 * c0) * id,
            (m30 * s4 - m31 * s2 + m33 * s0) * id,
            (-m20 * s4 + m21 * s2 - m23 * s0) * id,
            (-m10 * c3 + m11 * c1 - m12 * c0) * id,
            (m00 * c3 - m01 * c1 + m02 * c0) * id,
            (-m30 * s3 + m31 * s1 - m32 * s0) * id,
            (m20 * s3 - m21 * s1 + m22 * s0) * id);
    }

    public static Mat4 fromRows(Vec4 r0, Vec4 r1, Vec4 r2, Vec4 r3) {
        return new Mat4(
            r0.x(), r0.y(), r0.z(), r0.w(),
            r1.x(), r1.y(), r1.z(), r1.w(),
            r2.x(), r2.y(), r2.z(), r2.w(),
            r3.x(), r3.y(), r3.z(), r3.w());
    }

    /** Component at {@code row}, {@code column}. */
    public double get(int row, int column) {
        return switch (Objects.checkIndex(row, 4) * 4 + Objects.checkIndex(column, 4)) {
            case 0 -> m00;
            case 1 -> m01;
            case 2 -> m02;
            case 3 -> m03;
            case 4 -> m10;
            case 5 -> m11;
            case 6 -> m12;
            case 7 -> m13;
            case 8 -> m20;
            case 9 -> m21;
            case 10 -> m22;
            case 11 -> m23;
            case 12 -> m30;
            case 13 -> m31;
            case 14 -> m32;
            default -> m33;
        };
    }

    public Vec4 column(int index) {
        return new Vec4(get(0, index), get(1, index), get(2, index), get(3, index));
    }

    public Vec4 row(int index) {
        return new Vec4(get(index, 0), get(index, 1), get(index, 2), get(index, 3));
    }

    /** The upper-left 3x3 part: rotation and scale. */
    public Mat3 toMat3() {
        return new Mat3(m00, m01, m02, m10, m11, m12, m20, m21, m22);
    }

    /** Inverse transpose of the upper-left 3x3 part, for transforming normals. */
    public Mat3 normalMatrix() {
        return toMat3().inverse().transpose();
    }

    public Vec3 extractTranslation() {
        return new Vec3(m03, m13, m23);
    }

    public Vec3 extractScale() {
        return toMat3().extractScale();
    }

    public Quat extractRotation() {
        return toMat3().extractRotation();
    }

    /** Interpolates translation and scale linearly and rotation spherically (for translate-rotate-scale matrices). */
    @Override
    public Mat4 interpolate(Mat4 to, double t) {
        return trs(
            extractTranslation().interpolate(to.extractTranslation(), t),
            extractRotation().slerp(to.extractRotation(), t),
            extractScale().interpolate(to.extractScale(), t));
    }

    public boolean approxEquals(Mat4 o, double epsilon) {
        return column(0).approxEquals(o.column(0), epsilon)
            && column(1).approxEquals(o.column(1), epsilon)
            && column(2).approxEquals(o.column(2), epsilon)
            && column(3).approxEquals(o.column(3), epsilon);
    }

    // ---------------------------------------------------------------- memory

    /** Writes the sixteen components as doubles, column-major. */
    public void write(MemorySegment segment, long offset) {
        for (int i = 0; i < 16; i++) {
            segment.set(Memory.DOUBLE, offset + 8L * i, get(i % 4, i / 4));
        }
    }

    public static Mat4 read(MemorySegment s, long offset) {
        return new Mat4(
            s.get(Memory.DOUBLE, offset), s.get(Memory.DOUBLE, offset + 32), s.get(Memory.DOUBLE, offset + 64), s.get(Memory.DOUBLE, offset + 96),
            s.get(Memory.DOUBLE, offset + 8), s.get(Memory.DOUBLE, offset + 40), s.get(Memory.DOUBLE, offset + 72), s.get(Memory.DOUBLE, offset + 104),
            s.get(Memory.DOUBLE, offset + 16), s.get(Memory.DOUBLE, offset + 48), s.get(Memory.DOUBLE, offset + 80), s.get(Memory.DOUBLE, offset + 112),
            s.get(Memory.DOUBLE, offset + 24), s.get(Memory.DOUBLE, offset + 56), s.get(Memory.DOUBLE, offset + 88), s.get(Memory.DOUBLE, offset + 120));
    }

    /** Writes the sixteen components as floats, column-major (a GLSL {@code mat4}). */
    public void writeFloats(MemorySegment segment, long offset) {
        for (int i = 0; i < 16; i++) {
            segment.set(Memory.FLOAT, offset + 4L * i, (float) get(i % 4, i / 4));
        }
    }

    public static Mat4 readFloats(MemorySegment s, long offset) {
        return new Mat4(
            s.get(Memory.FLOAT, offset), s.get(Memory.FLOAT, offset + 16), s.get(Memory.FLOAT, offset + 32), s.get(Memory.FLOAT, offset + 48),
            s.get(Memory.FLOAT, offset + 4), s.get(Memory.FLOAT, offset + 20), s.get(Memory.FLOAT, offset + 36), s.get(Memory.FLOAT, offset + 52),
            s.get(Memory.FLOAT, offset + 8), s.get(Memory.FLOAT, offset + 24), s.get(Memory.FLOAT, offset + 40), s.get(Memory.FLOAT, offset + 56),
            s.get(Memory.FLOAT, offset + 12), s.get(Memory.FLOAT, offset + 28), s.get(Memory.FLOAT, offset + 44), s.get(Memory.FLOAT, offset + 60));
    }

    public void write(ByteBuffer buffer, int byteOffset) {
        for (int i = 0; i < 16; i++) {
            buffer.putDouble(byteOffset + 8 * i, get(i % 4, i / 4));
        }
    }

    public static Mat4 read(ByteBuffer b, int offset) {
        return new Mat4(
            b.getDouble(offset), b.getDouble(offset + 32), b.getDouble(offset + 64), b.getDouble(offset + 96),
            b.getDouble(offset + 8), b.getDouble(offset + 40), b.getDouble(offset + 72), b.getDouble(offset + 104),
            b.getDouble(offset + 16), b.getDouble(offset + 48), b.getDouble(offset + 80), b.getDouble(offset + 112),
            b.getDouble(offset + 24), b.getDouble(offset + 56), b.getDouble(offset + 88), b.getDouble(offset + 120));
    }

    public void write(DoubleBuffer buffer, int index) {
        for (int i = 0; i < 16; i++) {
            buffer.put(index + i, get(i % 4, i / 4));
        }
    }

    public static Mat4 read(DoubleBuffer b, int index) {
        return new Mat4(
            b.get(index), b.get(index + 4), b.get(index + 8), b.get(index + 12),
            b.get(index + 1), b.get(index + 5), b.get(index + 9), b.get(index + 13),
            b.get(index + 2), b.get(index + 6), b.get(index + 10), b.get(index + 14),
            b.get(index + 3), b.get(index + 7), b.get(index + 11), b.get(index + 15));
    }
}
