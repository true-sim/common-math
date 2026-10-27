package com.truesim.common.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Quaternion, vector, scalar and projection additions. */
class ExtrasTest {

    private static final double EPS = 1e-12;

    @Test
    void yawPitchRollRoundTrip() {
        for (double[] angles : new double[][] {{0.3, -0.4, 1.1}, {-2.5, 1.2, -0.7}, {3.0, 0.0, 0.1}}) {
            Vec3 back = Quat.fromYawPitchRoll(angles[0], angles[1], angles[2]).toYawPitchRoll();
            assertThat(back.approxEquals(Vec3.of(angles[0], angles[1], angles[2]), 1e-9)).isTrue();
        }
        // Gimbal lock: yaw and roll merge; the rotation must still be reproduced.
        Quat locked = Quat.fromYawPitchRoll(0.5, Maths.HALF_PI, 0.3);
        Vec3 angles = locked.toYawPitchRoll();
        assertThat(angles.z()).isEqualTo(0.0);
        assertThat(Quat.fromYawPitchRoll(angles.x(), angles.y(), angles.z()).approxSameRotation(locked, 1e-6)).isTrue();
    }

    @Test
    void rotateTowards() {
        Quat target = Quat.fromAxisAngle(Vec3.UP, 1);
        assertThat(Quat.IDENTITY.rotateTowards(target, 0.25).angle()).isCloseTo(0.25, within(1e-12));
        assertThat(Quat.IDENTITY.rotateTowards(target, 2).approxSameRotation(target, EPS)).isTrue();
    }

    @Test
    void integrateAngularVelocity() {
        Quat spun = Quat.IDENTITY.integrate(Vec3.of(0, 2, 0), 0.25);
        assertThat(spun.approxEquals(Quat.fromAxisAngle(Vec3.UP, 0.5), EPS)).isTrue();
        assertThat(spun.integrate(Vec3.ZERO, 1).approxEquals(spun, EPS)).isTrue();
    }

    @Test
    void swingTwist() {
        Quat q = Quat.fromAxisAngle(Vec3.UNIT_X, 0.4).mul(Quat.fromAxisAngle(Vec3.UP, 0.9));
        Quat twist = q.twist(Vec3.UP);
        Quat swing = q.swing(Vec3.UP);
        assertThat(swing.mul(twist).approxSameRotation(q, EPS)).isTrue();
        assertThat(twist.axis().approxEquals(Vec3.UP, 1e-9)).isTrue();
        assertThat(Quat.of(1, 0, 0, 0).twist(Vec3.UP)).isEqualTo(Quat.IDENTITY); // no twist component at all
    }

    @Test
    void logAndExp() {
        Quat q = Quat.fromAxisAngle(Vec3.of(1, 2, 3), 1.3);
        assertThat(q.log().exp().approxEquals(q, EPS)).isTrue();
        assertThat(Quat.IDENTITY.log()).isEqualTo(Quat.of(0, 0, 0, 0));
        assertThat(Quat.of(0, 0, 0, 0).exp()).isEqualTo(Quat.IDENTITY);
    }

    @Test
    void vectorAdditions() {
        Vec3 down = Vec3.of(0, -1, 0);
        assertThat(down.refract(Vec3.UP, 1).approxEquals(down, EPS)).isTrue();
        Vec3 grazing = Vec3.of(1, -0.1, 0).normalize();
        assertThat(grazing.refract(Vec3.UP, 1.5)).isEqualTo(Vec3.ZERO); // total internal reflection
        assertThat(Vec3.UNIT_X.signedAngle(Vec3.UNIT_Z, Vec3.UP)).isCloseTo(-Maths.HALF_PI, within(EPS));
        assertThat(Vec3.UNIT_X.signedAngle(Vec3.UNIT_Z.negate(), Vec3.UP)).isCloseTo(Maths.HALF_PI, within(EPS));
        for (Vec3 v : new Vec3[] {Vec3.UNIT_X, Vec3.of(0.1, 1, 0.3)}) {
            Vec3 perpendicular = v.anyPerpendicular();
            assertThat(perpendicular.dot(v)).isCloseTo(0, within(EPS));
            assertThat(perpendicular.length()).isCloseTo(1, within(EPS));
        }
        assertThat(Vec3.ZERO.moveTowards(Vec3.of(10, 0, 0), 3)).isEqualTo(Vec3.of(3, 0, 0));
        assertThat(Vec3.ZERO.moveTowards(Vec3.of(1, 0, 0), 3)).isEqualTo(Vec3.of(1, 0, 0));
        assertThat(Vec2.ZERO.moveTowards(Vec2.of(0, 10), 4)).isEqualTo(Vec2.of(0, 4));
        assertThat(Vec2.ZERO.moveTowards(Vec2.of(0, 1), 4)).isEqualTo(Vec2.of(0, 1));
        assertThat(Vec2.of(1.5, -0.5).floor()).isEqualTo(Vec2i.of(1, -1));
        assertThat(Vec3.of(1.5, -0.5, 2).floor()).isEqualTo(Vec3i.of(1, -1, 2));
    }

    @Test
    void integerVectors() {
        Vec2i a = Vec2i.of(3, -4);
        assertThat(a.add(Vec2i.ONE)).isEqualTo(Vec2i.of(4, -3));
        assertThat(a.sub(Vec2i.UNIT_X)).isEqualTo(Vec2i.of(2, -4));
        assertThat(a.scale(2)).isEqualTo(Vec2i.of(6, -8));
        assertThat(a.negate()).isEqualTo(Vec2i.of(-3, 4));
        assertThat(a.dot(Vec2i.UNIT_Y)).isEqualTo(-4);
        assertThat(a.manhattanDistance(Vec2i.ZERO)).isEqualTo(7);
        assertThat(a.chebyshevDistance(Vec2i.ZERO)).isEqualTo(4);
        assertThat(a.min(Vec2i.ZERO)).isEqualTo(Vec2i.of(0, -4));
        assertThat(a.max(Vec2i.ZERO)).isEqualTo(Vec2i.of(3, 0));
        assertThat(a.toVec2()).isEqualTo(Vec2.of(3, -4));

        Vec3i b = Vec3i.of(1, -2, 3);
        assertThat(b.add(Vec3i.ONE)).isEqualTo(Vec3i.of(2, -1, 4));
        assertThat(b.sub(Vec3i.UNIT_X)).isEqualTo(Vec3i.of(0, -2, 3));
        assertThat(b.scale(3)).isEqualTo(Vec3i.of(3, -6, 9));
        assertThat(b.negate()).isEqualTo(Vec3i.of(-1, 2, -3));
        assertThat(b.dot(Vec3i.UNIT_Z.add(Vec3i.UNIT_Y))).isEqualTo(1);
        assertThat(b.manhattanDistance(Vec3i.ZERO)).isEqualTo(6);
        assertThat(b.chebyshevDistance(Vec3i.ZERO)).isEqualTo(3);
        assertThat(b.min(Vec3i.ZERO)).isEqualTo(Vec3i.of(0, -2, 0));
        assertThat(b.max(Vec3i.ZERO)).isEqualTo(Vec3i.of(1, 0, 3));
        assertThat(b.toVec3()).isEqualTo(Vec3.of(1, -2, 3));
    }

    @Test
    void scalarAdditions() {
        assertThat(Maths.fract(2.25)).isEqualTo(0.25);
        assertThat(Maths.fract(-0.25)).isEqualTo(0.75);
        assertThat(Maths.step(1, 0.5)).isEqualTo(0.0);
        assertThat(Maths.step(1, 1)).isEqualTo(1.0);
        assertThat(Maths.pingPong(1.5, 2)).isEqualTo(1.5);
        assertThat(Maths.pingPong(3, 2)).isEqualTo(1.0);
    }

    @Test
    void reversedZProjection() {
        Mat4 projection = Mat4.perspectiveInfiniteReversedZ(Maths.HALF_PI, 1, 0.1);
        assertThat(projection.project(Vec3.of(0, 0, -0.1)).z()).isCloseTo(1, within(EPS));
        assertThat(projection.project(Vec3.of(0, 0, -1e9)).z()).isCloseTo(0, within(1e-9));
    }
}
