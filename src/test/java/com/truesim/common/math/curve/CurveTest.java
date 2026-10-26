package com.truesim.common.math.curve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.truesim.common.math.Vec2;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.color.Color;
import java.util.List;
import org.junit.jupiter.api.Test;

class CurveTest {

    private static final double EPS = 1e-12;

    @Test
    void quadraticBezier() {
        QuadraticBezier<Vec2> curve = new QuadraticBezier<>(Vec2.ZERO, Vec2.of(1, 2), Vec2.of(2, 0));
        assertThat(curve.at(0)).isEqualTo(Vec2.ZERO);
        assertThat(curve.at(0.5)).isEqualTo(Vec2.of(1, 1));
        assertThat(curve.at(1)).isEqualTo(Vec2.of(2, 0));
        assertThat(curve.derivative(0)).isEqualTo(Vec2.of(2, 4));
        assertThat(curve.derivative(0.5)).isEqualTo(Vec2.of(2, 0));
    }

    @Test
    void cubicBezier() {
        CubicBezier<Vec3> curve = new CubicBezier<>(Vec3.ZERO, Vec3.of(1, 1, 0), Vec3.of(2, 1, 0), Vec3.of(3, 0, 0));
        assertThat(curve.at(0)).isEqualTo(Vec3.ZERO);
        assertThat(curve.at(1)).isEqualTo(Vec3.of(3, 0, 0));
        assertThat(curve.at(0.5)).isEqualTo(Vec3.of(1.5, 0.75, 0));
        assertThat(curve.derivative(0)).isEqualTo(Vec3.of(3, 3, 0));
        CubicBezier<Vec3> left = curve.left(0.5);
        CubicBezier<Vec3> right = curve.right(0.5);
        assertThat(left.at(1).approxEquals(curve.at(0.5), EPS)).isTrue();
        assertThat(left.at(0.5).approxEquals(curve.at(0.25), EPS)).isTrue();
        assertThat(right.at(0.5).approxEquals(curve.at(0.75), EPS)).isTrue();
        assertThat(right.p3()).isEqualTo(curve.p3());
    }

    @Test
    void hermite() {
        Hermite<Vec2> curve = new Hermite<>(Vec2.ZERO, Vec2.of(1, 0), Vec2.of(1, 1), Vec2.of(0, 1));
        assertThat(curve.at(0)).isEqualTo(Vec2.ZERO);
        assertThat(curve.at(1)).isEqualTo(Vec2.of(1, 1));
        assertThat(curve.derivative(0)).isEqualTo(Vec2.of(1, 0));
        assertThat(curve.derivative(1)).isEqualTo(Vec2.of(0, 1));
    }

    @Test
    void catmullRomPassesThroughItsPoints() {
        List<Vec3> points = List.of(Vec3.ZERO, Vec3.of(1, 2, 0), Vec3.of(3, 2, 0), Vec3.of(4, 0, 1));
        for (Parametrization parametrization : List.of(Parametrization.UNIFORM, Parametrization.CENTRIPETAL, Parametrization.CHORDAL)) {
            CatmullRom<Vec3> open = CatmullRom.of(points, parametrization, false);
            assertThat(open.segments()).isEqualTo(3);
            for (int i = 0; i < points.size(); i++) {
                assertThat(open.at(i / 3.0).approxEquals(points.get(i), 1e-9)).as("%s point %d", parametrization, i).isTrue();
            }
            CatmullRom<Vec3> closed = CatmullRom.of(points, parametrization, true);
            assertThat(closed.segments()).isEqualTo(4);
            assertThat(closed.at(1).approxEquals(points.get(0), 1e-9)).isTrue();
        }
        assertThat(Parametrization.CENTRIPETAL.alpha()).isEqualTo(0.5);
    }

    @Test
    void catmullRomTangentsAndEdges() {
        List<Vec2> straight = List.of(Vec2.ZERO, Vec2.of(1, 0), Vec2.of(2, 0));
        CatmullRom<Vec2> line = CatmullRom.of(straight, Parametrization.UNIFORM, false);
        assertThat(line.derivative(0.5).approxEquals(Vec2.of(2, 0), EPS)).isTrue();
        assertThat(line.derivative(1).approxEquals(Vec2.of(2, 0), EPS)).isTrue();
        assertThat(line.at(2)).isEqualTo(Vec2.of(2, 0));
        assertThat(line.at(-1)).isEqualTo(Vec2.ZERO);
        assertThat(line.segment(0).p1()).isEqualTo(Vec2.of(1, 0));

        CatmullRom<Vec2> duplicates = CatmullRom.of(List.of(Vec2.ZERO, Vec2.ZERO, Vec2.ONE), Parametrization.CENTRIPETAL, false);
        assertThat(duplicates.at(1)).isEqualTo(Vec2.ONE);
        assertThatThrownBy(() -> CatmullRom.of(List.of(Vec2.ZERO), Parametrization.UNIFORM, false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void curvesWorkOnColors() {
        CatmullRom<Color> gradient = CatmullRom.of(List.of(Color.RED, Color.GREEN, Color.BLUE), Parametrization.UNIFORM, false);
        assertThat(gradient.at(0.5)).isEqualTo(Color.GREEN);
    }

    @Test
    void arcLength() {
        ArcLength<Vec2> line = ArcLength.of(new QuadraticBezier<>(Vec2.ZERO, Vec2.of(1, 0), Vec2.of(4, 0)), 64);
        assertThat(line.length()).isCloseTo(4, within(1e-9));
        assertThat(line.at(2).x()).isCloseTo(2, within(1e-3));
        assertThat(line.at(-1)).isEqualTo(Vec2.ZERO);
        assertThat(line.at(5)).isEqualTo(Vec2.of(4, 0));
        assertThat(line.curve().at(1)).isEqualTo(Vec2.of(4, 0));

        ArcLength<Vec2> uniform = ArcLength.of(new QuadraticBezier<>(Vec2.ZERO, Vec2.of(1, 0), Vec2.of(2, 0)), 4);
        assertThat(uniform.parameterAt(1)).isEqualTo(0.5); // exactly on a sample
        assertThat(uniform.parameterAt(0.25)).isEqualTo(0.125);
        assertThatThrownBy(() -> ArcLength.of(new QuadraticBezier<>(Vec2.ZERO, Vec2.ZERO, Vec2.ONE), 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
