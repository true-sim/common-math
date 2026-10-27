package com.truesim.common.math.layout;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * How one attribute of an element is stored: the component type and the number of components. Conversions are exact
 * and deterministic: floats round to nearest, normalized integers clamp and round to nearest.
 *
 * <ul>
 *   <li>{@code DOUBLE}, {@code FLOAT}, {@code HALF}: 64, 32 and 16-bit floats.
 *   <li>{@code UNORM8}, {@code UNORM16}: unsigned integers for [0, 1]; {@code SNORM8}, {@code SNORM16}: signed
 *       integers for [-1, 1] (-128 and -32768 read as -1, as on GPUs).
 *   <li>{@link #SRGB8_4}: a color as four bytes, red, green and blue sRGB-encoded, alpha linear.
 *   <li>{@link #OCT16}: a unit vector octahedrally encoded in two {@code SNORM16}s: 4 bytes for a normal.
 *   <li>{@link #INT1}: a 32-bit integer, e.g. an id or flags.
 * </ul>
 */
public enum Format {
    DOUBLE1(Type.DOUBLE, 1), DOUBLE2(Type.DOUBLE, 2), DOUBLE3(Type.DOUBLE, 3), DOUBLE4(Type.DOUBLE, 4),
    FLOAT1(Type.FLOAT, 1), FLOAT2(Type.FLOAT, 2), FLOAT3(Type.FLOAT, 3), FLOAT4(Type.FLOAT, 4),
    HALF2(Type.HALF, 2), HALF4(Type.HALF, 4),
    UNORM8_4(Type.UNORM8, 4), SNORM8_4(Type.SNORM8, 4),
    UNORM16_2(Type.UNORM16, 2), UNORM16_4(Type.UNORM16, 4), SNORM16_2(Type.SNORM16, 2), SNORM16_4(Type.SNORM16, 4),
    SRGB8_4(Type.UNORM8, 4),
    OCT16(Type.SNORM16, 2),
    INT1(Type.INT, 1);

    /** Storage of one component. */
    enum Type {
        DOUBLE(ValueLayout.JAVA_DOUBLE), FLOAT(ValueLayout.JAVA_FLOAT), HALF(ValueLayout.JAVA_SHORT),
        UNORM8(ValueLayout.JAVA_BYTE), SNORM8(ValueLayout.JAVA_BYTE), UNORM16(ValueLayout.JAVA_SHORT),
        SNORM16(ValueLayout.JAVA_SHORT), INT(ValueLayout.JAVA_INT);

        final ValueLayout layout;
        final int bytes;

        Type(ValueLayout layout) {
            this.layout = layout;
            this.bytes = (int) layout.byteSize();
        }
    }

    private static final ValueLayout.OfDouble DOUBLE_LAYOUT = ValueLayout.JAVA_DOUBLE_UNALIGNED;
    private static final ValueLayout.OfFloat FLOAT_LAYOUT = ValueLayout.JAVA_FLOAT_UNALIGNED;
    private static final ValueLayout.OfShort SHORT_LAYOUT = ValueLayout.JAVA_SHORT_UNALIGNED;
    private static final ValueLayout.OfInt INT_LAYOUT = ValueLayout.JAVA_INT_UNALIGNED;

    final Type type;
    final int components;

    Format(Type type, int components) {
        this.type = type;
        this.components = components;
    }

    /** Number of components. */
    public int components() {
        return components;
    }

    /** Bytes per attribute. */
    public int byteSize() {
        return type.bytes * components;
    }

    /** Natural alignment: the size of one component. */
    public int alignment() {
        return type.bytes;
    }

    /** Reads the component at {@code offset} as a double. */
    double read(MemorySegment segment, long offset) {
        return switch (type) {
            case DOUBLE -> segment.get(DOUBLE_LAYOUT, offset);
            case FLOAT -> segment.get(FLOAT_LAYOUT, offset);
            case HALF -> Float.float16ToFloat(segment.get(SHORT_LAYOUT, offset));
            case UNORM8 -> (segment.get(ValueLayout.JAVA_BYTE, offset) & 0xff) / 255.0;
            case SNORM8 -> Math.max(segment.get(ValueLayout.JAVA_BYTE, offset) / 127.0, -1);
            case UNORM16 -> (segment.get(SHORT_LAYOUT, offset) & 0xffff) / 65535.0;
            case SNORM16 -> Math.max(segment.get(SHORT_LAYOUT, offset) / 32767.0, -1);
            default -> segment.get(INT_LAYOUT, offset);
        };
    }

    /** Writes {@code value} as the component at {@code offset}. */
    void write(MemorySegment segment, long offset, double value) {
        switch (type) {
            case DOUBLE -> segment.set(DOUBLE_LAYOUT, offset, value);
            case FLOAT -> segment.set(FLOAT_LAYOUT, offset, (float) value);
            case HALF -> segment.set(SHORT_LAYOUT, offset, Float.floatToFloat16((float) value));
            case UNORM8 -> segment.set(ValueLayout.JAVA_BYTE, offset, (byte) Math.round(clamp(value, 0) * 255));
            case SNORM8 -> segment.set(ValueLayout.JAVA_BYTE, offset, (byte) Math.round(clamp(value, -1) * 127));
            case UNORM16 -> segment.set(SHORT_LAYOUT, offset, (short) Math.round(clamp(value, 0) * 65535));
            case SNORM16 -> segment.set(SHORT_LAYOUT, offset, (short) Math.round(clamp(value, -1) * 32767));
            default -> segment.set(INT_LAYOUT, offset, (int) value);
        }
    }

    /** Clamps to [min, 1]; NaN becomes 0. */
    private static double clamp(double value, double min) {
        return value >= min ? Math.min(value, 1) : value < min ? min : 0;
    }
}
