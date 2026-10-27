package com.truesim.common.math.layout;

import com.truesim.common.math.JitInlining;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * A typed view of one attribute across all elements of an {@link ElementBuffer}. Subclasses read and write values of
 * one type; each accepts the formats that can hold it.
 */
public abstract sealed class Attribute
    permits ScalarAttribute, IntAttribute, Vec2Attribute, Vec3Attribute, Vec4Attribute, QuatAttribute, ColorAttribute {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    final MemorySegment segment;
    final Format format;
    private final String name;
    private final long offset;
    private final long stride;
    private final int size;
    private final long componentBytes;

    Attribute(ElementBuffer buffer, Field<?> field, boolean accepted) {
        if (!accepted) {
            throw new IllegalArgumentException(
                getClass().getSimpleName() + " cannot hold " + field.name() + " in format " + field.format());
        }
        this.segment = buffer.segment();
        this.format = field.format();
        this.name = field.name();
        this.offset = field.offset();
        this.stride = buffer.layout().stride();
        this.size = buffer.size();
        this.componentBytes = format.type.bytes;
    }

    public String name() {
        return name;
    }

    public Format format() {
        return format;
    }

    /** Number of elements. */
    public int size() {
        return size;
    }

    /** Byte offset of this attribute in element {@code index}. */
    final long address(int index) {
        return Objects.checkIndex(index, size) * stride + offset;
    }

    /** Component {@code c} of element {@code index}, decoded. */
    final double component(int index, int c) {
        return read(address(index), c);
    }

    final void setComponent(int index, int c, double value) {
        write(address(index), c, value);
    }

    /** Component {@code c} of the attribute at {@code address} (from {@link #address}), decoded. */
    final double read(long address, int c) {
        return format.read(segment, address + c * componentBytes);
    }

    final void write(long address, int c, double value) {
        format.write(segment, address + c * componentBytes, value);
    }
}
