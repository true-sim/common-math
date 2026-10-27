package com.truesim.common.math.layout;

import com.truesim.common.math.Quat;

/**
 * A {@link Quat} per element, e.g. a particle's orientation. Formats: {@code DOUBLE4}, {@code FLOAT4}, {@code HALF4},
 * {@code SNORM16_4}. Values from the compact formats are renormalized when read.
 */
public final class QuatAttribute extends Attribute {

    private final boolean exact;

    QuatAttribute(ElementBuffer buffer, ElementLayout.Field field) {
        super(buffer, field, accepts(field.format()));
        exact = field.format() == Format.DOUBLE4;
    }

    private static boolean accepts(Format format) {
        return switch (format) {
            case DOUBLE4, FLOAT4, HALF4, SNORM16_4 -> true;
            default -> false;
        };
    }

    public Quat get(int index) {
        long address = address(index);
        double x = read(address, 0);
        double y = read(address, 1);
        double z = read(address, 2);
        double w = read(address, 3);
        double scale = exact ? 1 : 1 / Math.sqrt(x * x + y * y + z * z + w * w);
        return new Quat(x * scale, y * scale, z * scale, w * scale);
    }

    public void set(int index, Quat value) {
        long address = address(index);
        write(address, 0, value.x());
        write(address, 1, value.y());
        write(address, 2, value.z());
        write(address, 3, value.w());
    }
}
