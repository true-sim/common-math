package com.truesim.common.math;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Fixed-size array of {@link Vec4} values stored back to back in memory, four doubles each (see {@link Vec4#LAYOUT}).
 * Reading and writing values allocates nothing, so use it for state that changes every step.
 */
public final class Vec4Array extends StructArray {

    private Vec4Array(MemorySegment segment) {
        super(segment, Vec4.LAYOUT);
    }

    /** Off-heap array of {@code size} zero values, freed when {@code arena} is closed. */
    public static Vec4Array allocate(Arena arena, int size) {
        return new Vec4Array(StructArray.allocate(arena, Vec4.LAYOUT, size));
    }

    /** Heap array of {@code size} zero values, backed by a {@code double[]}. */
    public static Vec4Array onHeap(int size) {
        return new Vec4Array(StructArray.heap(Vec4.LAYOUT, size));
    }

    /** Views existing memory, e.g. from native code, as values. Its size must be a multiple of {@link Vec4#LAYOUT}. */
    public static Vec4Array wrap(MemorySegment segment) {
        return new Vec4Array(segment);
    }

    public Vec4 get(int index) {
        return Vec4.read(segment(), offset(index));
    }

    public void set(int index, Vec4 value) {
        value.write(segment(), offset(index));
    }

    public void fill(Vec4 value) {
        for (int i = 0; i < size(); i++) {
            set(i, value);
        }
    }

    /** Writes all values as floats ({@link Vec4#FLOAT_LAYOUT}), back to back from {@code offset} bytes into {@code target}. */
    public void writeFloats(MemorySegment target, long offset) {
        long floatStride = Vec4.FLOAT_LAYOUT.byteSize();
        for (int i = 0; i < size(); i++) {
            get(i).writeFloats(target, offset + i * floatStride);
        }
    }
}
