package com.truesim.common.math.curve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.truesim.common.math.Vec3;
import org.junit.jupiter.api.Test;

class PolylineFramesTest {

    private static Vec3 at(double[] values, int i) {
        return new Vec3(values[3 * i], values[3 * i + 1], values[3 * i + 2]);
    }

    @Test
    void framesAlongAHelixAreOrthonormalAndMatchTheCurveVersion() {
        int count = 200;
        double[] points = new double[3 * count];
        for (int i = 0; i < count; i++) {
            double t = i * 0.05;
            points[3 * i] = Math.cos(t);
            points[3 * i + 1] = 0.3 * t;
            points[3 * i + 2] = Math.sin(t);
        }
        double[] tangents = new double[3 * count];
        double[] normals = new double[3 * count];
        PolylineFrames.compute(points, count, 0, 1, 0, tangents, normals);
        for (int i = 0; i < count; i++) {
            Vec3 t = at(tangents, i);
            Vec3 n = at(normals, i);
            assertThat(t.length()).isCloseTo(1, within(1e-12));
            assertThat(n.length()).isCloseTo(1, within(1e-12));
            assertThat(t.dot(n)).isCloseTo(0, within(1e-12));
        }
        // Rotation-minimizing: the normal turns no faster than the tangent does (no twist about the tangent)
        for (int i = 1; i < count; i++) {
            Vec3 b0 = at(tangents, i - 1).cross(at(normals, i - 1));
            Vec3 b1 = at(tangents, i).cross(at(normals, i));
            double twist = at(normals, i).dot(b0) - at(normals, i - 1).dot(b1);
            assertThat(Math.abs(twist)).isLessThan(1e-3);
        }
    }

    @Test
    void straightLinesKeepTheirNormalAndBendsTransportIt() {
        double[] points = {0, 0, 0, 0, 1, 0, 0, 2, 0, 1, 2, 0};
        double[] tangents = new double[12];
        double[] normals = new double[12];
        PolylineFrames.compute(points, 4, 0, 0, 1, tangents, normals);
        assertThat(at(normals, 0)).isEqualTo(new Vec3(0, 0, 1));
        assertThat(at(normals, 1)).isEqualTo(new Vec3(0, 0, 1));
        // Turning within the xy plane leaves the z normal alone
        assertThat(at(normals, 3).approxEquals(new Vec3(0, 0, 1), 1e-12)).isTrue();
        assertThat(at(tangents, 3)).isEqualTo(new Vec3(1, 0, 0));
        assertThat(at(tangents, 2).approxEquals(new Vec3(1, 1, 0).normalize(), 1e-12)).isTrue();
    }

    @Test
    void upAlongTheTangentRepeatedPointsAndSinglePoints() {
        double[] tangents = new double[9];
        double[] normals = new double[9];
        // Up parallel to a vertical line: another perpendicular is chosen
        PolylineFrames.compute(new double[] {0, 0, 0, 0, 1, 0, 0, 2, 0}, 3, 0, 1, 0, tangents, normals);
        assertThat(at(normals, 0).dot(at(tangents, 0))).isCloseTo(0, within(1e-12));
        assertThat(at(normals, 0).length()).isCloseTo(1, within(1e-12));
        // Up parallel to a line along x: the other axis is used
        PolylineFrames.compute(new double[] {0, 0, 0, 1, 0, 0}, 2, 1, 0, 0, tangents, normals);
        assertThat(at(normals, 0).dot(at(tangents, 0))).isCloseTo(0, within(1e-12));
        assertThat(at(normals, 0).length()).isCloseTo(1, within(1e-12));
        // A repeated point keeps the frame
        PolylineFrames.compute(new double[] {0, 0, 0, 0, 0, 0, 0, 1, 0}, 3, 1, 0, 0, tangents, normals);
        assertThat(at(normals, 1)).isEqualTo(at(normals, 0));
        assertThat(at(tangents, 0)).isEqualTo(new Vec3(0, 1, 0));
        // A single point: tangent +y
        PolylineFrames.compute(new double[] {5, 5, 5}, 1, 1, 0, 0, tangents, normals);
        assertThat(at(tangents, 0)).isEqualTo(new Vec3(0, 1, 0));
        assertThat(at(normals, 0)).isEqualTo(new Vec3(1, 0, 0));
        // A full reversal: the segments cancel at the turning point, which keeps the tangent before
        PolylineFrames.compute(new double[] {0, 0, 0, 1, 0, 0, 0, 0, 0}, 3, 0, 1, 0, tangents, normals);
        assertThat(at(tangents, 1)).isEqualTo(new Vec3(1, 0, 0));
        assertThatThrownBy(() -> PolylineFrames.compute(new double[0], 0, 0, 1, 0, tangents, normals))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
