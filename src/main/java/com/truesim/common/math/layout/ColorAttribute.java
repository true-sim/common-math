package com.truesim.common.math.layout;

import com.truesim.common.math.color.Color;

/**
 * A {@link Color} (linear, with alpha) per element, e.g. a vertex color. Formats: {@code DOUBLE4}, {@code FLOAT4},
 * {@code HALF4}, {@code UNORM8_4} (linear bytes) and {@code SRGB8_4} (sRGB-encoded bytes, the usual 8-bit color).
 */
public final class ColorAttribute extends Attribute {

    private final boolean srgb;

    ColorAttribute(ElementBuffer buffer, Field<?> field) {
        super(buffer, field, accepts(field.format()));
        srgb = field.format() == Format.SRGB8_4;
    }

    static boolean accepts(Format format) {
        return switch (format) {
            case DOUBLE4, FLOAT4, HALF4, UNORM8_4, SRGB8_4 -> true;
            default -> false;
        };
    }

    public Color get(int index) {
        long address = address(index);
        return new Color(channel(address, 0), channel(address, 1), channel(address, 2), read(address, 3));
    }

    private double channel(long address, int c) {
        double value = read(address, c);
        return srgb ? Color.srgbToLinear(value) : value;
    }

    public void set(int index, Color value) {
        long address = address(index);
        setChannel(address, 0, value.r());
        setChannel(address, 1, value.g());
        setChannel(address, 2, value.b());
        write(address, 3, value.a());
    }

    private void setChannel(long address, int c, double value) {
        write(address, c, srgb ? Color.linearToSrgb(value) : value);
    }
}
