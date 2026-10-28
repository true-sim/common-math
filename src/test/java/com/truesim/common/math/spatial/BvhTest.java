package com.truesim.common.math.spatial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truesim.common.math.Mat4;
import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.geometry.Aabb;
import com.truesim.common.math.geometry.AabbArray;
import com.truesim.common.math.geometry.Frustum;
import com.truesim.common.math.geometry.Intersections;
import com.truesim.common.math.geometry.Ray;
import com.truesim.common.math.geometry.Sphere;
import com.truesim.common.math.random.Rng;
import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BvhTest {

    private static AabbArray randomBoxes(Rng rng, int n) {
        AabbArray boxes = AabbArray.onHeap(n);
        for (int i = 0; i < n; i++) {
            Vec3 center = rng.nextVec3InSphere().scale(50);
            boxes.set(i, Aabb.fromCenterExtents(center, Vec3.of(rng.nextDouble(0.1, 3), rng.nextDouble(0.1, 3), rng.nextDouble(0.1, 3))));
        }
        return boxes;
    }

    private static Set<Integer> collect(java.util.function.Consumer<Bvh.Visitor> query) {
        Set<Integer> found = new HashSet<>();
        query.accept(p -> {
            assertThat(found.add(p)).as("visited once").isTrue();
            return true;
        });
        return found;
    }

    /** Checks every query type against testing all primitives directly. */
    private static void assertMatchesBruteForce(Bvh bvh, AabbArray boxes, Rng rng) {
        for (int q = 0; q < 200; q++) {
            Aabb query = Aabb.fromCenterExtents(rng.nextVec3InSphere().scale(50), Vec3.splat(rng.nextDouble(1, 15)));
            Set<Integer> expected = new HashSet<>();
            for (int i = 0; i < boxes.size(); i++) {
                if (boxes.get(i).intersects(query)) {
                    expected.add(i);
                }
            }
            assertThat(collect(v -> bvh.queryAabb(query, v))).isEqualTo(expected);

            Sphere sphere = new Sphere(query.center(), query.extents().x());
            Set<Integer> inSphere = new HashSet<>();
            for (int i = 0; i < boxes.size(); i++) {
                if (sphere.intersects(boxes.get(i))) {
                    inSphere.add(i);
                }
            }
            assertThat(collect(v -> bvh.querySphere(sphere, v))).isEqualTo(inSphere);

            Ray ray = new Ray(rng.nextVec3OnSphere().scale(80), rng.nextVec3OnSphere());
            double nearest = Double.POSITIVE_INFINITY;
            for (int i = 0; i < boxes.size(); i++) {
                nearest = Math.min(nearest, boxes.get(i).intersect(ray));
            }
            RayHit hit = new RayHit();
            assertThat(bvh.raycastBounds(ray, Double.POSITIVE_INFINITY, hit)).isEqualTo(nearest < Double.POSITIVE_INFINITY);
            assertThat(hit.distance()).isEqualTo(nearest);
            if (hit.isHit()) {
                assertThat(boxes.get(hit.primitive()).intersect(ray)).isEqualTo(nearest);
            }
        }
        for (int f = 0; f < 20; f++) {
            Vec3 eye = rng.nextVec3InSphere().scale(60);
            Frustum frustum = Frustum.fromMatrix(Mat4.perspective(rng.nextDouble(0.3, 1.5), 1.5, rng.nextDouble(0.1, 20),
                rng.nextDouble(25, 120)).mul(Mat4.lookAt(eye, rng.nextVec3InSphere().scale(20), Vec3.UP)));
            Set<Integer> visible = new HashSet<>();
            for (int i = 0; i < boxes.size(); i++) {
                if (frustum.intersects(boxes.get(i))) {
                    visible.add(i);
                }
            }
            assertThat(collect(v -> bvh.queryFrustum(frustum, v))).isEqualTo(visible);
        }
    }

    @Test
    void frustumQueriesStopAnywhereKeepingTheirOrder() {
        Rng rng = Rng.seeded(77);
        AabbArray boxes = randomBoxes(rng, 3_000);
        for (int leafSize : new int[] {1, 4}) {
            Bvh bvh = Bvh.build(Arena.ofAuto(), boxes, leafSize);
            // One frustum seeing part of the boxes (straddled leaves), one seeing all (whole subtrees untested).
            Frustum part = Frustum.fromMatrix(Mat4.perspective(0.8, 1.2, 1, 60)
                .mul(Mat4.lookAt(Vec3.of(0, 0, 80), Vec3.ZERO, Vec3.UP)));
            Frustum all = Frustum.fromMatrix(Mat4.perspective(2.5, 1, 1, 10_000)
                .mul(Mat4.lookAt(Vec3.of(0, 0, 2_000), Vec3.ZERO, Vec3.UP)));
            for (Frustum frustum : new Frustum[] {part, all}) {
                List<Integer> everything = new ArrayList<>();
                int total = bvh.queryFrustum(frustum, p -> everything.add(p));
                assertThat(total).isEqualTo(everything.size()).isPositive();
                for (int stopAfter = 1; stopAfter <= Math.min(total, 40); stopAfter++) {
                    List<Integer> prefix = new ArrayList<>();
                    int limit = stopAfter;
                    int visited = bvh.queryFrustum(frustum, p -> {
                        prefix.add(p);
                        return prefix.size() < limit;
                    });
                    assertThat(visited).isEqualTo(stopAfter);
                    assertThat(prefix).isEqualTo(everything.subList(0, stopAfter));
                }
            }
            assertThat(bvh.queryFrustum(all, p -> true)).isEqualTo(3_000);
        }
        assertThat(Bvh.build(AabbArray.onHeap(0)).queryFrustum(Frustum.fromMatrix(Mat4.IDENTITY), p -> true)).isZero();
    }

    @Test
    void queriesMatchBruteForce() {
        Rng rng = Rng.seeded(31);
        AabbArray boxes = randomBoxes(rng, 2_000);
        for (int leafSize : new int[] {1, 4, 16}) {
            try (Arena arena = Arena.ofConfined()) {
                Bvh bvh = Bvh.build(arena, boxes, leafSize);
                assertThat(bvh.primitiveCount()).isEqualTo(2_000);
                assertThat(bvh.nodeCount()).isBetween(2 * 2_000 / leafSize - 1, 2 * 2_000 - 1);
                assertMatchesBruteForce(bvh, boxes, rng);
            }
        }
    }

    @Test
    void refitFollowsMovingObjects() {
        Rng rng = Rng.seeded(32);
        AabbArray boxes = randomBoxes(rng, 500);
        Bvh bvh = Bvh.build(boxes);
        assertThat(bvh.primitiveBounds()).isSameAs(boxes);
        for (int i = 0; i < boxes.size(); i++) {
            Aabb box = boxes.get(i);
            Vec3 move = rng.nextVec3InSphere().scale(10);
            boxes.set(i, new Aabb(box.min().add(move), box.max().add(move)));
        }
        bvh.refit();
        Aabb all = Aabb.EMPTY;
        for (int i = 0; i < boxes.size(); i++) {
            all = all.union(boxes.get(i));
        }
        assertThat(bvh.bounds()).isEqualTo(all);
        assertMatchesBruteForce(bvh, boxes, rng);
    }

    @Test
    void degradationAndRebuild() {
        Rng rng = Rng.seeded(34);
        AabbArray boxes = randomBoxes(rng, 1_000);
        Bvh bvh = Bvh.build(boxes);
        long memory = bvh.nodes().address();
        assertThat(bvh.degradation()).isEqualTo(1.0);
        bvh.refit();
        assertThat(bvh.degradation()).isEqualTo(1.0); // nothing moved

        // Every object moves to a random new place: the refit tree overlaps badly.
        for (int i = 0; i < boxes.size(); i++) {
            Aabb box = boxes.get(i);
            Vec3 move = rng.nextVec3InSphere().scale(60);
            boxes.set(i, new Aabb(box.min().add(move), box.max().add(move)));
        }
        bvh.refit();
        assertThat(bvh.degradation()).isGreaterThan(2);
        assertMatchesBruteForce(bvh, boxes, rng); // still correct, just slower

        bvh.rebuild();
        assertThat(bvh.degradation()).isEqualTo(1.0);
        assertThat(bvh.nodes().address()).isEqualTo(memory); // same memory
        assertThat(bvh.nodes().byteSize()).isEqualTo((long) Bvh.NODE_BYTES * bvh.nodeCount());
        assertMatchesBruteForce(bvh, boxes, rng);

        // Moving a single object far away stretches its ancestors without hiding behind the larger root box.
        Aabb outlier = boxes.get(0);
        Vec3 far = Vec3.of(1_000, 0, 0);
        boxes.set(0, new Aabb(outlier.min().add(far), outlier.max().add(far)));
        bvh.refit();
        assertThat(bvh.degradation()).isGreaterThan(1.5);
    }

    @Test
    void degradationEdgeCases() {
        Bvh empty = Bvh.build(AabbArray.onHeap(0));
        assertThat(empty.degradation()).isEqualTo(1.0);
        empty.rebuild();
        assertThat(empty.nodeCount()).isZero();

        AabbArray points = AabbArray.onHeap(10);
        points.fill(new Aabb(Vec3.ONE, Vec3.ONE)); // all in one point: the root has no area
        Bvh flat = Bvh.build(points);
        flat.refit();
        assertThat(flat.degradation()).isEqualTo(1.0);
    }

    @Test
    void customRayTestsAndEarlyStop() {
        Rng rng = Rng.seeded(33);
        AabbArray boxes = randomBoxes(rng, 300);
        Bvh bvh = Bvh.build(boxes);
        // Spheres inscribed in the boxes, as the "real" shapes.
        Bvh.RayTest spheres = (p, ox, oy, oz, dx, dy, dz, max) -> Intersections.raySphere(ox, oy, oz, dx, dy, dz,
            0.5 * (boxes.get(p, 0) + boxes.get(p, 3)), 0.5 * (boxes.get(p, 1) + boxes.get(p, 4)),
            0.5 * (boxes.get(p, 2) + boxes.get(p, 5)), 0.1);
        RayHit hit = new RayHit();
        for (int q = 0; q < 200; q++) {
            Ray ray = Ray.through(rng.nextVec3OnSphere().scale(80), boxes.get(rng.nextInt(300)).center());
            double nearest = Double.POSITIVE_INFINITY;
            for (int i = 0; i < boxes.size(); i++) {
                Vec3 c = boxes.get(i).center();
                nearest = Math.min(nearest, Intersections.raySphere(ray.origin(), ray.direction(), c, 0.1));
            }
            bvh.raycast(ray, Double.POSITIVE_INFINITY, spheres, hit);
            assertThat(hit.distance()).isEqualTo(nearest);
        }
        assertThat(bvh.raycast(Ray.through(Vec3.splat(200), Vec3.splat(300)), 1e9, spheres, hit)).isFalse();
        assertThat(hit.primitive()).isEqualTo(-1);
        assertThat(bvh.queryAabb(bvh.bounds(), p -> false)).isEqualTo(1);
        assertThat(bvh.querySphere(Vec3.ZERO.x(), 0, 0, 1_000, p -> false)).isEqualTo(1);
        Frustum everything = Frustum.fromMatrix(Mat4.orthographic(-500, 500, -500, 500, -500, 500));
        assertThat(bvh.queryFrustum(everything, p -> false)).isEqualTo(1);
    }

    @Test
    void degenerateInputs() {
        Bvh empty = Bvh.build(AabbArray.onHeap(0));
        assertThat(empty.nodeCount()).isZero();
        assertThat(empty.bounds()).isEqualTo(Aabb.EMPTY);
        RayHit hit = new RayHit();
        assertThat(empty.raycastBounds(new Ray(Vec3.ZERO, Vec3.UNIT_X), 1, hit)).isFalse();
        assertThat(empty.queryAabb(new Aabb(Vec3.splat(-1), Vec3.ONE), p -> true)).isZero();
        empty.refit();

        AabbArray one = AabbArray.onHeap(1);
        one.set(0, new Aabb(Vec3.ZERO, Vec3.ONE));
        Bvh single = Bvh.build(one);
        assertThat(single.nodeCount()).isEqualTo(1);
        assertThat(single.raycastBounds(new Ray(Vec3.of(-1, 0.5, 0.5), Vec3.UNIT_X), 10, hit)).isTrue();
        assertThat(hit.distance()).isEqualTo(1.0);

        // All boxes identical: no axis can be split, so ranges are halved.
        AabbArray same = AabbArray.onHeap(100);
        same.fill(new Aabb(Vec3.ZERO, Vec3.ONE));
        Bvh stacked = Bvh.build(same);
        assertThat(stacked.queryAabb(new Aabb(Vec3.splat(0.5), Vec3.splat(2)), p -> true)).isEqualTo(100);

        // Boxes on a line: two axes have no extent.
        AabbArray line = AabbArray.onHeap(64);
        for (int i = 0; i < 64; i++) {
            line.set(i, Aabb.fromCenterExtents(Vec3.of(i, 0, 0), Vec3.splat(0.25)));
        }
        Bvh row = Bvh.build(line);
        assertThat(row.queryAabb(new Aabb(Vec3.of(9.9, -1, -1), Vec3.of(12.1, 1, 1)), p -> true)).isEqualTo(3);
        assertThat(row.nodes().isReadOnly()).isTrue();
        assertThat(row.nodes().byteSize()).isEqualTo((long) Bvh.NODE_BYTES * row.nodeCount());

        assertThatThrownBy(() -> Bvh.build(Arena.ofAuto(), line, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Maths.PI).isPositive();
    }
}
