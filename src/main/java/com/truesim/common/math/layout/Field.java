package com.truesim.common.math.layout;

/**
 * One attribute of an {@link ElementLayout}: its name, format and byte offset in the element. Fields made by the
 * builder's typed methods ({@code vec3(...)}, {@code color(...)}, ...) are handles: keep them, and
 * {@link ElementBuffer#attribute(Field)} gives the matching view without looking anything up by name, so a misspelled
 * attribute is a compile error rather than a runtime one.
 *
 * <pre>{@code
 * ElementLayout.Builder builder = ElementLayout.builder();
 * Field<Vec3Attribute> position = builder.vec3("position", Format.FLOAT3);
 * ElementLayout vertex = builder.build();
 * Vec3Attribute positions = ElementBuffer.onHeap(vertex, count).attribute(position);
 * }</pre>
 *
 * @param <A> the view this field gives; {@link Attribute} for fields added by name only
 */
public final class Field<A extends Attribute> {

    /** The view a typed field gives, and the formats it accepts. */
    enum Kind {
        SCALAR, INT, VEC2, VEC3, VEC4, QUAT, COLOR;

        boolean accepts(Format format) {
            return switch (this) {
                case SCALAR -> ScalarAttribute.accepts(format);
                case INT -> IntAttribute.accepts(format);
                case VEC2 -> Vec2Attribute.accepts(format);
                case VEC3 -> Vec3Attribute.accepts(format);
                case VEC4 -> Vec4Attribute.accepts(format);
                case QUAT -> QuatAttribute.accepts(format);
                case COLOR -> ColorAttribute.accepts(format);
            };
        }

        Attribute view(ElementBuffer buffer, Field<?> field) {
            return switch (this) {
                case SCALAR -> new ScalarAttribute(buffer, field);
                case INT -> new IntAttribute(buffer, field);
                case VEC2 -> new Vec2Attribute(buffer, field);
                case VEC3 -> new Vec3Attribute(buffer, field);
                case VEC4 -> new Vec4Attribute(buffer, field);
                case QUAT -> new QuatAttribute(buffer, field);
                case COLOR -> new ColorAttribute(buffer, field);
            };
        }
    }

    private final String name;
    private final Format format;
    private final long offset;
    final Kind kind;

    Field(String name, Format format, long offset, Kind kind) {
        this.name = name;
        this.format = format;
        this.offset = offset;
        this.kind = kind;
    }

    public String name() {
        return name;
    }

    public Format format() {
        return format;
    }

    /** Byte offset within the element. */
    public long offset() {
        return offset;
    }

    @Override
    public String toString() {
        return name + ": " + format + " @" + offset;
    }
}
