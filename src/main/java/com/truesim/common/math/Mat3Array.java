package com.truesim.common.math;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Fixed-size array of {@link Mat3} values stored back to back in memory, nine doubles each (see {@link Mat3#LAYOUT}).
 * Reading and writing values allocates nothing, so use it for state that changes every step.
 */
public final class Mat3Array extends StructArray {

    private Mat3Array(MemorySegment segment) {
        super(segment, Mat3.LAYOUT);
    }

    /** Off-heap array of {@code size} zero values, freed when {@code arena} is closed. */
    public static Mat3Array allocate(Arena arena, int size) {
        return new Mat3Array(StructArray.allocate(arena, Mat3.LAYOUT, size));
    }

    /** Heap array of {@code size} zero values, backed by a {@code double[]}. */
    public static Mat3Array onHeap(int size) {
        return new Mat3Array(StructArray.heap(Mat3.LAYOUT, size));
    }

    /** Views existing memory, e.g. from native code, as values. Its size must be a multiple of {@link Mat3#LAYOUT}. */
    public static Mat3Array wrap(MemorySegment segment) {
        return new Mat3Array(segment);
    }

    public Mat3 get(int index) {
        return Mat3.read(segment(), offset(index));
    }

    public void set(int index, Mat3 value) {
        value.write(segment(), offset(index));
    }

    public void fill(Mat3 value) {
        for (int i = 0; i < size(); i++) {
            set(i, value);
        }
    }

    /** Writes all values as floats ({@link Mat3#FLOAT_LAYOUT}), back to back from {@code offset} bytes into {@code target}. */
    public void writeFloats(MemorySegment target, long offset) {
        long floatStride = Mat3.FLOAT_LAYOUT.byteSize();
        for (int i = 0; i < size(); i++) {
            get(i).writeFloats(target, offset + i * floatStride);
        }
    }
}
