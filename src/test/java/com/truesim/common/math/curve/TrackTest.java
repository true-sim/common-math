package com.truesim.common.math.curve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.truesim.common.math.Maths;
import com.truesim.common.math.Quat;
import com.truesim.common.math.Vec2;
import com.truesim.common.math.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackTest {

    private static final double EPS = 1e-9;

    /** A helix around Y: twists continuously, so naive frames would roll. */
    private static final Curve<Vec3> HELIX = new Curve<>() {
        @Override
        public Vec3 at(double t) {
            double a = t * Maths.TAU;
            return Vec3.of(Maths.cos(a), t, Maths.sin(a));
        }

        @Override
        public Vec3 derivative(double t) {
            double a = t * Maths.TAU;
            return Vec3.of(-Maths.sin(a) * Maths.TAU, 1, Maths.cos(a) * Maths.TAU);
        }
    };

    @Test
    void nearestPointOnCurve() {
        ArcLength<Vec2> line = ArcLength.of(new QuadraticBezier<>(Vec2.ZERO, Vec2.of(5, 0), Vec2.of(10, 0)), 20);
        assertThat(line.nearestDistance(Vec2.of(3.3, 2))).isCloseTo(3.3, within(EPS));
        assertThat(line.nearestPoint(Vec2.of(-4, 1))).isEqualTo(Vec2.ZERO);
        assertThat(line.nearestDistance(Vec2.of(14, -1))).isCloseTo(10, within(EPS));
        ArcLength<Vec2> point = ArcLength.of(new QuadraticBezier<>(Vec2.ONE, Vec2.ONE, Vec2.ONE), 4);
        assertThat(point.nearestDistance(Vec2.ZERO)).isEqualTo(0.0);
    }

    @Test
    void rotationMinimizingFramesAreOrthonormalAndDoNotTwist() {
        RotationMinimizingFrames frames = RotationMinimizingFrames.of(HELIX, 512, Vec3.UP);
        assertThat(frames.curve()).isSameAs(HELIX);
        Frame previous = frames.at(0);
        for (int i = 1; i <= 100; i++) {
            Frame frame = frames.at(i / 100.0);
            assertThat(frame.forward().length()).isCloseTo(1, within(EPS));
            assertThat(frame.up().length()).isCloseTo(1, within(EPS));
            assertThat(frame.forward().dot(frame.up())).isCloseTo(0, within(EPS));
            assertThat(frame.right().approxEquals(frame.forward().cross(frame.up()), EPS)).isTrue();
            // Rotation-minimizing: the up vector changes no faster than the curve bends.
            assertThat(frame.up().angle(previous.up())).isLessThan(0.1);
            previous = frame;
        }
        assertThat(frames.at(2).position().approxEquals(HELIX.at(1), EPS)).isTrue();
    }

    @Test
    void framesOnDegenerateCurves() {
        Curve<Vec3> line = new CubicBezier<>(Vec3.ZERO, Vec3.UNIT_X, Vec3.UNIT_X.scale(2), Vec3.UNIT_X.scale(3));
        Frame start = RotationMinimizingFrames.of(line, 4, Vec3.UNIT_X).at(0); // up parallel to the curve
        assertThat(start.up().dot(Vec3.UNIT_X)).isCloseTo(0, within(EPS));

        Curve<Vec3> standing = new Curve<>() {
            @Override
            public Vec3 at(double t) {
                return t < 0.5 ? Vec3.ZERO : Vec3.UNIT_Y; // does not move during the first steps
            }

            @Override
            public Vec3 derivative(double t) {
                return Vec3.UNIT_X; // and moves sideways to its tangent later
            }
        };
        Frame frame = RotationMinimizingFrames.of(standing, 4, Vec3.UP).at(1);
        assertThat(frame.up().approxEquals(Vec3.UP.negate(), EPS)).isTrue();
        assertThatThrownBy(() -> RotationMinimizingFrames.of(line, 0, Vec3.UP)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void frameOrientationAndBanking() {
        Frame frame = new Frame(Vec3.ZERO, Vec3.UNIT_X, Vec3.UP, Vec3.UNIT_X.cross(Vec3.UP));
        Quat orientation = frame.orientation();
        assertThat(orientation.forward().approxEquals(Vec3.UNIT_X, EPS)).isTrue();
        assertThat(orientation.up().approxEquals(Vec3.UP, EPS)).isTrue();
        Frame banked = frame.banked(Maths.HALF_PI);
        assertThat(banked.up().approxEquals(Vec3.UNIT_Z, EPS)).isTrue();
        assertThat(banked.forward()).isEqualTo(Vec3.UNIT_X);
    }

    @Test
    void quatSplinePassesThroughKeysSmoothly() {
        List<Quat> keys = List.of(
            Quat.IDENTITY,
            Quat.fromAxisAngle(Vec3.UP, 1),
            negated(Quat.fromAxisAngle(Vec3.UNIT_X, 1)), // opposite hemisphere: same rotation
            Quat.fromYawPitchRoll(2, 0.5, 0.3));
        QuatSpline open = QuatSpline.of(keys, false);
        assertThat(open.segments()).isEqualTo(3);
        for (int i = 0; i < keys.size(); i++) {
            assertThat(open.at(i / 3.0).approxSameRotation(keys.get(i), 1e-9)).as("key %d", i).isTrue();
        }
        // No jump in angular velocity at a key: the step before and after it turn by about the same angle.
        double h = 1e-4;
        double before = open.at(1 / 3.0 - h).angleTo(open.at(1 / 3.0));
        double after = open.at(1 / 3.0).angleTo(open.at(1 / 3.0 + h));
        assertThat(after / before).isCloseTo(1, within(0.01));

        QuatSpline closed = QuatSpline.of(keys, true);
        assertThat(closed.segments()).isEqualTo(4);
        assertThat(closed.at(1).approxSameRotation(keys.get(0), 1e-9)).isTrue();
        assertThat(closed.at(2).approxSameRotation(keys.get(0), 1e-9)).isTrue();
        assertThatThrownBy(() -> QuatSpline.of(List.of(Quat.IDENTITY), false)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Quat negated(Quat q) {
        return Quat.of(-q.x(), -q.y(), -q.z(), -q.w());
    }
}
