package com.truesim.common.math.layout;

import com.truesim.common.math.Vec2;

/**
 * A {@link Vec2} per element, e.g. texture coordinates. Formats: {@code DOUBLE2}, {@code FLOAT2}, {@code HALF2},
 * {@code UNORM16_2}, {@code SNORM16_2}.
 */
public final class Vec2Attribute extends Attribute {

    Vec2Attribute(ElementBuffer buffer, ElementLayout.Field field) {
        super(buffer, field, accepts(field.format()));
    }

    private static boolean accepts(Format format) {
        return switch (format) {
            case DOUBLE2, FLOAT2, HALF2, UNORM16_2, SNORM16_2 -> true;
            default -> false;
        };
    }

    public Vec2 get(int index) {
        long address = address(index);
        return new Vec2(read(address, 0), read(address, 1));
    }

    public double x(int index) {
        return component(index, 0);
    }

    public double y(int index) {
        return component(index, 1);
    }

    public void set(int index, Vec2 value) {
        set(index, value.x(), value.y());
    }

    public void set(int index, double x, double y) {
        long address = address(index);
        write(address, 0, x);
        write(address, 1, y);
    }
}
