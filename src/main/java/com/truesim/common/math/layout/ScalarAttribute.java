package com.truesim.common.math.layout;

/** One number per element, e.g. a particle's age or size. Formats: {@code DOUBLE1}, {@code FLOAT1}. */
public final class ScalarAttribute extends Attribute {

    ScalarAttribute(ElementBuffer buffer, ElementLayout.Field field) {
        super(buffer, field, field.format() == Format.DOUBLE1 || field.format() == Format.FLOAT1);
    }

    public double get(int index) {
        return component(index, 0);
    }

    public void set(int index, double value) {
        setComponent(index, 0, value);
    }
}
