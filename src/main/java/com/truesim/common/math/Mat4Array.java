package com.truesim.common.math;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * Fixed-size array of {@link Mat4} values stored back to back in memory, sixteen doubles each, column-major (see
 * {@link Mat4#LAYOUT}).
 *
 * <p>{@link #set} is allocation-free. {@link #get} usually allocates: reading sixteen values from memory exceeds what
 * the JIT can optimize away in one go. In hot code, use the operations that work directly in memory instead, such as
 * {@link #setProduct} and {@link #transformPoints}; they never create a {@link Mat4}.
 *
 * <pre>{@code
 * // world[i] = world[parent[i]] * local[i]
 * world.setProduct(i, world, parent[i], local, i);
 * }</pre>
 */
public final class Mat4Array extends StructArray {

    private Mat4Array(MemorySegment segment) {
        super(segment, Mat4.LAYOUT);
    }

    /** Off-heap array of {@code size} zero matrices, freed when {@code arena} is closed. */
    public static Mat4Array allocate(Arena arena, int size) {
        return new Mat4Array(StructArray.allocate(arena, Mat4.LAYOUT, size));
    }

    /** Heap array of {@code size} zero matrices, backed by a {@code double[]}. */
    public static Mat4Array onHeap(int size) {
        return new Mat4Array(StructArray.heap(Mat4.LAYOUT, size));
    }

    /** Views existing memory, e.g. from native code, as matrices. Its size must be a multiple of {@link Mat4#LAYOUT}. */
    public static Mat4Array wrap(MemorySegment segment) {
        return new Mat4Array(segment);
    }

    /** The matrix at {@code index}. Allocates in hot code; prefer the in-memory operations there. */
    public Mat4 get(int index) {
        return Mat4.read(segment(), offset(index));
    }

    public void set(int index, Mat4 value) {
        value.write(segment(), offset(index));
    }

    public void fill(Mat4 value) {
        for (int i = 0; i < size(); i++) {
            set(i, value);
        }
    }

    /** Component ({@code row}, {@code column}) of the matrix at {@code index}. */
    public double get(int index, int row, int column) {
        return element(segment(), offset(index), row, column);
    }

    // ---------------------------------------------------------------- in-memory operations

    /**
     * Sets matrix {@code index} to {@code left[leftIndex] * right[rightIndex]}. Any of the three may be the same
     * matrix.
     */
    public void setProduct(int index, Mat4Array left, int leftIndex, Mat4Array right, int rightIndex) {
        product(left.segment(), left.offset(leftIndex), right.segment(), right.offset(rightIndex), offset(index));
    }

    private void product(MemorySegment a, long ao, MemorySegment b, long bo, long target) {
        // Read everything before writing, so the target may alias either input.
        double m00 = dot(a, ao, b, bo, 0, 0);
        double m01 = dot(a, ao, b, bo, 0, 1);
        double m02 = dot(a, ao, b, bo, 0, 2);
        double m03 = dot(a, ao, b, bo, 0, 3);
        double m10 = dot(a, ao, b, bo, 1, 0);
        double m11 = dot(a, ao, b, bo, 1, 1);
        double m12 = dot(a, ao, b, bo, 1, 2);
        double m13 = dot(a, ao, b, bo, 1, 3);
        double m20 = dot(a, ao, b, bo, 2, 0);
        double m21 = dot(a, ao, b, bo, 2, 1);
        double m22 = dot(a, ao, b, bo, 2, 2);
        double m23 = dot(a, ao, b, bo, 2, 3);
        double m30 = dot(a, ao, b, bo, 3, 0);
        double m31 = dot(a, ao, b, bo, 3, 1);
        double m32 = dot(a, ao, b, bo, 3, 2);
        double m33 = dot(a, ao, b, bo, 3, 3);
        MemorySegment s = segment();
        put(s, target, 0, m00, m10, m20, m30);
        put(s, target, 1, m01, m11, m21, m31);
        put(s, target, 2, m02, m12, m22, m32);
        put(s, target, 3, m03, m13, m23, m33);
    }

    /**
     * Transforms {@code count} points of {@code points}, starting at {@code from}, in place by the affine matrix at
     * {@code index} (w = 1).
     */
    public void transformPoints(int index, Vec3Array points, int from, int count) {
        transform(index, points, from, count, 1);
    }

    /**
     * Transforms {@code count} directions of {@code directions}, starting at {@code from}, in place by the matrix at
     * {@code index} (w = 0): translation does not apply.
     */
    public void transformDirections(int index, Vec3Array directions, int from, int count) {
        transform(index, directions, from, count, 0);
    }

    private void transform(int index, Vec3Array vectors, int from, int count, double w) {
        Objects.checkFromIndexSize(from, count, vectors.size());
        MemorySegment s = segment();
        long o = offset(index);
        double m00 = element(s, o, 0, 0);
        double m01 = element(s, o, 0, 1);
        double m02 = element(s, o, 0, 2);
        double m03 = element(s, o, 0, 3) * w;
        double m10 = element(s, o, 1, 0);
        double m11 = element(s, o, 1, 1);
        double m12 = element(s, o, 1, 2);
        double m13 = element(s, o, 1, 3) * w;
        double m20 = element(s, o, 2, 0);
        double m21 = element(s, o, 2, 1);
        double m22 = element(s, o, 2, 2);
        double m23 = element(s, o, 2, 3) * w;
        MemorySegment v = vectors.segment();
        for (int i = from; i < from + count; i++) {
            long p = vectors.offset(i);
            double x = v.get(Memory.DOUBLE, p);
            double y = v.get(Memory.DOUBLE, p + 8);
            double z = v.get(Memory.DOUBLE, p + 16);
            v.set(Memory.DOUBLE, p, m00 * x + m01 * y + m02 * z + m03);
            v.set(Memory.DOUBLE, p + 8, m10 * x + m11 * y + m12 * z + m13);
            v.set(Memory.DOUBLE, p + 16, m20 * x + m21 * y + m22 * z + m23);
        }
    }

    /** Writes all matrices as floats ({@link Mat4#FLOAT_LAYOUT}), back to back from {@code offset} bytes into {@code target}. */
    public void writeFloats(MemorySegment target, long offset) {
        MemorySegment s = segment();
        long count = 16L * size();
        for (long i = 0; i < count; i++) {
            target.set(Memory.FLOAT, offset + 4 * i, (float) s.get(Memory.DOUBLE, 8 * i));
        }
    }

    // ---------------------------------------------------------------- column-major element access

    private static double element(MemorySegment s, long offset, int row, int column) {
        return s.get(Memory.DOUBLE, offset + 8L * (column * 4 + row));
    }

    private static double dot(MemorySegment a, long ao, MemorySegment b, long bo, int row, int column) {
        return element(a, ao, row, 0) * element(b, bo, 0, column)
            + element(a, ao, row, 1) * element(b, bo, 1, column)
            + element(a, ao, row, 2) * element(b, bo, 2, column)
            + element(a, ao, row, 3) * element(b, bo, 3, column);
    }


    private static void put(MemorySegment s, long offset, int column, double r0, double r1, double r2, double r3) {
        long base = offset + 32L * column;
        s.set(Memory.DOUBLE, base, r0);
        s.set(Memory.DOUBLE, base + 8, r1);
        s.set(Memory.DOUBLE, base + 16, r2);
        s.set(Memory.DOUBLE, base + 24, r3);
    }
}
