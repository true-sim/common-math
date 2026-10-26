package com.truesim.common.math.interpolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class EasingTest {

    private static final List<Easing> ALL = List.of(
        Easing.LINEAR,
        Easing.QUAD_IN, Easing.QUAD_OUT, Easing.QUAD_IN_OUT,
        Easing.CUBIC_IN, Easing.CUBIC_OUT, Easing.CUBIC_IN_OUT,
        Easing.QUART_IN, Easing.QUART_OUT, Easing.QUART_IN_OUT,
        Easing.QUINT_IN, Easing.QUINT_OUT, Easing.QUINT_IN_OUT,
        Easing.SINE_IN, Easing.SINE_OUT, Easing.SINE_IN_OUT,
        Easing.EXPO_IN, Easing.EXPO_OUT, Easing.EXPO_IN_OUT,
        Easing.CIRC_IN, Easing.CIRC_OUT, Easing.CIRC_IN_OUT,
        Easing.BACK_IN, Easing.BACK_OUT, Easing.BACK_IN_OUT,
        Easing.ELASTIC_IN, Easing.ELASTIC_OUT, Easing.ELASTIC_IN_OUT,
        Easing.BOUNCE_IN, Easing.BOUNCE_OUT, Easing.BOUNCE_IN_OUT);

    @Test
    void allEasingsStartAtZeroAndEndAtOne() {
        for (Easing easing : ALL) {
            assertThat(easing.apply(0)).isCloseTo(0, within(1e-3));
            assertThat(easing.apply(1)).isCloseTo(1, within(1e-3));
            for (double t = 0.05; t < 1; t += 0.1) {
                assertThat(easing.apply(t)).isBetween(-0.5, 1.5);
            }
        }
    }

    @Test
    void inOutEasingsAreSymmetric() {
        for (Easing easing : List.of(Easing.QUAD_IN_OUT, Easing.CUBIC_IN_OUT, Easing.QUART_IN_OUT, Easing.QUINT_IN_OUT,
            Easing.SINE_IN_OUT, Easing.EXPO_IN_OUT, Easing.CIRC_IN_OUT, Easing.BACK_IN_OUT, Easing.BOUNCE_IN_OUT)) {
            assertThat(easing.apply(0.5)).isCloseTo(0.5, within(1e-9));
            assertThat(easing.apply(0.25) + easing.apply(0.75)).isCloseTo(1, within(1e-9));
        }
        assertThat(Easing.ELASTIC_IN_OUT.apply(0.25) + Easing.ELASTIC_IN_OUT.apply(0.75)).isCloseTo(1, within(1e-9));
    }

    @Test
    void characteristicShapes() {
        assertThat(Easing.BACK_IN.apply(0.2)).isNegative();
        assertThat(Easing.BACK_OUT.apply(0.8)).isGreaterThan(1);
        assertThat(Easing.ELASTIC_OUT.apply(0.2)).isGreaterThan(1);
        assertThat(Easing.BOUNCE_OUT.apply(0.3)).isCloseTo(7.5625 * 0.09, within(1e-12));
        assertThat(Easing.BOUNCE_OUT.apply(0.5)).isCloseTo(0.765625, within(1e-12));
        assertThat(Easing.BOUNCE_OUT.apply(0.85)).isLessThan(1);
        assertThat(Easing.BOUNCE_OUT.apply(0.95)).isLessThan(1);
        assertThat(Easings.quadIn(0.5)).isEqualTo(0.25);
        assertThat(Easings.linear(0.3)).isEqualTo(0.3);
    }

    @Test
    void cubicBezierMatchesCss() {
        Easing ease = Easing.cubicBezier(0.25, 0.1, 0.25, 1.0); // CSS "ease"
        assertThat(ease.apply(0)).isEqualTo(0.0);
        assertThat(ease.apply(1)).isCloseTo(1, within(1e-12));
        assertThat(ease.apply(0.5)).isCloseTo(0.8024, within(1e-3));
        assertThat(Easing.cubicBezier(0, 0, 1, 1).apply(0.3)).isCloseTo(0.3, within(1e-9));
        assertThatThrownBy(() -> Easing.cubicBezier(-0.1, 0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Easing.cubicBezier(1.1, 0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Easing.cubicBezier(0, 0, -1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Easing.cubicBezier(0, 0, 2, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void steps() {
        Easing steps = Easing.steps(4);
        assertThat(steps.apply(0.3)).isEqualTo(0.25);
        assertThat(steps.apply(0.99)).isEqualTo(0.75);
        assertThat(steps.apply(1)).isEqualTo(1.0);
        assertThat(steps.apply(-1)).isEqualTo(0.0);
        assertThatThrownBy(() -> Easing.steps(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void combinators() {
        assertThat(Easing.QUAD_IN.reversed().apply(0.3)).isCloseTo(Easing.QUAD_OUT.apply(0.3), within(1e-12));
        Easing mirrored = Easing.CUBIC_IN.mirrored();
        assertThat(mirrored.apply(0.2)).isCloseTo(Easing.CUBIC_IN_OUT.apply(0.2), within(1e-12));
        assertThat(mirrored.apply(0.8)).isCloseTo(Easing.CUBIC_IN_OUT.apply(0.8), within(1e-12));
    }
}
