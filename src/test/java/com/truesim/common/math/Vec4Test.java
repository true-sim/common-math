package com.truesim.common.math;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import org.junit.jupiter.api.Test;

class Vec4Test {

    private final Vec4 a = Vec4.of(1, 2, 3, 4);
    private final Vec4 b = Vec4.of(2, -4, 6, -8);

    @Test
    void constants() {
        assertThat(Vec4.ZERO).isEqualTo(Vec4.splat(0));
        assertThat(Vec4.ONE).isEqualTo(Vec4.splat(1));
        assertThat(Vec4.UNIT_X.add(Vec4.UNIT_Y).add(Vec4.UNIT_Z).add(Vec4.UNIT_W)).isEqualTo(Vec4.ONE);
        assertThat(Vec4.LAYOUT.byteSize()).isEqualTo(32);
        assertThat(Vec4.FLOAT_LAYOUT.byteSize()).isEqualTo(16);
    }

    @Test
    void arithmetic() {
        assertThat(a.add(b)).isEqualTo(Vec4.of(3, -2, 9, -4));
        assertThat(a.sub(b)).isEqualTo(Vec4.of(-1, 6, -3, 12));
        assertThat(a.mul(b)).isEqualTo(Vec4.of(2, -8, 18, -32));
        assertThat(b.div(a)).isEqualTo(Vec4.of(2, -2, 2, -2));
        assertThat(a.scale(2)).isEqualTo(Vec4.of(2, 4, 6, 8));
        assertThat(a.div(2)).isEqualTo(Vec4.of(0.5, 1, 1.5, 2));
        assertThat(a.negate()).isEqualTo(Vec4.of(-1, -2, -3, -4));
        assertThat(a.dot(b)).isEqualTo(-20.0);
        assertThat(Vec4.of(1, 1, 1, 1).length()).isEqualTo(2.0);
        assertThat(a.lengthSquared()).isEqualTo(30.0);
        assertThat(Vec4.ZERO.distance(Vec4.of(1, 1, 1, 1))).isEqualTo(2.0);
        assertThat(Vec4.of(0, 0, 3, 4).normalize()).isEqualTo(Vec4.of(0, 0, 0.6, 0.8));
        assertThat(Vec4.ZERO.normalize()).isEqualTo(Vec4.ZERO);
        assertThat(a.interpolate(b, 0.5)).isEqualTo(Vec4.of(1.5, -1, 4.5, -2));
        assertThat(a.min(b)).isEqualTo(Vec4.of(1, -4, 3, -8));
        assertThat(a.max(b)).isEqualTo(Vec4.of(2, 2, 6, 4));
        assertThat(b.abs()).isEqualTo(Vec4.of(2, 4, 6, 8));
    }

    @Test
    void components() {
        assertThat(a.withX(9)).isEqualTo(Vec4.of(9, 2, 3, 4));
        assertThat(a.withY(9)).isEqualTo(Vec4.of(1, 9, 3, 4));
        assertThat(a.withZ(9)).isEqualTo(Vec4.of(1, 2, 9, 4));
        assertThat(a.withW(9)).isEqualTo(Vec4.of(1, 2, 3, 9));
        assertThat(a.xyz()).isEqualTo(Vec3.of(1, 2, 3));
        assertThat(Vec4.of(2, 4, 6, 2).perspectiveDivide()).isEqualTo(Vec3.of(1, 2, 3));
    }

    @Test
    void comparisons() {
        assertThat(a.approxEquals(a.add(Vec4.splat(1e-9)), 1e-6)).isTrue();
        assertThat(a.approxEquals(a.add(Vec4.UNIT_X), 1e-6)).isFalse();
        assertThat(a.approxEquals(a.add(Vec4.UNIT_Y), 1e-6)).isFalse();
        assertThat(a.approxEquals(a.add(Vec4.UNIT_Z), 1e-6)).isFalse();
        assertThat(a.approxEquals(a.add(Vec4.UNIT_W), 1e-6)).isFalse();
        assertThat(a.isFinite()).isTrue();
        assertThat(a.withX(Double.NaN).isFinite()).isFalse();
        assertThat(a.withY(Double.NaN).isFinite()).isFalse();
        assertThat(a.withZ(Double.NaN).isFinite()).isFalse();
        assertThat(a.withW(Double.NaN).isFinite()).isFalse();
    }

    @Test
    void memory() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(64);
            a.write(segment, 8);
            assertThat(Vec4.read(segment, 8)).isEqualTo(a);
            a.writeFloats(segment, 4);
            assertThat(Vec4.readFloats(segment, 4)).isEqualTo(a);
        }
        ByteBuffer bytes = ByteBuffer.allocate(64);
        a.write(bytes, 8);
        assertThat(Vec4.read(bytes, 8)).isEqualTo(a);
        DoubleBuffer doubles = DoubleBuffer.allocate(8);
        a.write(doubles, 2);
        assertThat(Vec4.read(doubles, 2)).isEqualTo(a);
    }
}
