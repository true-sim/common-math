package com.truesim.common.math.interpolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.truesim.common.math.Maths;
import com.truesim.common.math.Quat;
import com.truesim.common.math.Scalar;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.color.Color;
import org.junit.jupiter.api.Test;

class AnimationTest {

    @Test
    void tween() {
        Tween<Vec3> move = Tween.of(Vec3.ZERO, Vec3.of(10, 0, 0), 2);
        assertThat(move.valueAt(-1)).isEqualTo(Vec3.ZERO);
        assertThat(move.valueAt(1)).isEqualTo(Vec3.of(5, 0, 0));
        assertThat(move.valueAt(5)).isEqualTo(Vec3.of(10, 0, 0));
        assertThat(move.progress(0.5)).isEqualTo(0.25);
        assertThat(move.isFinished(1)).isFalse();
        assertThat(move.isFinished(2)).isTrue();
        assertThat(move.reversed().valueAt(0)).isEqualTo(Vec3.of(10, 0, 0));

        Quat target = Quat.fromAxisAngle(Vec3.UP, Maths.HALF_PI);
        Tween<Quat> turn = Tween.of(Quat.IDENTITY, target, 1, Easing.SINE_IN_OUT);
        assertThat(turn.valueAt(1).approxEquals(target, 1e-12)).isTrue();
        assertThat(turn.easing()).isEqualTo(Easing.SINE_IN_OUT);

        Tween<Scalar> instant = Tween.of(Scalar.ZERO, Scalar.ONE, 0);
        assertThat(instant.valueAt(0)).isEqualTo(Scalar.ONE);
        assertThatThrownBy(() -> Tween.of(Scalar.ZERO, Scalar.ONE, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tween.of(Scalar.ZERO, Scalar.ONE, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keyframes() {
        Keyframes<Color> sky = Keyframes.<Color>builder()
            .key(0, Color.BLACK)
            .key(10, Color.WHITE, Easing.QUAD_IN)
            .key(20, Color.RED)
            .build();
        assertThat(sky.size()).isEqualTo(3);
        assertThat(sky.startTime()).isEqualTo(0.0);
        assertThat(sky.endTime()).isEqualTo(20.0);
        assertThat(sky.time(1)).isEqualTo(10.0);
        assertThat(sky.value(2)).isEqualTo(Color.RED);
        assertThat(sky.valueAt(-5)).isEqualTo(Color.BLACK);
        assertThat(sky.valueAt(25)).isEqualTo(Color.RED);
        assertThat(sky.valueAt(10)).isEqualTo(Color.WHITE);
        assertThat(sky.valueAt(5).r()).isCloseTo(0.25, within(1e-12)); // quad-in at t = 0.5
        assertThat(sky.valueAt(15).g()).isCloseTo(0.5, within(1e-12));

        Keyframes<Scalar> single = Keyframes.<Scalar>builder().key(1, Scalar.ONE).build();
        assertThat(single.valueAt(0)).isEqualTo(Scalar.ONE);
        assertThat(single.valueAt(2)).isEqualTo(Scalar.ONE);

        assertThatThrownBy(() -> Keyframes.<Scalar>builder().key(1, Scalar.ONE).key(1, Scalar.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Keyframes.<Scalar>builder().build()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void springConvergesWithoutOvershoot() {
        Spring spring = new Spring(0.5);
        Motion<Vec3> motion = Motion.at(Vec3.ZERO);
        assertThat(motion.velocity()).isEqualTo(Vec3.ZERO);
        Vec3 target = Vec3.of(10, 0, 0);
        double previous = 0;
        for (int i = 0; i < 300; i++) {
            motion = spring.step(motion, target, 1 / 60.0);
            assertThat(motion.value().x()).isBetween(previous, 10.0);
            previous = motion.value().x();
        }
        assertThat(motion.value().x()).isCloseTo(10, within(1e-3));
        assertThat(spring.smoothTime()).isEqualTo(0.5);
        assertThatThrownBy(() -> new Spring(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
