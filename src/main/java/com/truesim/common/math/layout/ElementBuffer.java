package com.truesim.common.math.layout;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * A run of elements in memory with an {@link ElementLayout}: off-heap from an {@link Arena}, on the heap, or any
 * existing {@link MemorySegment} such as a mapped file or a buffer shared with native code or the GPU. Typed views
 * read and write one attribute across all elements:
 *
 * <pre>{@code
 * ElementBuffer vertices = ElementBuffer.allocate(arena, vertex, count);
 * Vec3Attribute position = vertices.vec3("position");
 * Vec3Attribute normal = vertices.vec3("normal");
 * position.set(i, p);
 * position.transformPoints(model);
 * }</pre>
 *
 * <p>Views are cheap to keep: create them once and reuse them. Reading and writing values allocates nothing. For a
 * structure-of-arrays layout (common for particles), use one buffer per attribute with {@link ElementLayout#of}: the
 * views work the same.
 */
public final class ElementBuffer {

    private final MemorySegment segment;
    private final ElementLayout layout;
    private final int size;

    private ElementBuffer(MemorySegment segment, ElementLayout layout) {
        if (segment.byteSize() % layout.stride() != 0) {
            throw new IllegalArgumentException(
                "Segment size " + segment.byteSize() + " is not a multiple of the stride " + layout.stride());
        }
        this.segment = segment;
        this.layout = layout;
        this.size = Math.toIntExact(segment.byteSize() / layout.stride());
    }

    /** Off-heap memory for {@code count} elements, owned by {@code arena}, zeroed. */
    public static ElementBuffer allocate(Arena arena, ElementLayout layout, int count) {
        return new ElementBuffer(arena.allocate(Math.multiplyExact(layout.stride(), count), layout.alignment()), layout);
    }

    /** Heap memory for {@code count} elements, zeroed. */
    public static ElementBuffer onHeap(ElementLayout layout, int count) {
        long bytes = Math.multiplyExact(layout.stride(), count);
        long[] words = new long[Math.toIntExact((bytes + 7) / 8)];
        return new ElementBuffer(MemorySegment.ofArray(words).asSlice(0, bytes), layout);
    }

    /** Elements in existing memory, e.g. a mapped file or a buffer shared with native code. */
    public static ElementBuffer wrap(MemorySegment segment, ElementLayout layout) {
        return new ElementBuffer(segment, layout);
    }

    /** Number of elements. */
    public int size() {
        return size;
    }

    public ElementLayout layout() {
        return layout;
    }

    /** The underlying memory, e.g. to upload to the GPU. */
    public MemorySegment segment() {
        return segment;
    }

    public ScalarAttribute scalar(String name) {
        return new ScalarAttribute(this, layout.field(name));
    }

    public IntAttribute ints(String name) {
        return new IntAttribute(this, layout.field(name));
    }

    public Vec2Attribute vec2(String name) {
        return new Vec2Attribute(this, layout.field(name));
    }

    public Vec3Attribute vec3(String name) {
        return new Vec3Attribute(this, layout.field(name));
    }

    public Vec4Attribute vec4(String name) {
        return new Vec4Attribute(this, layout.field(name));
    }

    public QuatAttribute quat(String name) {
        return new QuatAttribute(this, layout.field(name));
    }

    public ColorAttribute color(String name) {
        return new ColorAttribute(this, layout.field(name));
    }
}
