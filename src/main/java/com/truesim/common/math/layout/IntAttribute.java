package com.truesim.common.math.layout;

/** One 32-bit integer per element, e.g. an id or flags. Format: {@code INT1}. */
public final class IntAttribute extends Attribute {

    IntAttribute(ElementBuffer buffer, Field<?> field) {
        super(buffer, field, accepts(field.format()));
    }

    static boolean accepts(Format format) {
        return format == Format.INT1;
    }

    public int get(int index) {
        return (int) component(index, 0);
    }

    public void set(int index, int value) {
        setComponent(index, 0, value);
    }
}
