package com.truesim.common.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import org.junit.jupiter.api.Test;

class Vec3Test {

    private final Vec3 a = Vec3.of(1, 2, 3);
    private final Vec3 b = Vec3.of(4, -5, 6);

    @Test
    void constants() {
        assertThat(Vec3.ZERO).isEqualTo(new Vec3(0, 0, 0));
        assertThat(Vec3.ONE).isEqualTo(Vec3.splat(1));
        assertThat(Vec3.UNIT_X.cross(Vec3.UNIT_Y)).isEqualTo(Vec3.UNIT_Z);
        assertThat(Vec3.RIGHT.cross(Vec3.UP).approxEquals(Vec3.FORWARD.negate(), 0)).isTrue();
        assertThat(Vec3.LAYOUT.byteSize()).isEqualTo(24);
        assertThat(Vec3.FLOAT_LAYOUT.byteSize()).isEqualTo(12);
    }

    @Test
    void arithmetic() {
        assertThat(a.add(b)).isEqualTo(Vec3.of(5, -3, 9));
        assertThat(a.add(1, 1, 1)).isEqualTo(Vec3.of(2, 3, 4));
        assertThat(a.sub(b)).isEqualTo(Vec3.of(-3, 7, -3));
        assertThat(a.sub(1, 1, 1)).isEqualTo(Vec3.of(0, 1, 2));
        assertThat(a.mul(b)).isEqualTo(Vec3.of(4, -10, 18));
        assertThat(b.div(a)).isEqualTo(Vec3.of(4, -2.5, 2));
        assertThat(a.scale(2)).isEqualTo(Vec3.of(2, 4, 6));
        assertThat(a.div(2)).isEqualTo(Vec3.of(0.5, 1, 1.5));
        assertThat(a.negate()).isEqualTo(Vec3.of(-1, -2, -3));
        assertThat(a.addScaled(b, 0.5)).isEqualTo(Vec3.of(3, -0.5, 6));
    }

    @Test
    void productsAndLengths() {
        assertThat(a.dot(b)).isEqualTo(12.0);
        assertThat(a.cross(b)).isEqualTo(Vec3.of(27, 6, -13));
        assertThat(Vec3.of(2, 3, 6).length()).isEqualTo(7.0);
        assertThat(a.lengthSquared()).isEqualTo(14.0);
        assertThat(a.distance(a.add(2, 3, 6))).isEqualTo(7.0);
        assertThat(a.distanceSquared(b)).isEqualTo(67.0);
        assertThat(Vec3.of(0, 3, 4).normalize().approxEquals(Vec3.of(0, 0.6, 0.8), 1e-15)).isTrue();
        assertThat(Vec3.ZERO.normalize()).isEqualTo(Vec3.ZERO);
        assertThat(Vec3.of(0, 3, 4).clampLength(10)).isEqualTo(Vec3.of(0, 3, 4));
        assertThat(Vec3.of(0, 3, 4).clampLength(1).approxEquals(Vec3.of(0, 0.6, 0.8), 1e-15)).isTrue();
        assertThat(Vec3.UNIT_X.angle(Vec3.UNIT_Y)).isCloseTo(Maths.HALF_PI, within(1e-15));
    }

    @Test
    void geometry() {
        assertThat(Vec3.of(1, -1, 0).reflect(Vec3.UNIT_Y)).isEqualTo(Vec3.of(1, 1, 0));
        assertThat(a.project(Vec3.of(0, 2, 0))).isEqualTo(Vec3.of(0, 2, 0));
        assertThat(a.project(Vec3.ZERO)).isEqualTo(Vec3.ZERO);
        assertThat(a.reject(Vec3.UNIT_Y)).isEqualTo(Vec3.of(1, 0, 3));
        assertThat(a.interpolate(b, 0.5)).isEqualTo(Vec3.of(2.5, -1.5, 4.5));
        assertThat(a.min(b)).isEqualTo(Vec3.of(1, -5, 3));
        assertThat(a.max(b)).isEqualTo(Vec3.of(4, 2, 6));
        assertThat(b.abs()).isEqualTo(Vec3.of(4, 5, 6));
        assertThat(b.clamp(Vec3.ZERO, Vec3.splat(5))).isEqualTo(Vec3.of(4, 0, 5));
    }

    @Test
    void components() {
        assertThat(a.withX(9)).isEqualTo(Vec3.of(9, 2, 3));
        assertThat(a.withY(9)).isEqualTo(Vec3.of(1, 9, 3));
        assertThat(a.withZ(9)).isEqualTo(Vec3.of(1, 2, 9));
        assertThat(a.xy()).isEqualTo(Vec2.of(1, 2));
        assertThat(a.xz()).isEqualTo(Vec2.of(1, 3));
        assertThat(a.yz()).isEqualTo(Vec2.of(2, 3));
        assertThat(a.extend(1)).isEqualTo(Vec4.of(1, 2, 3, 1));
    }

    @Test
    void comparisons() {
        assertThat(a.approxEquals(a.add(1e-9, 1e-9, 1e-9), 1e-6)).isTrue();
        assertThat(a.approxEquals(a.add(1, 0, 0), 1e-6)).isFalse();
        assertThat(a.approxEquals(a.add(0, 1, 0), 1e-6)).isFalse();
        assertThat(a.approxEquals(a.add(0, 0, 1), 1e-6)).isFalse();
        assertThat(a.isFinite()).isTrue();
        assertThat(a.withX(Double.NaN).isFinite()).isFalse();
        assertThat(a.withY(Double.POSITIVE_INFINITY).isFinite()).isFalse();
        assertThat(a.withZ(Double.NaN).isFinite()).isFalse();
    }

    @Test
    void memory() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(64);
            a.write(segment, 8);
            assertThat(Vec3.read(segment, 8)).isEqualTo(a);
            a.writeFloats(segment, 4);
            assertThat(Vec3.readFloats(segment, 4)).isEqualTo(a);
        }
        ByteBuffer bytes = ByteBuffer.allocate(64);
        a.write(bytes, 8);
        assertThat(Vec3.read(bytes, 8)).isEqualTo(a);
        DoubleBuffer doubles = DoubleBuffer.allocate(8);
        a.write(doubles, 2);
        assertThat(Vec3.read(doubles, 2)).isEqualTo(a);
    }
}
