package com.truesim.common.math.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.truesim.common.math.Mat4;
import com.truesim.common.math.Quat;
import com.truesim.common.math.Vec2;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.Vec4;
import com.truesim.common.math.color.Color;
import com.truesim.common.math.random.Rng;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.Test;

class ElementLayoutTest {

    static final ElementLayout VERTEX = ElementLayout.builder()
        .attribute("position", Format.FLOAT3)
        .attribute("normal", Format.OCT16)
        .attribute("uv", Format.HALF2)
        .attribute("color", Format.SRGB8_4)
        .build();

    // ---------------------------------------------------------------- layouts

    @Test
    void offsetsFollowNaturalAlignment() {
        assertThat(VERTEX.stride()).isEqualTo(24);
        assertThat(VERTEX.alignment()).isEqualTo(4);
        assertThat(VERTEX.field("position").offset()).isZero();
        assertThat(VERTEX.field("normal").offset()).isEqualTo(12);
        assertThat(VERTEX.field("uv").offset()).isEqualTo(16);
        assertThat(VERTEX.field("color").offset()).isEqualTo(20);
        assertThat(VERTEX.fields()).extracting(Field::name).containsExactly("position", "normal", "uv", "color");
        StructLayout struct = VERTEX.memoryLayout();
        assertThat(struct.byteSize()).isEqualTo(24);
        assertThat(struct.byteOffset(PathElement.groupElement("uv"))).isEqualTo(16);
        assertThat(struct.byteOffset(PathElement.groupElement("color"))).isEqualTo(20);
        assertThat(VERTEX.toString()).isEqualTo(
            "ElementLayout[position: FLOAT3 @0, normal: OCT16 @12, uv: HALF2 @16, color: SRGB8_4 @20, stride 24]");
    }

    @Test
    void paddingAndAlignment() {
        // A float, then doubles aligned to 8: four bytes of padding inside, none at the end.
        ElementLayout particle = ElementLayout.builder()
            .attribute("age", Format.FLOAT1)
            .attribute("position", Format.DOUBLE3)
            .attribute("id", Format.INT1)
            .build();
        assertThat(particle.field("position").offset()).isEqualTo(8);
        assertThat(particle.field("id").offset()).isEqualTo(32);
        assertThat(particle.stride()).isEqualTo(40);
        assertThat(particle.memoryLayout().byteSize()).isEqualTo(40);
        assertThat(particle.memoryLayout().byteOffset(PathElement.groupElement("position"))).isEqualTo(8);
        // Requested 16-byte alignment pads a 12-byte element to 16.
        ElementLayout padded = ElementLayout.builder().attribute("p", Format.FLOAT3).alignment(16).alignment(4).build();
        assertThat(padded.stride()).isEqualTo(16);
        assertThat(padded.alignment()).isEqualTo(16);
        assertThat(padded.memoryLayout().byteSize()).isEqualTo(16);
        assertThat(padded.memoryLayout().byteAlignment()).isEqualTo(16);
        // A single attribute, as in a structure of arrays.
        ElementLayout single = ElementLayout.of("velocity", Format.DOUBLE3);
        assertThat(single.stride()).isEqualTo(24);
        assertThat(single.memoryLayout().byteSize()).isEqualTo(24);
    }

    @Test
    void formats() {
        assertThat(Format.FLOAT3.components()).isEqualTo(3);
        assertThat(Format.FLOAT3.byteSize()).isEqualTo(12);
        assertThat(Format.FLOAT3.alignment()).isEqualTo(4);
        assertThat(Format.OCT16.byteSize()).isEqualTo(4);
        assertThat(Format.SRGB8_4.alignment()).isEqualTo(1);
        assertThat(Format.DOUBLE1.byteSize()).isEqualTo(8);
    }

    @Test
    void invalidLayouts() {
        assertThatIllegalArgumentException().isThrownBy(() -> VERTEX.field("tangent"));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ElementLayout.builder().attribute("p", Format.FLOAT3).attribute("p", Format.FLOAT2));
        assertThatIllegalArgumentException().isThrownBy(() -> ElementLayout.builder().alignment(0));
        assertThatIllegalArgumentException().isThrownBy(() -> ElementLayout.builder().alignment(12));
        assertThatIllegalStateException().isThrownBy(() -> ElementLayout.builder().build());
    }

    @Test
    void typedFields() {
        ElementLayout.Builder builder = ElementLayout.builder();
        Field<Vec3Attribute> position = builder.vec3("position", Format.FLOAT3);
        Field<Vec3Attribute> normal = builder.vec3("normal", Format.OCT16);
        Field<Vec2Attribute> uv = builder.vec2("uv", Format.HALF2);
        Field<Vec4Attribute> tangent = builder.vec4("tangent", Format.SNORM8_4);
        Field<QuatAttribute> spin = builder.quat("spin", Format.FLOAT4);
        Field<ColorAttribute> color = builder.color("color", Format.SRGB8_4);
        Field<ScalarAttribute> age = builder.scalar("age", Format.FLOAT1);
        Field<IntAttribute> id = builder.ints("id", Format.INT1);
        builder.attribute("extra", Format.FLOAT2);
        ElementLayout layout = builder.build();
        ElementBuffer buffer = ElementBuffer.onHeap(layout, 3);
        // Views straight from the handles: no names involved.
        buffer.attribute(position).set(2, Vec3.of(1, 2, 3));
        buffer.attribute(normal).set(2, Vec3.UNIT_Y);
        buffer.attribute(uv).set(2, Vec2.of(0.5, 0.25));
        buffer.attribute(tangent).set(2, Vec4.of(1, 0, 0, -1));
        buffer.attribute(spin).set(2, Quat.IDENTITY);
        buffer.attribute(color).set(2, Color.rgba(1, 0, 0, 1));
        buffer.attribute(age).set(2, 1.5);
        buffer.attribute(id).set(2, 42);
        // The same memory through the name-based views.
        assertThat(buffer.vec3("position").get(2)).isEqualTo(Vec3.of(1, 2, 3));
        assertThat(buffer.vec3("normal").get(2).y()).isEqualTo(1);
        assertThat(buffer.vec2("uv").get(2)).isEqualTo(Vec2.of(0.5, 0.25));
        assertThat(buffer.vec4("tangent").w(2)).isEqualTo(-1);
        assertThat(buffer.quat("spin").get(2)).isEqualTo(Quat.IDENTITY);
        assertThat(buffer.color("color").get(2).r()).isEqualTo(1);
        assertThat(buffer.scalar("age").get(2)).isEqualTo(1.5);
        assertThat(buffer.ints("id").get(2)).isEqualTo(42);
        assertThat(position.name()).isEqualTo("position");
        assertThat(position.format()).isEqualTo(Format.FLOAT3);
        assertThat(normal.offset()).isEqualTo(12);
        assertThat(position.toString()).isEqualTo("position: FLOAT3 @0");
        assertThat(layout.fields()).startsWith(position, normal);
        assertThat(layout.field("uv")).isSameAs(uv);
    }

    @Test
    void typedFieldsAreCheckedEarly() {
        // A format the view cannot hold fails when the layout is built, not on first use.
        assertThatIllegalArgumentException().isThrownBy(() -> ElementLayout.builder().vec3("p", Format.HALF2))
            .withMessage("A vec3 attribute cannot use HALF2");
        assertThatIllegalArgumentException().isThrownBy(() -> ElementLayout.builder().color("c", Format.SNORM8_4));
        // Handles belong to their layout.
        ElementLayout.Builder builder = ElementLayout.builder();
        Field<Vec3Attribute> position = builder.vec3("position", Format.FLOAT3);
        builder.build();
        ElementLayout.Builder twin = ElementLayout.builder();
        twin.vec3("position", Format.FLOAT3);
        ElementBuffer other = ElementBuffer.onHeap(twin.build(), 1);
        assertThatIllegalArgumentException().isThrownBy(() -> other.attribute(position))
            .withMessageContaining("is not part of");
        // Fields added by name have no typed view.
        ElementBuffer named = ElementBuffer.onHeap(ElementLayout.of("p", Format.FLOAT3), 1);
        Field<?> untyped = named.layout().field("p");
        assertThatIllegalArgumentException().isThrownBy(() -> named.attribute(untyped))
            .withMessageContaining("was added by name");
    }

    // ---------------------------------------------------------------- buffers

    @Test
    void buffers() {
        try (Arena arena = Arena.ofConfined()) {
            ElementBuffer offHeap = ElementBuffer.allocate(arena, VERTEX, 10);
            assertThat(offHeap.size()).isEqualTo(10);
            assertThat(offHeap.segment().byteSize()).isEqualTo(240);
            assertThat(offHeap.layout()).isSameAs(VERTEX);
            assertThat(offHeap.segment().isNative()).isTrue();
        }
        ElementBuffer heap = ElementBuffer.onHeap(VERTEX, 3);
        assertThat(heap.size()).isEqualTo(3);
        assertThat(heap.segment().byteSize()).isEqualTo(72);
        ElementBuffer wrapped = ElementBuffer.wrap(MemorySegment.ofArray(new byte[48]), VERTEX);
        assertThat(wrapped.size()).isEqualTo(2);
        assertThatIllegalArgumentException().isThrownBy(() -> ElementBuffer.wrap(MemorySegment.ofArray(new byte[50]), VERTEX));
        Vec3Attribute position = heap.vec3("position");
        assertThat(position.name()).isEqualTo("position");
        assertThat(position.format()).isEqualTo(Format.FLOAT3);
        assertThat(position.size()).isEqualTo(3);
        assertThatThrownBy(() -> position.get(3)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> position.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void attributesAreIndependent() {
        ElementBuffer vertices = ElementBuffer.onHeap(VERTEX, 4);
        Vec3Attribute position = vertices.vec3("position");
        Vec3Attribute normal = vertices.vec3("normal");
        Vec2Attribute uv = vertices.vec2("uv");
        ColorAttribute color = vertices.color("color");
        for (int i = 0; i < 4; i++) {
            position.set(i, Vec3.of(i, 2 * i, 3 * i));
            normal.set(i, Vec3.of(0, 1, 0));
            uv.set(i, Vec2.of(0.25 * i, 0.5));
            color.set(i, Color.rgba(1, 0.5, 0, 1));
        }
        for (int i = 0; i < 4; i++) {
            assertThat(position.get(i)).isEqualTo(Vec3.of(i, 2 * i, 3 * i));
            assertThat(normal.get(i).y()).isCloseTo(1, within(1e-12));
            assertThat(uv.get(i)).isEqualTo(Vec2.of(0.25 * i, 0.5));
            assertThat(color.get(i).r()).isEqualTo(1);
        }
        // The bytes are where the layout says: element 2's position x at byte 48 as a float.
        assertThat(vertices.segment().get(ValueLayout.JAVA_FLOAT_UNALIGNED, 48)).isEqualTo(2f);
        assertThat(vertices.segment().get(ValueLayout.JAVA_FLOAT_UNALIGNED, 48 + 4)).isEqualTo(4f);
    }

    @Test
    void viewsRejectFormatsTheyCannotHold() {
        ElementBuffer buffer = ElementBuffer.onHeap(VERTEX, 1);
        assertThatIllegalArgumentException().isThrownBy(() -> buffer.vec2("position"))
            .withMessageContaining("Vec2Attribute cannot hold position in format FLOAT3");
        assertThatIllegalArgumentException().isThrownBy(() -> buffer.vec4("position"));
        assertThatIllegalArgumentException().isThrownBy(() -> buffer.vec3("uv"));
        assertThatIllegalArgumentException().isThrownBy(() -> buffer.quat("color"));
        assertThatIllegalArgumentException().isThrownBy(() -> buffer.color("normal"));
        assertThatIllegalArgumentException().isThrownBy(() -> buffer.scalar("uv"));
        assertThatIllegalArgumentException().isThrownBy(() -> buffer.ints("uv"));
    }

    // ---------------------------------------------------------------- formats through the views

    private static ElementBuffer single(Format format) {
        return ElementBuffer.onHeap(ElementLayout.of("a", format), 2);
    }

    @Test
    void scalarsAndInts() {
        ScalarAttribute d = single(Format.DOUBLE1).scalar("a");
        d.set(1, 0.1);
        assertThat(d.get(1)).isEqualTo(0.1);
        ScalarAttribute f = single(Format.FLOAT1).scalar("a");
        f.set(1, 0.1);
        assertThat(f.get(1)).isEqualTo((double) 0.1f);
        IntAttribute id = single(Format.INT1).ints("a");
        id.set(0, -7);
        assertThat(id.get(0)).isEqualTo(-7);
    }

    @Test
    void vec2Formats() {
        Vec2Attribute d = single(Format.DOUBLE2).vec2("a");
        d.set(0, Vec2.of(0.1, -0.2));
        assertThat(d.get(0)).isEqualTo(Vec2.of(0.1, -0.2));
        Vec2Attribute f = single(Format.FLOAT2).vec2("a");
        f.set(0, 0.1, -0.2);
        assertThat(f.x(0)).isEqualTo((double) 0.1f);
        assertThat(f.y(0)).isEqualTo((double) -0.2f);
        Vec2Attribute h = single(Format.HALF2).vec2("a");
        h.set(0, 0.5, 1000.3);
        assertThat(h.get(0).x()).isEqualTo(0.5);
        assertThat(h.get(0).y()).isCloseTo(1000.3, within(0.5));
        // Normalized integers clamp, round to nearest, and read NaN as 0.
        Vec2Attribute u = single(Format.UNORM16_2).vec2("a");
        u.set(0, -0.5, 2);
        assertThat(u.get(0)).isEqualTo(Vec2.of(0, 1));
        u.set(1, Double.NaN, 0.5);
        assertThat(u.x(1)).isZero();
        assertThat(u.y(1)).isCloseTo(0.5, within(1.0 / 65535));
        Vec2Attribute s = single(Format.SNORM16_2).vec2("a");
        s.set(0, -3, 0.25);
        assertThat(s.x(0)).isEqualTo(-1);
        assertThat(s.y(0)).isCloseTo(0.25, within(1.0 / 32767));
        // -32768 also reads as -1.
        s.set(1, 0, 0);
        ElementBuffer raw = ElementBuffer.onHeap(ElementLayout.of("a", Format.SNORM16_2), 1);
        raw.segment().set(ValueLayout.JAVA_SHORT_UNALIGNED, 0, Short.MIN_VALUE);
        assertThat(raw.vec2("a").x(0)).isEqualTo(-1);
    }

    @Test
    void vec3Formats() {
        Rng rng = Rng.seeded(1);
        for (Format format : new Format[] {Format.DOUBLE3, Format.FLOAT3, Format.HALF4, Format.SNORM8_4, Format.SNORM16_4}) {
            ElementBuffer buffer = single(format);
            Vec3Attribute v = buffer.vec3("a");
            double tolerance = switch (format) {
                case DOUBLE3 -> 0;
                case FLOAT3 -> 1e-7;
                case HALF4 -> 1e-3;
                case SNORM8_4 -> 0.5 / 127;
                default -> 0.5 / 32767;
            };
            for (int i = 0; i < 50; i++) {
                Vec3 value = rng.nextVec3InSphere().scale(0.9);
                v.set(1, value);
                assertThat(v.x(1)).isCloseTo(value.x(), within(tolerance));
                assertThat(v.y(1)).isCloseTo(value.y(), within(tolerance));
                assertThat(v.z(1)).isCloseTo(value.z(), within(tolerance));
            }
            if (format.components() == 4) {
                // The fourth component is written as 0.
                assertThat(format.read(buffer.segment(), buffer.layout().stride() + 3L * (format.byteSize() / 4))).isZero();
            }
        }
    }

    @Test
    void octahedralNormals() {
        Vec3Attribute n = single(Format.OCT16).vec3("a");
        Rng rng = Rng.seeded(2);
        for (int i = 0; i < 2000; i++) {
            Vec3 normal = rng.nextVec3OnSphere();
            if (i < 6) {
                // The axes, including the poles where the fold meets.
                normal = new Vec3[] {Vec3.UNIT_X, Vec3.UNIT_Y, Vec3.UNIT_Z, Vec3.UNIT_X.negate(), Vec3.UNIT_Y.negate(),
                    Vec3.UNIT_Z.negate()}[i];
            }
            n.set(0, normal.scale(3)); // any length: only the direction is kept
            Vec3 decoded = n.get(0);
            assertThat(decoded.length()).isCloseTo(1, within(1e-12));
            assertThat(decoded.dot(normal)).isGreaterThan(Math.cos(1e-4));
            assertThat(n.x(0)).isEqualTo(decoded.x());
            assertThat(n.y(0)).isEqualTo(decoded.y());
            assertThat(n.z(0)).isEqualTo(decoded.z());
        }
        n.set(1, Vec3.ZERO);
        assertThat(n.get(1)).isEqualTo(Vec3.UNIT_Z);
    }

    @Test
    void bulkTransforms() {
        ElementBuffer buffer = ElementBuffer.onHeap(ElementLayout.builder()
            .attribute("position", Format.DOUBLE3).attribute("normal", Format.OCT16).build(), 3);
        Vec3Attribute position = buffer.vec3("position");
        Vec3Attribute normal = buffer.vec3("normal");
        for (int i = 0; i < 3; i++) {
            position.set(i, Vec3.of(i, 0, 0));
            normal.set(i, Vec3.UNIT_X);
        }
        Mat4 m = Mat4.trs(Vec3.of(1, 2, 3), Quat.fromAxisAngle(Vec3.UNIT_Z, Math.PI / 2), Vec3.ONE);
        position.transformPoints(m);
        normal.transformDirections(m);
        for (int i = 0; i < 3; i++) {
            assertThat(position.get(i).x()).isCloseTo(1, within(1e-12));
            assertThat(position.get(i).y()).isCloseTo(2 + i, within(1e-12));
            assertThat(position.get(i).z()).isCloseTo(3, within(1e-12));
            assertThat(normal.get(i).y()).isCloseTo(1, within(1e-9));
        }
    }

    @Test
    void vec4Formats() {
        Format[] formats = {Format.DOUBLE4, Format.FLOAT4, Format.HALF4, Format.UNORM8_4, Format.SNORM8_4,
            Format.UNORM16_4, Format.SNORM16_4};
        for (Format format : formats) {
            Vec4Attribute v = single(format).vec4("a");
            v.set(0, Vec4.of(0.25, 0.5, 0.75, 1));
            assertThat(v.x(0)).isCloseTo(0.25, within(0.5 / 127));
            assertThat(v.y(0)).isCloseTo(0.5, within(0.5 / 127));
            assertThat(v.z(0)).isCloseTo(0.75, within(0.5 / 127));
            assertThat(v.w(0)).isEqualTo(1);
        }
        Vec4Attribute bytes = single(Format.UNORM8_4).vec4("a");
        bytes.set(1, 0.5, 1.0 / 255, 0.002, 2);
        assertThat(bytes.get(1)).isEqualTo(Vec4.of(128 / 255.0, 1 / 255.0, 1 / 255.0, 1));
    }

    @Test
    void quaternions() {
        Quat q = Quat.fromYawPitchRoll(0.3, -1.2, 2.1);
        QuatAttribute exact = single(Format.DOUBLE4).quat("a");
        exact.set(0, q);
        assertThat(exact.get(0)).isEqualTo(q);
        for (Format format : new Format[] {Format.FLOAT4, Format.HALF4, Format.SNORM16_4}) {
            QuatAttribute compact = single(format).quat("a");
            compact.set(1, q);
            Quat read = compact.get(1);
            assertThat(read.length()).isCloseTo(1, within(1e-12));
            assertThat(Math.abs(read.dot(q))).isCloseTo(1, within(1e-5));
        }
    }

    @Test
    void colors() {
        Color color = Color.rgba(0.8, 0.2, 0.05, 0.5);
        // SRGB8_4 stores the same bytes as Color.writeRgba8.
        ElementBuffer srgbBuffer = single(Format.SRGB8_4);
        srgbBuffer.color("a").set(0, color);
        MemorySegment reference = MemorySegment.ofArray(new byte[4]);
        color.writeRgba8(reference, 0);
        assertThat(srgbBuffer.segment().asSlice(0, 4).toArray(ValueLayout.JAVA_BYTE))
            .containsExactly(reference.toArray(ValueLayout.JAVA_BYTE));
        assertThat(srgbBuffer.color("a").get(0)).isEqualTo(Color.readRgba8(reference, 0));
        for (Format format : new Format[] {Format.DOUBLE4, Format.FLOAT4, Format.HALF4, Format.UNORM8_4}) {
            ColorAttribute c = single(format).color("a");
            c.set(1, color);
            assertThat(c.get(1).approxEquals(color, 0.5 / 255)).isTrue();
        }
    }
}
