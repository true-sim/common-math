package com.truesim.common.math.layout;

import com.truesim.common.math.Vec4;

/**
 * A {@link Vec4} per element, e.g. a tangent with handedness or skinning weights. Formats: {@code DOUBLE4},
 * {@code FLOAT4}, {@code HALF4}, {@code UNORM8_4}, {@code SNORM8_4}, {@code UNORM16_4}, {@code SNORM16_4}.
 */
public final class Vec4Attribute extends Attribute {

    Vec4Attribute(ElementBuffer buffer, ElementLayout.Field field) {
        super(buffer, field, accepts(field.format()));
    }

    private static boolean accepts(Format format) {
        return switch (format) {
            case DOUBLE4, FLOAT4, HALF4, UNORM8_4, SNORM8_4, UNORM16_4, SNORM16_4 -> true;
            default -> false;
        };
    }

    public Vec4 get(int index) {
        long address = address(index);
        return new Vec4(read(address, 0), read(address, 1), read(address, 2), read(address, 3));
    }

    public double x(int index) {
        return component(index, 0);
    }

    public double y(int index) {
        return component(index, 1);
    }

    public double z(int index) {
        return component(index, 2);
    }

    public double w(int index) {
        return component(index, 3);
    }

    public void set(int index, Vec4 value) {
        set(index, value.x(), value.y(), value.z(), value.w());
    }

    public void set(int index, double x, double y, double z, double w) {
        long address = address(index);
        write(address, 0, x);
        write(address, 1, y);
        write(address, 2, z);
        write(address, 3, w);
    }
}
