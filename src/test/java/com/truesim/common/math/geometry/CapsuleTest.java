package com.truesim.common.math.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.truesim.common.math.Mat4;
import com.truesim.common.math.Quat;
import com.truesim.common.math.Transform;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.random.Rng;
import java.lang.foreign.Arena;
import org.junit.jupiter.api.Test;

class CapsuleTest {

    private static final double MISS = Double.POSITIVE_INFINITY;
    private final Capsule capsule = new Capsule(Vec3.ZERO, Vec3.of(0, 2, 0), 0.5);

    @Test
    void segmentSegmentDistance() {
        Segment x = new Segment(Vec3.of(-1, 0, 0), Vec3.of(1, 0, 0));
        assertThat(x.distance(new Segment(Vec3.of(0, 1, -1), Vec3.of(0, 1, 1)))).isEqualTo(1.0); // crossing
        assertThat(x.distance(new Segment(Vec3.of(-1, 2, 0), Vec3.of(1, 2, 0)))).isEqualTo(2.0); // parallel
        assertThat(x.distance(new Segment(Vec3.of(3, 0, 0), Vec3.of(5, 0, 0)))).isEqualTo(2.0); // collinear, apart
        assertThat(x.distance(new Segment(Vec3.of(0, 3, 0), Vec3.of(0, 3, 0)))).isEqualTo(3.0); // second is a point
        assertThat(new Segment(Vec3.ONE, Vec3.ONE).distance(x)).isCloseTo(Math.sqrt(2), within(1e-12)); // first a point
        assertThat(new Segment(Vec3.ONE, Vec3.ONE).distance(new Segment(Vec3.ZERO, Vec3.ZERO)))
            .isCloseTo(Math.sqrt(3), within(1e-12)); // both points
        assertThat(x.distance(new Segment(Vec3.of(0, 1, 1), Vec3.of(0, 1, 5)))).isCloseTo(Math.sqrt(2), within(1e-12)); // t < 0
        assertThat(x.distance(new Segment(Vec3.of(0, 1, -5), Vec3.of(0, 1, -1)))).isCloseTo(Math.sqrt(2), within(1e-12)); // t > 1

        // Against dense sampling of the first segment with exact distances to the second.
        Rng rng = Rng.seeded(21);
        for (int i = 0; i < 2_000; i++) {
            Segment a = new Segment(rng.nextVec3InSphere().scale(3), rng.nextVec3InSphere().scale(3));
            Segment b = new Segment(rng.nextVec3InSphere().scale(3), rng.nextVec3InSphere().scale(3));
            double reference = Double.POSITIVE_INFINITY;
            for (int k = 0; k <= 400; k++) {
                reference = Math.min(reference, b.distance(a.at(k / 400.0)));
            }
            double distance = a.distance(b);
            assertThat(distance).isLessThanOrEqualTo(reference + 1e-12);
            assertThat(distance).isGreaterThan(reference - 0.02);
        }
    }

    @Test
    void basics() {
        assertThat(capsule.axis().length()).isEqualTo(2.0);
        assertThat(capsule.bounds()).isEqualTo(new Aabb(Vec3.of(-0.5, -0.5, -0.5), Vec3.of(0.5, 2.5, 0.5)));
        assertThat(capsule.contains(Vec3.of(0.4, 1, 0))).isTrue();
        assertThat(capsule.contains(Vec3.of(0, 2.4, 0))).isTrue();
        assertThat(capsule.contains(Vec3.of(0.6, 1, 0))).isFalse();
        assertThat(capsule.signedDistance(Vec3.of(3, 1, 0))).isEqualTo(2.5);
        assertThat(capsule.signedDistance(Vec3.of(0, 1, 0))).isEqualTo(-0.5);
        assertThat(capsule.closestPoint(Vec3.of(3, 1, 0))).isEqualTo(Vec3.of(0.5, 1, 0));
        assertThat(capsule.closestPoint(Vec3.of(0.1, 1, 0))).isEqualTo(Vec3.of(0.1, 1, 0));
        assertThat(capsule.intersects(new Sphere(Vec3.of(1, 1, 0), 0.6))).isTrue();
        assertThat(capsule.intersects(new Sphere(Vec3.of(1, 1, 0), 0.4))).isFalse();
        assertThat(capsule.intersects(new Capsule(Vec3.of(0.9, -1, 0), Vec3.of(0.9, 3, 0), 0.5))).isTrue();
        assertThat(capsule.intersects(new Capsule(Vec3.of(1.1, -1, 0), Vec3.of(1.1, 3, 0), 0.5))).isFalse();
        Transform t = Transform.of(Vec3.ONE, Quat.fromAxisAngle(Vec3.UNIT_X, 1), Vec3.of(1, -2, 1));
        Capsule moved = capsule.transform(t);
        assertThat(moved.radius()).isEqualTo(1.0);
        assertThat(moved.start()).isEqualTo(t.transformPoint(Vec3.ZERO));
        assertThat(capsule.transform(t.toMat4()).radius()).isCloseTo(1, within(1e-12));
        assertThat(capsule.transform(Mat4.translation(Vec3.ONE)).end()).isEqualTo(Vec3.of(1, 3, 1));
    }

    @Test
    void rays() {
        assertThat(capsule.intersect(new Ray(Vec3.of(-5, 1, 0), Vec3.UNIT_X))).isCloseTo(4.5, within(1e-12)); // body
        assertThat(capsule.intersect(new Ray(Vec3.of(0, 10, 0), Vec3.of(0, -1, 0)))).isCloseTo(7.5, within(1e-12)); // along axis
        assertThat(capsule.intersect(new Ray(Vec3.of(-5, 2.3, 0), Vec3.UNIT_X))).isCloseTo(5 - 0.4, within(1e-12)); // cap
        assertThat(capsule.intersect(new Ray(Vec3.of(-5, -0.3, 0), Vec3.UNIT_X))).isCloseTo(5 - 0.4, within(1e-12)); // cap
        assertThat(capsule.intersect(new Ray(Vec3.of(-5, 5, 0), Vec3.UNIT_X))).isEqualTo(MISS);
        assertThat(capsule.intersect(new Ray(Vec3.of(5, 1, 0), Vec3.UNIT_X))).isEqualTo(MISS); // behind
        assertThat(capsule.intersect(new Ray(Vec3.of(0, 1, 0), Vec3.UNIT_X))).isEqualTo(0.0); // inside

        // Any reported hit lies on the surface, the ray is outside before it, and misses never touch the capsule.
        Rng rng = Rng.seeded(22);
        for (int i = 0; i < 5_000; i++) {
            Capsule c = new Capsule(rng.nextVec3InSphere(), rng.nextVec3InSphere().scale(2), rng.nextDouble(0.1, 1));
            Ray ray = new Ray(rng.nextVec3OnSphere().scale(5), rng.nextVec3OnSphere());
            double t = c.intersect(ray);
            if (c.contains(ray.origin())) {
                assertThat(t).isEqualTo(0.0);
            } else if (t < MISS) {
                assertThat(c.signedDistance(ray.at(t))).isCloseTo(0, within(1e-9));
                assertThat(c.signedDistance(ray.at(t * 0.999))).isPositive();
            } else {
                for (int k = 0; k <= 200; k++) {
                    assertThat(c.signedDistance(ray.at(k * 0.05))).isGreaterThan(-1e-9);
                }
            }
        }
    }

    @Test
    void aabbArray() {
        try (Arena arena = Arena.ofConfined()) {
            for (AabbArray boxes : new AabbArray[] {AabbArray.onHeap(3), AabbArray.allocate(arena, 3)}) {
                assertThat(boxes.get(0).isEmpty()).isTrue();
                boxes.set(1, new Aabb(Vec3.ZERO, Vec3.ONE));
                boxes.set(2, -1, -2, -3, 1, 2, 3);
                assertThat(boxes.get(2)).isEqualTo(new Aabb(Vec3.of(-1, -2, -3), Vec3.of(1, 2, 3)));
                assertThat(boxes.get(2, 4)).isEqualTo(2.0);
                assertThat(AabbArray.wrap(boxes.segment()).get(1)).isEqualTo(new Aabb(Vec3.ZERO, Vec3.ONE));
            }
        }
        assertThat(Aabb.LAYOUT.byteSize()).isEqualTo(48);
    }
}
