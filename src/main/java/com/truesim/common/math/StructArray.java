package com.truesim.common.math;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * Base class of the array types: a fixed number of values stored back to back in a {@link MemorySegment}, either on
 * the heap or off-heap. Use them for state that changes every step: reading and writing values allocates nothing.
 */
public abstract class StructArray {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    private final MemorySegment segment;
    private final int size;
    private final long stride;

    protected StructArray(MemorySegment segment, MemoryLayout layout) {
        this.stride = layout.byteSize();
        if (segment.byteSize() % stride != 0) {
            throw new IllegalArgumentException("Segment size " + segment.byteSize() + " is not a multiple of " + stride);
        }
        this.segment = segment;
        this.size = Math.toIntExact(segment.byteSize() / stride);
    }

    /** Off-heap memory for {@code count} values, owned by {@code arena}. */
    protected static MemorySegment allocate(Arena arena, MemoryLayout layout, int count) {
        return arena.allocate(layout, count);
    }

    /** Heap memory for {@code count} values, backed by a {@code double[]}. */
    protected static MemorySegment heap(MemoryLayout layout, int count) {
        return MemorySegment.ofArray(new double[Math.multiplyExact(count, (int) (layout.byteSize() / Double.BYTES))]);
    }

    /** Number of values. */
    public final int size() {
        return size;
    }

    /** The underlying memory, e.g. to pass to native code or upload to the GPU. */
    public final MemorySegment segment() {
        return segment;
    }

    /** Bytes per value. */
    public final long stride() {
        return stride;
    }

    /** Byte offset of the value at {@code index}. */
    protected final long offset(int index) {
        return Objects.checkIndex(index, size) * stride;
    }
}
