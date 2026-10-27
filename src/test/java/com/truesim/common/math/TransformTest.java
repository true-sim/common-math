package com.truesim.common.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.lang.foreign.Arena;
import org.junit.jupiter.api.Test;

class TransformTest {

    private static final double EPS = 1e-12;
    private final Quat rotation = Quat.fromYawPitchRoll(0.4, -0.3, 0.2);
    private final Transform parent = Transform.of(Vec3.of(1, 2, 3), rotation, Vec3.splat(2));
    private final Transform child = Transform.of(Vec3.of(-1, 0, 4), Quat.fromAxisAngle(Vec3.UP, 0.7), Vec3.splat(0.5));

    @Test
    void factories() {
        assertThat(Transform.of(Vec3.ONE, rotation).scale()).isEqualTo(Vec3.ONE);
        assertThat(Transform.ofTranslation(Vec3.ONE).transformPoint(Vec3.ZERO)).isEqualTo(Vec3.ONE);
        assertThat(Transform.ofRotation(rotation).rotation()).isEqualTo(rotation);
        assertThat(Transform.fromMatrix(parent.toMat4()).approxEquals(parent, EPS)).isTrue();
        assertThat(Transform.IDENTITY.transformPoint(Vec3.of(1, 2, 3))).isEqualTo(Vec3.of(1, 2, 3));
        assertThat(Transform.LAYOUT.byteSize()).isEqualTo(80);
    }

    @Test
    void applicationMatchesMatrix() {
        Vec3 p = Vec3.of(0.3, -1, 2);
        assertThat(parent.transformPoint(p).approxEquals(parent.toMat4().transformPoint(p), EPS)).isTrue();
        assertThat(parent.transformDirection(p).approxEquals(parent.toMat4().transformDirection(p), EPS)).isTrue();
        assertThat(parent.inverseTransformPoint(parent.transformPoint(p)).approxEquals(p, EPS)).isTrue();
        assertThat(parent.forward().approxEquals(rotation.forward(), EPS)).isTrue();
    }

    @Test
    void compositionAndInverse() {
        assertThat(parent.mul(child).toMat4().approxEquals(parent.toMat4().mul(child.toMat4()), EPS)).isTrue();
        assertThat(parent.mul(parent.inverse()).approxEquals(Transform.IDENTITY, EPS)).isTrue();
        assertThat(parent.inverse().toMat4().approxEquals(parent.toMat4().inverse(), EPS)).isTrue();
    }

    @Test
    void withersInterpolationAndComparison() {
        assertThat(parent.withTranslation(Vec3.ZERO).translation()).isEqualTo(Vec3.ZERO);
        assertThat(parent.withRotation(Quat.IDENTITY).rotation()).isEqualTo(Quat.IDENTITY);
        assertThat(parent.withScale(Vec3.ONE).scale()).isEqualTo(Vec3.ONE);
        Transform half = Transform.IDENTITY.interpolate(parent, 0.5);
        assertThat(half.translation().approxEquals(Vec3.of(0.5, 1, 1.5), EPS)).isTrue();
        assertThat(half.scale().approxEquals(Vec3.splat(1.5), EPS)).isTrue();
        assertThat(parent.approxEquals(parent.withTranslation(Vec3.ZERO), EPS)).isFalse();
        assertThat(parent.approxEquals(parent.withRotation(Quat.IDENTITY), EPS)).isFalse();
        assertThat(parent.approxEquals(parent.withScale(Vec3.ONE), EPS)).isFalse();
    }

    @Test
    void transformArray() {
        try (Arena arena = Arena.ofConfined()) {
            for (TransformArray array : new TransformArray[] {TransformArray.onHeap(3), TransformArray.allocate(arena, 3)}) {
                assertThat(array.get(2)).isEqualTo(Transform.IDENTITY);
                array.set(1, parent);
                assertThat(TransformArray.wrap(array.segment()).get(1)).isEqualTo(parent);
            }
        }
    }

    @Test
    void transformArrayWorksInMemory() {
        TransformArray world = TransformArray.onHeap(3);
        world.set(0, parent);
        world.set(1, child);
        world.setProduct(2, world, 0, world, 1);
        assertThat(world.get(2).approxEquals(parent.mul(child), EPS)).isTrue();
        world.setProduct(0, world, 0, world, 0); // target aliases both inputs
        assertThat(world.get(0).approxEquals(parent.mul(parent), EPS)).isTrue();
        Vec3Array points = Vec3Array.onHeap(3);
        points.fill(Vec3.of(1, 2, 3));
        world.transformPoints(1, points, 1, 1);
        assertThat(points.get(0)).isEqualTo(Vec3.of(1, 2, 3));
        assertThat(points.get(1).approxEquals(child.transformPoint(Vec3.of(1, 2, 3)), EPS)).isTrue();
        assertThat(points.get(2)).isEqualTo(Vec3.of(1, 2, 3));
    }

    @Test
    void transform2D() {
        Transform2D a = Transform2D.of(Vec2.of(1, 2), 0.5, Vec2.splat(2));
        Transform2D b = Transform2D.of(Vec2.of(-3, 1), -1.2);
        Vec2 p = Vec2.of(0.7, -0.4);
        assertThat(Transform2D.IDENTITY.transformPoint(p)).isEqualTo(p);
        assertThat(a.transformPoint(p).approxEquals(a.toMat3().transformPoint2D(p), EPS)).isTrue();
        assertThat(a.transformDirection(p).approxEquals(a.toMat3().transformDirection2D(p), EPS)).isTrue();
        assertThat(a.inverseTransformPoint(a.transformPoint(p)).approxEquals(p, EPS)).isTrue();
        assertThat(a.mul(b).transformPoint(p).approxEquals(a.transformPoint(b.transformPoint(p)), EPS)).isTrue();
        assertThat(a.mul(a.inverse()).approxEquals(Transform2D.IDENTITY, EPS)).isTrue();
        Transform2D half = Transform2D.of(Vec2.ZERO, 3).interpolate(Transform2D.of(Vec2.ONE, -3), 0.5);
        assertThat(half.rotation()).isCloseTo(Maths.PI, within(1e-9)); // shortest arc, through pi
        assertThat(a.approxEquals(Transform2D.of(Vec2.ZERO, 0.5, Vec2.splat(2)), EPS)).isFalse();
        assertThat(a.approxEquals(Transform2D.of(Vec2.of(1, 2), 0.6, Vec2.splat(2)), EPS)).isFalse();
        assertThat(a.approxEquals(Transform2D.of(Vec2.of(1, 2), 0.5, Vec2.ONE), EPS)).isFalse();
    }
}
