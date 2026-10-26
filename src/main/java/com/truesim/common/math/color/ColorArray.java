package com.truesim.common.math.color;

import com.truesim.common.math.StructArray;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Fixed-size array of {@link Color} values stored back to back in memory, four doubles each (see
 * {@link Color#LAYOUT}). Reading and writing values allocates nothing.
 */
public final class ColorArray extends StructArray {

    private ColorArray(MemorySegment segment) {
        super(segment, Color.LAYOUT);
    }

    /** Off-heap array of {@code size} transparent colors, freed when {@code arena} is closed. */
    public static ColorArray allocate(Arena arena, int size) {
        return new ColorArray(StructArray.allocate(arena, Color.LAYOUT, size));
    }

    /** Heap array of {@code size} transparent colors, backed by a {@code double[]}. */
    public static ColorArray onHeap(int size) {
        return new ColorArray(StructArray.heap(Color.LAYOUT, size));
    }

    /** Views existing memory as colors. Its size must be a multiple of {@link Color#LAYOUT}. */
    public static ColorArray wrap(MemorySegment segment) {
        return new ColorArray(segment);
    }

    public Color get(int index) {
        return Color.read(segment(), offset(index));
    }

    public void set(int index, Color value) {
        value.write(segment(), offset(index));
    }

    public void fill(Color value) {
        for (int i = 0; i < size(); i++) {
            set(i, value);
        }
    }

    /** Writes all colors as linear floats ({@link Color#FLOAT_LAYOUT}), back to back from {@code offset} bytes. */
    public void writeFloats(MemorySegment target, long offset) {
        long floatStride = Color.FLOAT_LAYOUT.byteSize();
        for (int i = 0; i < size(); i++) {
            get(i).writeFloats(target, offset + i * floatStride);
        }
    }

    /** Writes all colors as {@code RGBA8} texels ({@link Color#RGBA8_LAYOUT}), back to back from {@code offset} bytes. */
    public void writeRgba8(MemorySegment target, long offset) {
        long texelStride = Color.RGBA8_LAYOUT.byteSize();
        for (int i = 0; i < size(); i++) {
            get(i).writeRgba8(target, offset + i * texelStride);
        }
    }
}
