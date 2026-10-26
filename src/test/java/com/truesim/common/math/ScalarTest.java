package com.truesim.common.math;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScalarTest {

    @Test
    void behavesLikeADouble() {
        Scalar two = Scalar.of(2);
        assertThat(Scalar.ZERO.value()).isEqualTo(0.0);
        assertThat(two.add(Scalar.ONE)).isEqualTo(Scalar.of(3));
        assertThat(two.sub(Scalar.ONE)).isEqualTo(Scalar.ONE);
        assertThat(two.scale(1.5)).isEqualTo(Scalar.of(3));
        assertThat(two.dot(two)).isEqualTo(4.0);
        assertThat(two.distance(Scalar.of(-1))).isEqualTo(3.0);
        assertThat(Scalar.ZERO.interpolate(two, 0.25)).isEqualTo(Scalar.of(0.5));
    }
}
