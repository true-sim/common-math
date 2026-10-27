package com.truesim.common.math.geometry;

import com.truesim.common.math.StructArray;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * Fixed-size array of {@link Aabb} values stored back to back in memory, six doubles each (see {@link Aabb#LAYOUT}),
 * e.g. the bounds of all objects for a {@link com.truesim.common.math.spatial.Bvh}. The component accessors read and
 * write single doubles, for loops that never create objects.
 */
public final class AabbArray extends StructArray {

    private static final ValueLayout.OfDouble DOUBLE = ValueLayout.JAVA_DOUBLE_UNALIGNED;

    private AabbArray(MemorySegment segment) {
        super(segment, Aabb.LAYOUT);
    }

    /** Off-heap array of {@code size} empty boxes, freed when {@code arena} is closed. */
    public static AabbArray allocate(Arena arena, int size) {
        AabbArray array = new AabbArray(StructArray.allocate(arena, Aabb.LAYOUT, size));
        array.fill(Aabb.EMPTY);
        return array;
    }

    /** Heap array of {@code size} empty boxes, backed by a {@code double[]}. */
    public static AabbArray onHeap(int size) {
        AabbArray array = new AabbArray(StructArray.heap(Aabb.LAYOUT, size));
        array.fill(Aabb.EMPTY);
        return array;
    }

    /** Views existing memory as boxes. Its size must be a multiple of {@link Aabb#LAYOUT}. */
    public static AabbArray wrap(MemorySegment segment) {
        return new AabbArray(segment);
    }

    public Aabb get(int index) {
        return Aabb.read(segment(), offset(index));
    }

    public void set(int index, Aabb value) {
        value.write(segment(), offset(index));
    }

    /** Sets box {@code index} from plain doubles. */
    public void set(int index, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        MemorySegment s = segment();
        long o = offset(index);
        s.set(DOUBLE, o, minX);
        s.set(DOUBLE, o + 8, minY);
        s.set(DOUBLE, o + 16, minZ);
        s.set(DOUBLE, o + 24, maxX);
        s.set(DOUBLE, o + 32, maxY);
        s.set(DOUBLE, o + 40, maxZ);
    }

    public void fill(Aabb value) {
        for (int i = 0; i < size(); i++) {
            set(i, value);
        }
    }

    /**
     * Component {@code component} of box {@code index}: 0, 1, 2 are minX, minY, minZ and 3, 4, 5 are maxX, maxY, maxZ.
     */
    public double get(int index, int component) {
        return segment().get(DOUBLE, offset(index) + 8L * Objects.checkIndex(component, 6));
    }
}
