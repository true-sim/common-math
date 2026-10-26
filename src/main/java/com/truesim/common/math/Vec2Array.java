package com.truesim.common.math;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Fixed-size array of {@link Vec2} values stored back to back in memory, two doubles each (see {@link Vec2#LAYOUT}).
 * Reading and writing values allocates nothing, so use it for state that changes every step.
 */
public final class Vec2Array extends StructArray {

    private Vec2Array(MemorySegment segment) {
        super(segment, Vec2.LAYOUT);
    }

    /** Off-heap array of {@code size} zero values, freed when {@code arena} is closed. */
    public static Vec2Array allocate(Arena arena, int size) {
        return new Vec2Array(StructArray.allocate(arena, Vec2.LAYOUT, size));
    }

    /** Heap array of {@code size} zero values, backed by a {@code double[]}. */
    public static Vec2Array onHeap(int size) {
        return new Vec2Array(StructArray.heap(Vec2.LAYOUT, size));
    }

    /** Views existing memory, e.g. from native code, as values. Its size must be a multiple of {@link Vec2#LAYOUT}. */
    public static Vec2Array wrap(MemorySegment segment) {
        return new Vec2Array(segment);
    }

    public Vec2 get(int index) {
        return Vec2.read(segment(), offset(index));
    }

    public void set(int index, Vec2 value) {
        value.write(segment(), offset(index));
    }

    public void fill(Vec2 value) {
        for (int i = 0; i < size(); i++) {
            set(i, value);
        }
    }

    /** Writes all values as floats ({@link Vec2#FLOAT_LAYOUT}), back to back from {@code offset} bytes into {@code target}. */
    public void writeFloats(MemorySegment target, long offset) {
        long floatStride = Vec2.FLOAT_LAYOUT.byteSize();
        for (int i = 0; i < size(); i++) {
            get(i).writeFloats(target, offset + i * floatStride);
        }
    }
}
