package com.truesim.common.math.layout;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The layout of one element of interleaved data, such as a vertex or a particle: named attributes, each with a
 * {@link Format}, at offsets computed from their natural alignment, and the stride from one element to the next.
 *
 * <p>The builder's typed methods return {@link Field} handles, which give their views without looking up names:
 *
 * <pre>{@code
 * ElementLayout.Builder builder = ElementLayout.builder();
 * Field<Vec3Attribute> position = builder.vec3("position", Format.FLOAT3);
 * Field<Vec3Attribute> normal = builder.vec3("normal", Format.OCT16);
 * Field<Vec2Attribute> uv = builder.vec2("uv", Format.HALF2);
 * Field<ColorAttribute> color = builder.color("color", Format.SRGB8_4);
 * ElementLayout vertex = builder.build(); // 24 bytes per vertex
 * }</pre>
 *
 * <p>For layouts read from data, {@link Builder#attribute(String, Format)} adds attributes by name, and
 * {@link ElementBuffer#vec3(String)} and friends find them by name. {@link #memoryLayout()} describes the layout as a
 * {@link StructLayout}, for the foreign function and memory API or to derive a GPU vertex description.
 */
public final class ElementLayout {

    private final List<Field<?>> fields;
    private final long stride;
    private final long alignment;

    private ElementLayout(List<Field<?>> fields, long stride, long alignment) {
        this.fields = List.copyOf(fields);
        this.stride = stride;
        this.alignment = alignment;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A layout with a single attribute, e.g. one array of a structure-of-arrays particle system. */
    public static ElementLayout of(String name, Format format) {
        return builder().attribute(name, format).build();
    }

    /** The attributes in order. */
    public List<Field<?>> fields() {
        return fields;
    }

    /** The attribute called {@code name}. */
    public Field<?> field(String name) {
        for (Field<?> field : fields) {
            if (field.name().equals(name)) {
                return field;
            }
        }
        throw new IllegalArgumentException("No attribute " + name + " in " + this);
    }

    /** Whether {@code field} is one of this layout's own fields. */
    boolean contains(Field<?> field) {
        for (Field<?> own : fields) {
            if (own == field) {
                return true;
            }
        }
        return false;
    }

    /** Bytes from one element to the next. */
    public long stride() {
        return stride;
    }

    /** Alignment of each element: the largest attribute alignment, or more if requested. */
    public long alignment() {
        return alignment;
    }

    /** The layout as a struct, padding included, named after the attributes. */
    public StructLayout memoryLayout() {
        List<MemoryLayout> members = new ArrayList<>();
        long cursor = 0;
        for (Field<?> field : fields) {
            if (field.offset() > cursor) {
                members.add(MemoryLayout.paddingLayout(field.offset() - cursor));
            }
            Format format = field.format();
            MemoryLayout member = format.components() == 1
                ? format.type.layout
                : MemoryLayout.sequenceLayout(format.components(), format.type.layout);
            members.add(member.withName(field.name()));
            cursor = field.offset() + format.byteSize();
        }
        if (stride > cursor) {
            members.add(MemoryLayout.paddingLayout(stride - cursor));
        }
        return MemoryLayout.structLayout(members.toArray(MemoryLayout[]::new)).withByteAlignment(alignment);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("ElementLayout[");
        for (Field<?> field : fields) {
            text.append(field).append(", ");
        }
        return text.append("stride ").append(stride).append(']').toString();
    }

    /** Collects attributes in order; each starts at the next offset its format's alignment allows. */
    public static final class Builder {

        private final List<Field<?>> fields = new ArrayList<>();
        private long end;
        private long alignment = 1;

        private Builder() {
        }

        /** Adds an attribute found by name later, e.g. for layouts read from data. */
        public Builder attribute(String name, Format format) {
            add(name, format, null);
            return this;
        }

        /** Adds a number per element ({@code DOUBLE1}, {@code FLOAT1}). */
        public Field<ScalarAttribute> scalar(String name, Format format) {
            return add(name, format, Field.Kind.SCALAR);
        }

        /** Adds a 32-bit integer per element ({@code INT1}). */
        public Field<IntAttribute> ints(String name, Format format) {
            return add(name, format, Field.Kind.INT);
        }

        public Field<Vec2Attribute> vec2(String name, Format format) {
            return add(name, format, Field.Kind.VEC2);
        }

        public Field<Vec3Attribute> vec3(String name, Format format) {
            return add(name, format, Field.Kind.VEC3);
        }

        public Field<Vec4Attribute> vec4(String name, Format format) {
            return add(name, format, Field.Kind.VEC4);
        }

        public Field<QuatAttribute> quat(String name, Format format) {
            return add(name, format, Field.Kind.QUAT);
        }

        public Field<ColorAttribute> color(String name, Format format) {
            return add(name, format, Field.Kind.COLOR);
        }

        private <A extends Attribute> Field<A> add(String name, Format format, Field.Kind kind) {
            if (kind != null && !kind.accepts(format)) {
                throw new IllegalArgumentException(
                    "A " + kind.name().toLowerCase(Locale.ROOT) + " attribute cannot use " + format);
            }
            for (Field<?> field : fields) {
                if (field.name().equals(name)) {
                    throw new IllegalArgumentException("Duplicate attribute " + name);
                }
            }
            long offset = align(end, format.alignment());
            Field<A> field = new Field<>(name, format, offset, kind);
            fields.add(field);
            end = offset + format.byteSize();
            alignment = Math.max(alignment, format.alignment());
            return field;
        }

        /**
         * Aligns each element to at least {@code bytes} (a power of two), e.g. 16 for GPU storage buffers that need
         * vec4 alignment. The stride grows to a multiple of it.
         */
        public Builder alignment(int bytes) {
            if (bytes < 1 || Integer.bitCount(bytes) != 1) {
                throw new IllegalArgumentException("Alignment must be a power of two: " + bytes);
            }
            alignment = Math.max(alignment, bytes);
            return this;
        }

        public ElementLayout build() {
            if (fields.isEmpty()) {
                throw new IllegalStateException("A layout needs at least one attribute");
            }
            return new ElementLayout(fields, align(end, alignment), alignment);
        }

        private static long align(long offset, long alignment) {
            return (offset + alignment - 1) / alignment * alignment;
        }
    }
}
