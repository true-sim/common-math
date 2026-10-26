package com.truesim.common.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import org.junit.jupiter.api.Test;

class QuatTest {

    private static final double EPS = 1e-12;
    private final Quat quarterY = Quat.fromAxisAngle(Vec3.UNIT_Y, Maths.HALF_PI);

    @Test
    void axisAngle() {
        assertThat(quarterY.rotate(Vec3.FORWARD).approxEquals(Vec3.of(-1, 0, 0), EPS)).isTrue();
        assertThat(quarterY.angle()).isCloseTo(Maths.HALF_PI, within(EPS));
        assertThat(quarterY.axis().approxEquals(Vec3.UNIT_Y, EPS)).isTrue();
        assertThat(Quat.fromAxisAngle(Vec3.UNIT_Y, -Maths.HALF_PI).axis().approxEquals(Vec3.UNIT_Y.negate(), EPS)).isTrue();
        assertThat(new Quat(0, -0.7071067811865476, 0, -0.7071067811865476).axis().approxEquals(Vec3.UNIT_Y, EPS)).isTrue();
        assertThat(Quat.IDENTITY.axis()).isEqualTo(Vec3.UNIT_X);
        assertThat(Quat.of(0, 0, 0, 1)).isEqualTo(Quat.IDENTITY);
    }

    @Test
    void yawPitchRoll() {
        Quat q = Quat.fromYawPitchRoll(Maths.HALF_PI, 0, 0);
        assertThat(q.approxEquals(quarterY, EPS)).isTrue();
        Quat pitched = Quat.fromYawPitchRoll(0, Maths.HALF_PI, 0);
        assertThat(pitched.forward().approxEquals(Vec3.UP, EPS)).isTrue();
        Quat rolled = Quat.fromYawPitchRoll(0, 0, Maths.HALF_PI);
        assertThat(rolled.right().approxEquals(Vec3.UP, EPS)).isTrue();
        assertThat(rolled.up().approxEquals(Vec3.UNIT_X.negate(), EPS)).isTrue();
    }

    @Test
    void fromTo() {
        assertThat(Quat.fromTo(Vec3.UNIT_X, Vec3.UNIT_X.scale(3))).isEqualTo(Quat.IDENTITY);
        assertThat(Quat.fromTo(Vec3.UNIT_X, Vec3.UNIT_Y).rotate(Vec3.UNIT_X).approxEquals(Vec3.UNIT_Y, EPS)).isTrue();
        assertThat(Quat.fromTo(Vec3.UNIT_Z, Vec3.UNIT_Z.negate()).rotate(Vec3.UNIT_Z).approxEquals(Vec3.UNIT_Z.negate(), EPS))
            .isTrue();
        assertThat(Quat.fromTo(Vec3.UNIT_X, Vec3.UNIT_X.negate()).rotate(Vec3.UNIT_X).approxEquals(Vec3.UNIT_X.negate(), EPS))
            .isTrue();
    }

    @Test
    void lookRotation() {
        Quat q = Quat.lookRotation(Vec3.UNIT_X, Vec3.UP);
        assertThat(q.forward().approxEquals(Vec3.UNIT_X, EPS)).isTrue();
        assertThat(q.up().approxEquals(Vec3.UP, EPS)).isTrue();
        Quat down = Quat.lookRotation(Vec3.UP.negate(), Vec3.UP);
        assertThat(down.forward().approxEquals(Vec3.UP.negate(), EPS)).isTrue();
        Quat sideways = Quat.lookRotation(Vec3.UNIT_X, Vec3.UNIT_X);
        assertThat(sideways.forward().approxEquals(Vec3.UNIT_X, EPS)).isTrue();
    }

    @Test
    void fromRotationMatrixAllBranches() {
        for (Quat q : new Quat[] {
            Quat.fromAxisAngle(Vec3.UNIT_X, 0.3),
            Quat.fromAxisAngle(Vec3.UNIT_X, Maths.PI),
            Quat.fromAxisAngle(Vec3.UNIT_Y, Maths.PI),
            Quat.fromAxisAngle(Vec3.UNIT_Z, Maths.PI),
            Quat.fromAxisAngle(Vec3.of(0.6, 0, 0.8), Maths.PI)}) {
            assertThat(Mat3.rotation(q).extractRotation().approxSameRotation(q, EPS)).isTrue();
        }
    }

    @Test
    void algebra() {
        Quat q = Quat.fromYawPitchRoll(0.3, -0.7, 1.1);
        assertThat(q.mul(q.inverse()).approxEquals(Quat.IDENTITY, EPS)).isTrue();
        assertThat(q.mul(q.conjugate()).approxEquals(Quat.IDENTITY, EPS)).isTrue();
        assertThat(Quat.of(0, 0, 0, 2).inverse().approxEquals(Quat.of(0, 0, 0, 0.5), EPS)).isTrue();
        assertThat(q.length()).isCloseTo(1, within(EPS));
        assertThat(q.lengthSquared()).isCloseTo(1, within(EPS));
        assertThat(q.dot(q)).isCloseTo(1, within(EPS));
        assertThat(Quat.of(0, 0, 0, 2).normalize()).isEqualTo(Quat.IDENTITY);
        assertThat(Quat.of(0, 0, 0, 0).normalize()).isEqualTo(Quat.IDENTITY);
        assertThat(quarterY.angleTo(Quat.IDENTITY)).isCloseTo(Maths.HALF_PI, within(EPS));
        Vec3 v = Vec3.of(1, 2, 3);
        assertThat(q.rotate(v).approxEquals(Mat3.rotation(q).transform(v), EPS)).isTrue();
    }

    @Test
    void interpolation() {
        Quat half = Quat.IDENTITY.slerp(quarterY, 0.5);
        assertThat(half.angle()).isCloseTo(Maths.HALF_PI / 2, within(EPS));
        assertThat(Quat.IDENTITY.interpolate(quarterY, 0.5)).isEqualTo(half);
        Quat negated = Quat.of(-quarterY.x(), -quarterY.y(), -quarterY.z(), -quarterY.w());
        assertThat(Quat.IDENTITY.slerp(negated, 0.5).approxSameRotation(half, EPS)).isTrue();
        Quat tiny = Quat.fromAxisAngle(Vec3.UNIT_Y, 1e-4);
        assertThat(Quat.IDENTITY.slerp(tiny, 0.5).angle()).isCloseTo(0.5e-4, within(1e-9));
        assertThat(Quat.IDENTITY.nlerp(quarterY, 1).approxEquals(quarterY, EPS)).isTrue();
        assertThat(Quat.IDENTITY.nlerp(negated, 1).approxSameRotation(quarterY, EPS)).isTrue();
    }

    @Test
    void comparisons() {
        Quat q = Quat.of(0.1, 0.2, 0.3, 0.4);
        assertThat(q.approxEquals(Quat.of(1, 0.2, 0.3, 0.4), EPS)).isFalse();
        assertThat(q.approxEquals(Quat.of(0.1, 1, 0.3, 0.4), EPS)).isFalse();
        assertThat(q.approxEquals(Quat.of(0.1, 0.2, 1, 0.4), EPS)).isFalse();
        assertThat(q.approxEquals(Quat.of(0.1, 0.2, 0.3, 1), EPS)).isFalse();
        assertThat(q.approxSameRotation(Quat.of(-0.1, -0.2, -0.3, -0.4), EPS)).isTrue();
        assertThat(q.approxSameRotation(Quat.IDENTITY, EPS)).isFalse();
    }

    @Test
    void memory() {
        Quat q = Quat.of(0.5, -0.25, 0.125, 0.75);
        assertThat(Quat.LAYOUT.byteSize()).isEqualTo(32);
        assertThat(Quat.FLOAT_LAYOUT.byteSize()).isEqualTo(16);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(64);
            q.write(segment, 8);
            assertThat(Quat.read(segment, 8)).isEqualTo(q);
            q.writeFloats(segment, 4);
            assertThat(Quat.readFloats(segment, 4)).isEqualTo(q);
        }
        ByteBuffer bytes = ByteBuffer.allocate(64);
        q.write(bytes, 8);
        assertThat(Quat.read(bytes, 8)).isEqualTo(q);
        DoubleBuffer doubles = DoubleBuffer.allocate(8);
        q.write(doubles, 2);
        assertThat(Quat.read(doubles, 2)).isEqualTo(q);
    }
}
