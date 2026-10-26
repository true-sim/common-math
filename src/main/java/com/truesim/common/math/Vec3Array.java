package com.truesim.common.math;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Fixed-size array of {@link Vec3} values stored back to back in memory, three doubles each (see {@link Vec3#LAYOUT}).
 * Reading and writing values allocates nothing, so use it for state that changes every step.
 */
public final class Vec3Array extends StructArray {

    private Vec3Array(MemorySegment segment) {
        super(segment, Vec3.LAYOUT);
    }

    /** Off-heap array of {@code size} zero values, freed when {@code arena} is closed. */
    public static Vec3Array allocate(Arena arena, int size) {
        return new Vec3Array(StructArray.allocate(arena, Vec3.LAYOUT, size));
    }

    /** Heap array of {@code size} zero values, backed by a {@code double[]}. */
    public static Vec3Array onHeap(int size) {
        return new Vec3Array(StructArray.heap(Vec3.LAYOUT, size));
    }

    /** Views existing memory, e.g. from native code, as values. Its size must be a multiple of {@link Vec3#LAYOUT}. */
    public static Vec3Array wrap(MemorySegment segment) {
        return new Vec3Array(segment);
    }

    public Vec3 get(int index) {
        return Vec3.read(segment(), offset(index));
    }

    public void set(int index, Vec3 value) {
        value.write(segment(), offset(index));
    }

    public void fill(Vec3 value) {
        for (int i = 0; i < size(); i++) {
            set(i, value);
        }
    }

    /** Writes all values as floats ({@link Vec3#FLOAT_LAYOUT}), back to back from {@code offset} bytes into {@code target}. */
    public void writeFloats(MemorySegment target, long offset) {
        long floatStride = Vec3.FLOAT_LAYOUT.byteSize();
        for (int i = 0; i < size(); i++) {
            get(i).writeFloats(target, offset + i * floatStride);
        }
    }
}
