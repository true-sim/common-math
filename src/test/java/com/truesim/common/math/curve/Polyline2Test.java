package com.truesim.common.math.curve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class Polyline2Test {

    /** An L: 4 east, then 3 north (+y). */
    private static final Polyline2 L = new Polyline2(new double[] {0, 4, 4}, new double[] {0, 0, 3});

    @Test
    void lengthsSegmentsAndPoints() {
        assertThat(L.count()).isEqualTo(3);
        assertThat(L.length()).isEqualTo(7);
        assertThat(L.s).containsExactly(0, 4, 7);
        assertThat(L.segment(-1)).isZero();
        assertThat(L.segment(4)).isEqualTo(1);
        assertThat(L.segment(5)).isEqualTo(1);
        assertThat(L.segment(99)).isEqualTo(1);
        double[] p = new double[4];
        L.at(5, p);
        assertThat(p).containsExactly(4, 1, 0, 1);
        L.at(-5, p);
        assertThat(p).containsExactly(0, 0, 1, 0);
        assertThat(L.value(new double[] {10, 20, 50}, 5.5)).isEqualTo(35);
        assertThatThrownBy(() -> new Polyline2(new double[0], new double[0]))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Polyline2(new double[1], new double[2]))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void singlePointsAndRepeatedPoints() {
        Polyline2 point = new Polyline2(new double[] {2}, new double[] {3});
        double[] p = new double[4];
        point.at(1, p);
        assertThat(p).containsExactly(2, 3, 1, 0);
        assertThat(point.value(new double[] {7}, 1)).isEqualTo(7);
        assertThat(point.segment(0)).isZero();
        Polyline2 repeated = new Polyline2(new double[] {1, 1}, new double[] {1, 1});
        repeated.at(0, p);
        assertThat(p).containsExactly(1, 1, 1, 0);
        assertThat(repeated.value(new double[] {3, 5}, 0)).isEqualTo(3);
        double[] q = new double[3];
        assertThat(point.nearest(2, 7, q)).isZero();
        assertThat(q).containsExactly(2, 3, 4);
    }

    @Test
    void nearestPoints() {
        double[] q = new double[3];
        assertThat(L.nearest(6, 1, q)).isEqualTo(5);
        assertThat(q).containsExactly(4, 1, 2);
        assertThat(L.nearest(2, -1, q)).isEqualTo(2);
        assertThat(q).containsExactly(2, 0, 1);
    }

    @Test
    void cornersCutAndResampled() {
        Polyline2 cut = Polyline2.chaikin(L, 1);
        assertThat(cut.x).containsExactly(0, 3, 4, 4);
        assertThat(cut.y).containsExactly(0, 0, 0.75, 3);
        Polyline2 twice = Polyline2.chaikin(L, 2);
        assertThat(twice.count()).isEqualTo(6);
        assertThat(twice.x[0]).isZero();
        assertThat(twice.y[twice.count() - 1]).isEqualTo(3);
        // two points stay as they are
        Polyline2 straight = new Polyline2(new double[] {0, 1}, new double[] {0, 0});
        assertThat(Polyline2.chaikin(straight, 3).x).containsExactly(0, 1);
        Polyline2 even = L.resample(2);
        assertThat(even.count()).isEqualTo(5);
        assertThat(even.x[4]).isEqualTo(4);
        assertThat(even.y[4]).isEqualTo(3);
        assertThat(even.s[1]).isCloseTo(1.75, within(1e-12));
        assertThat(new Polyline2(new double[] {0, 0}, new double[] {0, 0}).resample(1).count()).isEqualTo(2);
        Polyline2 part = L.part(1, 2);
        assertThat(part.x).containsExactly(4, 4);
        assertThat(part.length()).isEqualTo(3);
    }
}
