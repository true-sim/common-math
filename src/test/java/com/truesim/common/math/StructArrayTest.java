package com.truesim.common.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.Test;

class StructArrayTest {

    @Test
    void vec3ArrayOnHeapAndOffHeap() {
        try (Arena arena = Arena.ofConfined()) {
            for (Vec3Array array : new Vec3Array[] {Vec3Array.onHeap(4), Vec3Array.allocate(arena, 4)}) {
                assertThat(array.size()).isEqualTo(4);
                assertThat(array.stride()).isEqualTo(24);
                assertThat(array.segment().byteSize()).isEqualTo(96);
                assertThat(array.get(3)).isEqualTo(Vec3.ZERO);
                array.set(2, Vec3.of(1, 2, 3));
                assertThat(array.get(2)).isEqualTo(Vec3.of(1, 2, 3));
                array.fill(Vec3.ONE);
                assertThat(array.get(0)).isEqualTo(Vec3.ONE);
                MemorySegment floats = arena.allocate(Vec3.FLOAT_LAYOUT, 4);
                array.writeFloats(floats, 0);
                assertThat(Vec3.readFloats(floats, 36)).isEqualTo(Vec3.ONE);
                assertThatThrownBy(() -> array.get(4)).isInstanceOf(IndexOutOfBoundsException.class);
                assertThatThrownBy(() -> array.set(-1, Vec3.ONE)).isInstanceOf(IndexOutOfBoundsException.class);
            }
        }
    }

    @Test
    void mat4ArrayWorksInMemory() {
        Quat rotation = Quat.fromYawPitchRoll(0.3, -0.2, 0.1);
        Mat4 a = Mat4.trs(Vec3.of(1, 2, 3), rotation, Vec3.of(1, 2, 1));
        Mat4 b = Mat4.trs(Vec3.of(-1, 0, 4), rotation.conjugate(), Vec3.ONE);
        Mat4Array m = Mat4Array.onHeap(3);
        m.set(0, a);
        m.set(1, b);
        m.setProduct(2, m, 0, m, 1);
        assertThat(m.get(2).approxEquals(a.mul(b), 1e-12)).isTrue();
        m.setProduct(0, m, 0, m, 0); // target aliases both inputs
        assertThat(m.get(0).approxEquals(a.mul(a), 1e-12)).isTrue();
        Mat4Array other = Mat4Array.onHeap(1);
        other.set(0, a);
        m.setProduct(1, m, 1, other, 0);
        assertThat(m.get(1).approxEquals(b.mul(a), 1e-12)).isTrue();
        assertThat(m.get(1, 0, 3)).isEqualTo(m.get(1).m03());

        Vec3Array points = Vec3Array.onHeap(4);
        points.fill(Vec3.of(1, 2, 3));
        m.set(2, a);
        m.transformPoints(2, points, 1, 2);
        assertThat(points.get(0)).isEqualTo(Vec3.of(1, 2, 3));
        assertThat(points.get(1).approxEquals(a.transformPoint(Vec3.of(1, 2, 3)), 1e-12)).isTrue();
        assertThat(points.get(3)).isEqualTo(Vec3.of(1, 2, 3));
        m.transformDirections(2, points, 3, 1);
        assertThat(points.get(3).approxEquals(a.transformDirection(Vec3.of(1, 2, 3)), 1e-12)).isTrue();
        assertThatThrownBy(() -> m.transformPoints(2, points, 3, 2)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void wrapExistingMemory() {
        double[] backing = {1, 2, 3, 4, 5, 6};
        Vec3Array array = Vec3Array.wrap(MemorySegment.ofArray(backing));
        assertThat(array.size()).isEqualTo(2);
        assertThat(array.get(1)).isEqualTo(Vec3.of(4, 5, 6));
        array.set(0, Vec3.ZERO);
        assertThat(backing[0]).isEqualTo(0.0);
        assertThatThrownBy(() -> Vec3Array.wrap(MemorySegment.ofArray(new double[4])))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("multiple of 24");
    }

    @Test
    void allArrayTypes() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment floats = arena.allocate(1024);

            Vec2Array vec2 = Vec2Array.allocate(arena, 2);
            vec2.fill(Vec2.of(1, 2));
            vec2.set(1, Vec2.ONE);
            vec2.writeFloats(floats, 0);
            assertThat(Vec2Array.wrap(vec2.segment()).get(0)).isEqualTo(Vec2.of(1, 2));
            assertThat(Vec2Array.onHeap(3).get(2)).isEqualTo(Vec2.ZERO);
            assertThat(Vec2.readFloats(floats, 8)).isEqualTo(Vec2.ONE);

            Vec4Array vec4 = Vec4Array.allocate(arena, 2);
            vec4.fill(Vec4.ONE);
            vec4.set(1, Vec4.UNIT_W);
            vec4.writeFloats(floats, 0);
            assertThat(Vec4Array.wrap(vec4.segment()).get(1)).isEqualTo(Vec4.UNIT_W);
            assertThat(Vec4Array.onHeap(1).get(0)).isEqualTo(Vec4.ZERO);
            assertThat(Vec4.readFloats(floats, 16)).isEqualTo(Vec4.UNIT_W);

            QuatArray quats = QuatArray.allocate(arena, 2);
            quats.fill(Quat.IDENTITY);
            quats.set(0, Quat.of(1, 0, 0, 0));
            quats.writeFloats(floats, 0);
            assertThat(QuatArray.wrap(quats.segment()).get(1)).isEqualTo(Quat.IDENTITY);
            assertThat(QuatArray.onHeap(1).get(0)).isEqualTo(Quat.of(0, 0, 0, 0));
            assertThat(Quat.readFloats(floats, 0)).isEqualTo(Quat.of(1, 0, 0, 0));

            Mat3Array mat3 = Mat3Array.allocate(arena, 2);
            mat3.fill(Mat3.IDENTITY);
            mat3.set(1, Mat3.scaling(2));
            mat3.writeFloats(floats, 0);
            assertThat(Mat3Array.wrap(mat3.segment()).get(0)).isEqualTo(Mat3.IDENTITY);
            assertThat(Mat3Array.onHeap(1).get(0)).isEqualTo(Mat3.ZERO);
            assertThat(Mat3.readFloats(floats, 36)).isEqualTo(Mat3.scaling(2));

            Mat4Array mat4 = Mat4Array.allocate(arena, 2);
            mat4.fill(Mat4.IDENTITY);
            mat4.set(1, Mat4.scaling(2));
            mat4.writeFloats(floats, 0);
            assertThat(Mat4Array.wrap(mat4.segment()).get(0)).isEqualTo(Mat4.IDENTITY);
            assertThat(Mat4Array.onHeap(1).get(0)).isEqualTo(Mat4.ZERO);
            assertThat(Mat4.readFloats(floats, 64)).isEqualTo(Mat4.scaling(2));
        }
    }
}
