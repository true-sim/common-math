package com.truesim.common.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import org.junit.jupiter.api.Test;

class Vec2Test {

    private final Vec2 a = Vec2.of(1, 2);
    private final Vec2 b = Vec2.of(4, -6);

    @Test
    void constantsAndFactories() {
        assertThat(Vec2.ZERO).isEqualTo(new Vec2(0, 0));
        assertThat(Vec2.ONE).isEqualTo(Vec2.splat(1));
        assertThat(Vec2.UNIT_X.perpendicular().approxEquals(Vec2.UNIT_Y, 0)).isTrue();
        assertThat(Vec2.fromAngle(Maths.HALF_PI).approxEquals(Vec2.UNIT_Y, 1e-15)).isTrue();
        assertThat(Vec2.LAYOUT.byteSize()).isEqualTo(16);
        assertThat(Vec2.FLOAT_LAYOUT.byteSize()).isEqualTo(8);
    }

    @Test
    void arithmetic() {
        assertThat(a.add(b)).isEqualTo(Vec2.of(5, -4));
        assertThat(a.add(1, 1)).isEqualTo(Vec2.of(2, 3));
        assertThat(a.sub(b)).isEqualTo(Vec2.of(-3, 8));
        assertThat(a.sub(1, 1)).isEqualTo(Vec2.of(0, 1));
        assertThat(a.mul(b)).isEqualTo(Vec2.of(4, -12));
        assertThat(b.div(a)).isEqualTo(Vec2.of(4, -3));
        assertThat(a.scale(2)).isEqualTo(Vec2.of(2, 4));
        assertThat(a.div(2)).isEqualTo(Vec2.of(0.5, 1));
        assertThat(a.negate()).isEqualTo(Vec2.of(-1, -2));
        assertThat(a.addScaled(b, 0.5)).isEqualTo(Vec2.of(3, -1));
    }

    @Test
    void productsAndLengths() {
        assertThat(a.dot(b)).isEqualTo(-8.0);
        assertThat(Vec2.UNIT_X.cross(Vec2.UNIT_Y)).isEqualTo(1.0);
        assertThat(Vec2.of(3, 4).length()).isEqualTo(5.0);
        assertThat(a.lengthSquared()).isEqualTo(5.0);
        assertThat(Vec2.ZERO.distance(Vec2.of(3, 4))).isEqualTo(5.0);
        assertThat(a.distanceSquared(b)).isEqualTo(73.0);
        assertThat(Vec2.of(3, 4).normalize().approxEquals(Vec2.of(0.6, 0.8), 1e-15)).isTrue();
        assertThat(Vec2.ZERO.normalize()).isEqualTo(Vec2.ZERO);
        assertThat(Vec2.of(3, 4).clampLength(10)).isEqualTo(Vec2.of(3, 4));
        assertThat(Vec2.of(3, 4).clampLength(1).approxEquals(Vec2.of(0.6, 0.8), 1e-15)).isTrue();
        assertThat(Vec2.UNIT_Y.angle()).isEqualTo(Maths.HALF_PI);
        assertThat(Vec2.UNIT_Y.angleTo(Vec2.UNIT_X)).isEqualTo(-Maths.HALF_PI);
        assertThat(Vec2.UNIT_X.rotate(Maths.HALF_PI).approxEquals(Vec2.UNIT_Y, 1e-15)).isTrue();
    }

    @Test
    void geometry() {
        assertThat(Vec2.of(1, -1).reflect(Vec2.UNIT_Y)).isEqualTo(Vec2.of(1, 1));
        assertThat(a.project(Vec2.of(0, 3))).isEqualTo(Vec2.of(0, 2));
        assertThat(a.project(Vec2.ZERO)).isEqualTo(Vec2.ZERO);
        assertThat(a.reject(Vec2.UNIT_Y)).isEqualTo(Vec2.of(1, 0));
        assertThat(a.interpolate(b, 0.5)).isEqualTo(Vec2.of(2.5, -2));
        assertThat(a.min(b)).isEqualTo(Vec2.of(1, -6));
        assertThat(a.max(b)).isEqualTo(Vec2.of(4, 2));
        assertThat(b.abs()).isEqualTo(Vec2.of(4, 6));
        assertThat(b.clamp(Vec2.ZERO, Vec2.splat(3))).isEqualTo(Vec2.of(3, 0));
    }

    @Test
    void componentsAndComparisons() {
        assertThat(a.withX(9)).isEqualTo(Vec2.of(9, 2));
        assertThat(a.withY(9)).isEqualTo(Vec2.of(1, 9));
        assertThat(a.yx()).isEqualTo(Vec2.of(2, 1));
        assertThat(a.extend(3)).isEqualTo(Vec3.of(1, 2, 3));
        assertThat(a.approxEquals(a.add(1e-9, 1e-9), 1e-6)).isTrue();
        assertThat(a.approxEquals(a.add(1, 0), 1e-6)).isFalse();
        assertThat(a.approxEquals(a.add(0, 1), 1e-6)).isFalse();
        assertThat(a.isFinite()).isTrue();
        assertThat(a.withX(Double.NaN).isFinite()).isFalse();
        assertThat(a.withY(Double.NaN).isFinite()).isFalse();
    }

    @Test
    void memory() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(64);
            a.write(segment, 8);
            assertThat(Vec2.read(segment, 8)).isEqualTo(a);
            a.writeFloats(segment, 4);
            assertThat(Vec2.readFloats(segment, 4)).isEqualTo(a);
        }
        ByteBuffer bytes = ByteBuffer.allocate(64);
        a.write(bytes, 8);
        assertThat(Vec2.read(bytes, 8)).isEqualTo(a);
        DoubleBuffer doubles = DoubleBuffer.allocate(8);
        a.write(doubles, 2);
        assertThat(Vec2.read(doubles, 2)).isEqualTo(a);
        assertThat(Maths.atan2(1, 1)).isCloseTo(Maths.PI / 4, within(1e-15));
    }
}
