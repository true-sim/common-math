package com.truesim.common.math;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Fixed-size array of {@link Quat} values stored back to back in memory, four doubles each (see {@link Quat#LAYOUT}).
 * Reading and writing values allocates nothing, so use it for state that changes every step.
 */
public final class QuatArray extends StructArray {

    private QuatArray(MemorySegment segment) {
        super(segment, Quat.LAYOUT);
    }

    /** Off-heap array of {@code size} zero values, freed when {@code arena} is closed. */
    public static QuatArray allocate(Arena arena, int size) {
        return new QuatArray(StructArray.allocate(arena, Quat.LAYOUT, size));
    }

    /** Heap array of {@code size} zero values, backed by a {@code double[]}. */
    public static QuatArray onHeap(int size) {
        return new QuatArray(StructArray.heap(Quat.LAYOUT, size));
    }

    /** Views existing memory, e.g. from native code, as values. Its size must be a multiple of {@link Quat#LAYOUT}. */
    public static QuatArray wrap(MemorySegment segment) {
        return new QuatArray(segment);
    }

    public Quat get(int index) {
        return Quat.read(segment(), offset(index));
    }

    public void set(int index, Quat value) {
        value.write(segment(), offset(index));
    }

    public void fill(Quat value) {
        for (int i = 0; i < size(); i++) {
            set(i, value);
        }
    }

    /** Writes all values as floats ({@link Quat#FLOAT_LAYOUT}), back to back from {@code offset} bytes into {@code target}. */
    public void writeFloats(MemorySegment target, long offset) {
        long floatStride = Quat.FLOAT_LAYOUT.byteSize();
        for (int i = 0; i < size(); i++) {
            get(i).writeFloats(target, offset + i * floatStride);
        }
    }
}
