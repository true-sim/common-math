package com.truesim.common.math.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.truesim.common.math.Mat4;
import com.truesim.common.math.Maths;
import com.truesim.common.math.Quat;
import com.truesim.common.math.Transform;
import com.truesim.common.math.Vec2;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.random.Rng;
import org.junit.jupiter.api.Test;

class GeometryTest {

    private static final double EPS = 1e-12;
    private static final double MISS = Double.POSITIVE_INFINITY;
    private static final Quat ROTATION = Quat.fromYawPitchRoll(0.6, -0.4, 0.3);
    private static final Transform TRANSFORM = Transform.of(Vec3.of(1, 2, 3), ROTATION, Vec3.splat(2));
    private static final Vec3[] UNIT_STEPS = {
        Vec3.UNIT_X, Vec3.UNIT_X.negate(), Vec3.UNIT_Y, Vec3.UNIT_Y.negate(), Vec3.UNIT_Z, Vec3.UNIT_Z.negate()};

    // ---------------------------------------------------------------- rays and planes

    @Test
    void rays() {
        Ray ray = Ray.through(Vec3.ZERO, Vec3.of(0, 0, -5));
        assertThat(ray.direction()).isEqualTo(Vec3.FORWARD);
        assertThat(ray.at(2)).isEqualTo(Vec3.of(0, 0, -2));
        assertThat(ray.distance(Vec3.of(1, 0, -3))).isCloseTo(1, within(EPS));
        assertThat(ray.distance(Vec3.of(0, 0, 4))).isCloseTo(4, within(EPS)); // behind the origin
        Ray moved = ray.transform(TRANSFORM);
        assertThat(moved.origin().approxEquals(TRANSFORM.transformPoint(Vec3.ZERO), EPS)).isTrue();
        assertThat(ray.transform(TRANSFORM.toMat4()).direction().approxEquals(moved.direction(), EPS)).isTrue();
    }

    @Test
    void axisParallelRaysOnBoxFaces() {
        // Rays along each axis, with the other two coordinates on a face, inside, or outside the box [0, 1]^3.
        double[] offsets = {0, 1, 0.5, -0.5, 1.5};
        boolean[] inside = {true, true, true, false, false};
        for (int axis = 0; axis < 3; axis++) {
            for (int i = 0; i < offsets.length; i++) {
                for (int j = 0; j < offsets.length; j++) {
                    double[] origin = new double[3];
                    double[] direction = new double[3];
                    origin[axis] = -2;
                    direction[axis] = 1;
                    origin[(axis + 1) % 3] = offsets[i];
                    origin[(axis + 2) % 3] = offsets[j];
                    double t = Intersections.rayAabb(origin[0], origin[1], origin[2], direction[0], direction[1],
                        direction[2], 0, 0, 0, 1, 1, 1);
                    assertThat(t).as("axis %d, %s %s", axis, offsets[i], offsets[j])
                        .isEqualTo(inside[i] && inside[j] ? 2 : Double.POSITIVE_INFINITY);
                }
            }
        }
        // A vertical ray exactly on the edge of a flat floor tile.
        assertThat(Intersections.rayAabb(4, 10, 4, 0, -1, 0, 2, 0, 2, 4, 0, 4)).isEqualTo(10);
        assertThat(new Aabb(Vec3.of(2, 0, 2), Vec3.of(4, 0, 4)).intersect(new Ray(Vec3.of(2, 10, 3), Vec3.of(0, -1, 0))))
            .isEqualTo(10);
    }

    @Test
    void planes() {
        Plane floor = Plane.fromPointNormal(Vec3.of(0, 1, 0), Vec3.of(0, 3, 0));
        assertThat(floor).isEqualTo(new Plane(Vec3.UP, 1));
        assertThat(Plane.fromPoints(Vec3.ZERO, Vec3.UNIT_Z, Vec3.UNIT_X).normal().approxEquals(Vec3.UP, EPS)).isTrue();
        assertThat(floor.signedDistance(Vec3.of(5, 3, 2))).isEqualTo(2.0);
        assertThat(floor.project(Vec3.of(5, 3, 2))).isEqualTo(Vec3.of(5, 1, 2));
        assertThat(floor.flip().signedDistance(Vec3.of(5, 3, 2))).isEqualTo(-2.0);
        assertThat(floor.intersect(new Ray(Vec3.of(0, 5, 0), Vec3.of(0, -1, 0)))).isEqualTo(4.0);
        assertThat(floor.intersect(new Ray(Vec3.of(0, 5, 0), Vec3.UNIT_X))).isEqualTo(MISS);
        assertThat(floor.intersect(new Ray(Vec3.of(0, 5, 0), Vec3.UP))).isEqualTo(MISS);
        Vec3 onPlane = Vec3.of(3, 1, -2);
        Plane moved = floor.transform(TRANSFORM);
        assertThat(moved.signedDistance(TRANSFORM.transformPoint(onPlane))).isCloseTo(0, within(1e-9));
        Plane viaMatrix = floor.transform(TRANSFORM.toMat4());
        assertThat(viaMatrix.normal().approxEquals(moved.normal(), 1e-9)).isTrue();
        assertThat(viaMatrix.distance()).isCloseTo(moved.distance(), within(1e-9));
    }

    // ---------------------------------------------------------------- boxes

    @Test
    void aabbBasics() {
        Aabb box = Aabb.of(Vec3.of(1, 1, 1), Vec3.of(-1, -2, -3));
        assertThat(box).isEqualTo(new Aabb(Vec3.of(-1, -2, -3), Vec3.ONE));
        assertThat(Aabb.fromCenterExtents(Vec3.ZERO, Vec3.ONE)).isEqualTo(new Aabb(Vec3.splat(-1), Vec3.ONE));
        assertThat(box.center()).isEqualTo(Vec3.of(0, -0.5, -1));
        assertThat(box.size()).isEqualTo(Vec3.of(2, 3, 4));
        assertThat(box.extents()).isEqualTo(Vec3.of(1, 1.5, 2));
        assertThat(box.volume()).isEqualTo(24.0);
        assertThat(box.surfaceArea()).isEqualTo(52.0);
        assertThat(Aabb.EMPTY.isEmpty()).isTrue();
        assertThat(box.isEmpty()).isFalse();
        assertThat(new Aabb(Vec3.ZERO, Vec3.of(1, -1, 1)).isEmpty()).isTrue();
        assertThat(new Aabb(Vec3.ZERO, Vec3.of(1, 1, -1)).isEmpty()).isTrue();
        assertThat(box.closestPoint(Vec3.of(5, 0, 0))).isEqualTo(Vec3.of(1, 0, 0));
        assertThat(box.distance(Vec3.of(5, 0, 0))).isEqualTo(4.0);
        assertThat(box.union(Aabb.of(Vec3.splat(5), Vec3.splat(6))).max()).isEqualTo(Vec3.splat(6));
        assertThat(Aabb.EMPTY.include(Vec3.ONE).include(Vec3.ZERO)).isEqualTo(new Aabb(Vec3.ZERO, Vec3.ONE));
        assertThat(box.expand(1).min()).isEqualTo(Vec3.of(-2, -3, -4));
    }

    @Test
    void aabbContainmentAndOverlap() {
        Aabb box = new Aabb(Vec3.splat(-1), Vec3.ONE);
        assertThat(box.contains(Vec3.ZERO)).isTrue();
        Aabb inner = new Aabb(Vec3.splat(-0.5), Vec3.splat(0.5));
        assertThat(box.contains(inner)).isTrue();
        assertThat(box.contains(inner.expand(1))).isFalse();
        assertThat(box.contains(new Aabb(Vec3.ZERO, Vec3.splat(2)))).isFalse(); // min inside, max outside
        assertThat(box.intersects(inner)).isTrue();
        for (Vec3 step : UNIT_STEPS) {
            assertThat(box.contains(step.scale(1.5))).as("point %s", step).isFalse();
            Aabb shifted = new Aabb(box.min().add(step.scale(3)), box.max().add(step.scale(3)));
            assertThat(box.intersects(shifted)).as("box %s", step).isFalse();
        }
    }

    @Test
    void aabbRaysAndTransforms() {
        Aabb box = new Aabb(Vec3.splat(-1), Vec3.ONE);
        assertThat(box.intersect(new Ray(Vec3.of(-5, 0, 0), Vec3.UNIT_X))).isEqualTo(4.0);
        assertThat(box.intersect(new Ray(Vec3.ZERO, Vec3.UNIT_X))).isEqualTo(0.0);
        assertThat(box.intersect(new Ray(Vec3.of(-5, 5, 0), Vec3.UNIT_X))).isEqualTo(MISS);
        Aabb moved = box.transform(TRANSFORM);
        for (Vec3 corner : new Vec3[] {Vec3.splat(-1), Vec3.ONE, Vec3.of(1, -1, 1), Vec3.of(-1, 1, -1)}) {
            assertThat(moved.expand(1e-9).contains(TRANSFORM.transformPoint(corner))).isTrue();
        }
        assertThat(box.transform(Mat4.translation(Vec3.ONE))).isEqualTo(new Aabb(Vec3.ZERO, Vec3.splat(2)));
    }

    @Test
    void rects() {
        Rect rect = Rect.of(Vec2.of(2, 3), Vec2.ZERO);
        assertThat(rect).isEqualTo(Rect.fromPositionSize(Vec2.ZERO, Vec2.of(2, 3)));
        assertThat(rect.center()).isEqualTo(Vec2.of(1, 1.5));
        assertThat(rect.size()).isEqualTo(Vec2.of(2, 3));
        assertThat(rect.area()).isEqualTo(6.0);
        assertThat(Rect.EMPTY.isEmpty()).isTrue();
        assertThat(new Rect(Vec2.ZERO, Vec2.of(1, -1)).isEmpty()).isTrue();
        assertThat(rect.isEmpty()).isFalse();
        assertThat(rect.contains(Vec2.ONE)).isTrue();
        assertThat(rect.contains(new Rect(Vec2.ONE, Vec2.of(1.5, 2)))).isTrue();
        assertThat(rect.contains(rect.expand(1))).isFalse();
        assertThat(rect.contains(new Rect(Vec2.ONE, Vec2.splat(9)))).isFalse(); // min inside, max outside
        for (Vec2 step : new Vec2[] {Vec2.UNIT_X, Vec2.UNIT_X.negate(), Vec2.UNIT_Y, Vec2.UNIT_Y.negate()}) {
            Vec2 offset = step.scale(10);
            assertThat(rect.contains(rect.center().add(offset))).isFalse();
            assertThat(rect.intersects(new Rect(rect.min().add(offset), rect.max().add(offset)))).isFalse();
        }
        assertThat(rect.intersects(rect.expand(-0.5))).isTrue();
        assertThat(rect.intersection(Rect.of(Vec2.ONE, Vec2.splat(5)))).isEqualTo(new Rect(Vec2.ONE, Vec2.of(2, 3)));
        assertThat(rect.closestPoint(Vec2.of(-1, 1))).isEqualTo(Vec2.of(0, 1));
        assertThat(rect.union(Rect.of(Vec2.splat(4), Vec2.splat(5))).max()).isEqualTo(Vec2.splat(5));
        assertThat(Rect.EMPTY.include(Vec2.ONE).include(Vec2.ZERO)).isEqualTo(new Rect(Vec2.ZERO, Vec2.ONE));
    }

    // ---------------------------------------------------------------- spheres, triangles, segments

    @Test
    void spheres() {
        Sphere sphere = new Sphere(Vec3.ZERO, 1);
        assertThat(sphere.contains(Vec3.of(0.5, 0, 0))).isTrue();
        assertThat(sphere.contains(Vec3.of(2, 0, 0))).isFalse();
        assertThat(sphere.intersects(new Sphere(Vec3.of(1.5, 0, 0), 1))).isTrue();
        assertThat(sphere.intersects(new Sphere(Vec3.of(3, 0, 0), 1))).isFalse();
        assertThat(sphere.intersects(new Aabb(Vec3.of(0.5, 0.5, 0.5), Vec3.splat(2)))).isTrue();
        assertThat(sphere.intersects(new Aabb(Vec3.splat(0.8), Vec3.splat(2)))).isFalse();
        assertThat(sphere.closestPoint(Vec3.of(0.5, 0, 0))).isEqualTo(Vec3.of(0.5, 0, 0));
        assertThat(sphere.closestPoint(Vec3.of(3, 0, 0))).isEqualTo(Vec3.UNIT_X);
        assertThat(sphere.bounds()).isEqualTo(new Aabb(Vec3.splat(-1), Vec3.ONE));
        assertThat(sphere.intersect(new Ray(Vec3.of(-5, 0, 0), Vec3.UNIT_X))).isEqualTo(4.0);
        assertThat(sphere.intersect(new Ray(Vec3.ZERO, Vec3.UNIT_X))).isEqualTo(1.0);
        assertThat(sphere.intersect(new Ray(Vec3.of(-5, 5, 0), Vec3.UNIT_X))).isEqualTo(MISS);
        assertThat(sphere.intersect(new Ray(Vec3.of(5, 0, 0), Vec3.UNIT_X))).isEqualTo(MISS);
        Sphere moved = sphere.transform(Transform.of(Vec3.ONE, ROTATION, Vec3.of(1, -3, 2)));
        assertThat(moved).isEqualTo(new Sphere(Vec3.ONE, 3));
        assertThat(sphere.transform(Mat4.trs(Vec3.ONE, ROTATION, Vec3.of(1, 3, 2))).radius()).isCloseTo(3, within(1e-9));
    }

    @Test
    void triangles() {
        Triangle t = new Triangle(Vec3.ZERO, Vec3.UNIT_X, Vec3.UNIT_Y);
        assertThat(t.normal()).isEqualTo(Vec3.UNIT_Z);
        assertThat(t.area()).isEqualTo(0.5);
        assertThat(t.centroid().approxEquals(Vec3.of(1.0 / 3, 1.0 / 3, 0), EPS)).isTrue();
        assertThat(t.plane()).isEqualTo(new Plane(Vec3.UNIT_Z, 0));
        assertThat(t.bounds()).isEqualTo(new Aabb(Vec3.ZERO, Vec3.of(1, 1, 0)));
        assertThat(t.barycentric(Vec3.of(0.25, 0.5, 7)).approxEquals(Vec3.of(0.25, 0.25, 0.5), EPS)).isTrue();
        assertThat(t.transform(TRANSFORM).a()).isEqualTo(TRANSFORM.transformPoint(Vec3.ZERO));
        assertThat(t.transform(TRANSFORM.toMat4()).c().approxEquals(TRANSFORM.transformPoint(Vec3.UNIT_Y), 1e-9)).isTrue();
    }

    @Test
    void triangleRays() {
        Triangle t = new Triangle(Vec3.ZERO, Vec3.UNIT_X, Vec3.UNIT_Y);
        Vec3 down = Vec3.of(0, 0, -1);
        assertThat(t.intersect(new Ray(Vec3.of(0.25, 0.25, 1), down))).isEqualTo(1.0);
        assertThat(t.intersect(new Ray(Vec3.of(0.25, 0.25, -1), Vec3.UNIT_Z))).isEqualTo(1.0); // back side
        assertThat(t.intersect(new Ray(Vec3.of(0.25, 0.25, 1), Vec3.UNIT_X))).isEqualTo(MISS); // parallel
        assertThat(t.intersect(new Ray(Vec3.of(-0.5, 0.25, 1), down))).isEqualTo(MISS); // u < 0
        assertThat(t.intersect(new Ray(Vec3.of(1.5, -0.2, 1), down))).isEqualTo(MISS); // u > 1
        assertThat(t.intersect(new Ray(Vec3.of(0.5, -0.5, 1), down))).isEqualTo(MISS); // v < 0
        assertThat(t.intersect(new Ray(Vec3.of(0.8, 0.8, 1), down))).isEqualTo(MISS); // u + v > 1
        assertThat(t.intersect(new Ray(Vec3.of(0.25, 0.25, -1), down))).isEqualTo(MISS); // behind
    }

    @Test
    void triangleClosestPointRegions() {
        Triangle t = new Triangle(Vec3.ZERO, Vec3.UNIT_X, Vec3.UNIT_Y);
        assertThat(t.closestPoint(Vec3.of(-1, -1, 0))).isEqualTo(Vec3.ZERO); // vertex a
        assertThat(t.closestPoint(Vec3.of(2, -0.5, 0))).isEqualTo(Vec3.UNIT_X); // vertex b
        assertThat(t.closestPoint(Vec3.of(0.5, -1, 0))).isEqualTo(Vec3.of(0.5, 0, 0)); // edge ab
        assertThat(t.closestPoint(Vec3.of(-0.5, 2, 0))).isEqualTo(Vec3.UNIT_Y); // vertex c
        assertThat(t.closestPoint(Vec3.of(-1, 0.5, 0))).isEqualTo(Vec3.of(0, 0.5, 0)); // edge ac
        assertThat(t.closestPoint(Vec3.of(1, 1, 0)).approxEquals(Vec3.of(0.5, 0.5, 0), EPS)).isTrue(); // edge bc
        assertThat(t.closestPoint(Vec3.of(0.25, 0.25, 1)).approxEquals(Vec3.of(0.25, 0.25, 0), EPS)).isTrue(); // inside
        // Brute force over many points: never farther than the distance to any sampled point of the triangle.
        Rng rng = Rng.seeded(4);
        for (int i = 0; i < 2_000; i++) {
            Vec3 p = rng.nextVec3InSphere().scale(3);
            double best = t.closestPoint(p).distance(p);
            for (int k = 0; k < 20; k++) {
                double u = rng.nextDouble();
                double v = rng.nextDouble() * (1 - u);
                assertThat(best).isLessThanOrEqualTo(Vec3.of(u, v, 0).distance(p) + 1e-12);
            }
        }
    }

    @Test
    void closestPointOnDegenerateTriangles() {
        Vec3 o = Vec3.ZERO;
        Vec3 x1 = Vec3.UNIT_X;
        Vec3 x2 = Vec3.of(2, 0, 0);
        Vec3 xm = Vec3.of(-1, 0, 0);
        Triangle[] flat = {
            new Triangle(o, x1, x2), new Triangle(o, x1, xm), new Triangle(o, xm, x1), new Triangle(x1, o, x2),
            new Triangle(x2, x1, o), new Triangle(xm, x2, o), new Triangle(o, o, x1), new Triangle(o, x1, x1)};
        Rng rng = Rng.seeded(8);
        for (Triangle t : flat) {
            Segment[] edges = {new Segment(t.a(), t.b()), new Segment(t.b(), t.c()), new Segment(t.c(), t.a())};
            for (int i = 0; i < 500; i++) {
                Vec3 p = rng.nextVec3InSphere().scale(4);
                Vec3 closest = t.closestPoint(p);
                assertThat(closest.isFinite()).isTrue();
                double best = Double.POSITIVE_INFINITY;
                for (Segment edge : edges) {
                    best = Math.min(best, edge.distance(p));
                }
                assertThat(closest.distance(p)).isCloseTo(best, within(1e-9));
            }
        }
    }

    @Test
    void segments() {
        Segment s = new Segment(Vec3.ZERO, Vec3.of(4, 0, 0));
        assertThat(s.length()).isEqualTo(4.0);
        assertThat(s.at(0.25)).isEqualTo(Vec3.UNIT_X);
        assertThat(s.closestParameter(Vec3.of(1, 5, 0))).isEqualTo(0.25);
        assertThat(s.closestParameter(Vec3.of(-3, 0, 0))).isEqualTo(0.0);
        assertThat(s.closestParameter(Vec3.of(9, 0, 0))).isEqualTo(1.0);
        assertThat(s.closestPoint(Vec3.of(2, 3, 0))).isEqualTo(Vec3.of(2, 0, 0));
        assertThat(s.distance(Vec3.of(2, 3, 0))).isEqualTo(3.0);
        assertThat(new Segment(Vec3.ONE, Vec3.ONE).closestParameter(Vec3.ZERO)).isEqualTo(0.0);
        assertThat(s.transform(TRANSFORM).end()).isEqualTo(TRANSFORM.transformPoint(Vec3.of(4, 0, 0)));
        assertThat(s.transform(Mat4.translation(Vec3.ONE)).start()).isEqualTo(Vec3.ONE);
    }

    // ---------------------------------------------------------------- oriented boxes

    @Test
    void obbBasics() {
        Obb box = Obb.of(Vec3.ZERO, Vec3.of(1, 2, 3), Quat.IDENTITY);
        assertThat(box.contains(Vec3.of(0.5, 1, 1))).isTrue();
        assertThat(box.contains(Vec3.of(2, 0, 0))).isFalse();
        assertThat(box.contains(Vec3.of(0, 3, 0))).isFalse();
        assertThat(box.contains(Vec3.of(0, 0, 4))).isFalse();
        assertThat(box.closestPoint(Vec3.of(5, 0, -9))).isEqualTo(Vec3.of(1, 0, -3));
        assertThat(box.bounds()).isEqualTo(new Aabb(Vec3.of(-1, -2, -3), Vec3.of(1, 2, 3)));
        assertThat(Obb.of(new Aabb(Vec3.ZERO, Vec3.of(2, 4, 6)))).isEqualTo(Obb.of(Vec3.of(1, 2, 3), Vec3.of(1, 2, 3), Quat.IDENTITY));

        Obb turned = Obb.of(Vec3.ONE, Vec3.of(1, 2, 3), ROTATION);
        assertThat(turned.contains(Vec3.ONE.add(ROTATION.rotate(Vec3.of(0.9, -1.9, 2.9))))).isTrue();
        assertThat(turned.closestPoint(Vec3.ONE)).isEqualTo(Vec3.ONE);
        Vec3 corner = turned.toMat4().transformPoint(Vec3.ONE);
        assertThat(turned.bounds().expand(1e-9).contains(corner)).isTrue();
        Obb placed = Obb.of(new Aabb(Vec3.splat(-1), Vec3.ONE), TRANSFORM);
        assertThat(placed.halfExtents()).isEqualTo(Vec3.splat(2));
        assertThat(Obb.of(Vec3.ZERO, Vec3.ONE, Quat.IDENTITY).transform(TRANSFORM).rotation()).isEqualTo(ROTATION);
    }

    @Test
    void obbRaysPlanesAndBoxes() {
        Obb box = Obb.of(Vec3.ZERO, Vec3.ONE, Quat.fromAxisAngle(Vec3.UP, Maths.PI / 4));
        assertThat(box.intersect(new Ray(Vec3.of(-5, 0, 0), Vec3.UNIT_X))).isCloseTo(5 - Math.sqrt(2), within(EPS));
        assertThat(box.intersect(new Ray(Vec3.of(-5, 3, 0), Vec3.UNIT_X))).isEqualTo(MISS);
        assertThat(box.inFrontOf(new Plane(Vec3.UNIT_X, 1.4))).isTrue();
        assertThat(box.inFrontOf(new Plane(Vec3.UNIT_X, 1.5))).isFalse();
        assertThat(box.intersects(new Aabb(Vec3.of(1.3, -1, -1), Vec3.of(3, 1, 1)))).isTrue();
        assertThat(box.intersects(new Aabb(Vec3.of(1.5, -1, -1), Vec3.of(3, 1, 1)))).isFalse();
    }

    @Test
    void obbOverlapMatchesBruteForce() {
        Rng rng = Rng.seeded(12);
        int overlaps = 0;
        for (int i = 0; i < 50_000; i++) {
            Obb a = randomBox(rng);
            Obb b = randomBox(rng);
            double gap = bruteForceGap(a, b);
            if (Math.abs(gap) < 1e-6) {
                continue; // touching within rounding: either answer is right
            }
            assertThat(a.intersects(b)).as("pair %d", i).isEqualTo(gap <= 0);
            overlaps += gap <= 0 ? 1 : 0;
        }
        assertThat(overlaps).isBetween(5_000, 45_000);
    }

    private static Obb randomBox(Rng rng) {
        Vec3 extents = Vec3.of(rng.nextDouble(0.05, 2), rng.nextDouble(0.05, 2), rng.nextDouble(0.05, 2));
        return Obb.of(rng.nextVec3InSphere().scale(3), extents, rng.nextRotation());
    }

    /** Largest separation of the boxes' projections over all 15 candidate axes; positive means separated. */
    private static double bruteForceGap(Obb a, Obb b) {
        Vec3[] axesA = {a.rotation().right(), a.rotation().up(), a.rotation().rotate(Vec3.UNIT_Z)};
        Vec3[] axesB = {b.rotation().right(), b.rotation().up(), b.rotation().rotate(Vec3.UNIT_Z)};
        double gap = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 3; i++) {
            gap = Math.max(gap, separation(a, b, axesA[i]));
            gap = Math.max(gap, separation(a, b, axesB[i]));
            for (int j = 0; j < 3; j++) {
                Vec3 axis = axesA[i].cross(axesB[j]);
                if (axis.lengthSquared() > 1e-12) {
                    gap = Math.max(gap, separation(a, b, axis.normalize()));
                }
            }
        }
        return gap;
    }

    private static double separation(Obb a, Obb b, Vec3 axis) {
        double[] ra = projection(a, axis);
        double[] rb = projection(b, axis);
        return Math.max(ra[0] - rb[1], rb[0] - ra[1]);
    }

    private static double[] projection(Obb box, Vec3 axis) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 local = Vec3.of((corner & 1) == 0 ? -1 : 1, (corner & 2) == 0 ? -1 : 1, (corner & 4) == 0 ? -1 : 1);
            double d = box.toMat4().transformPoint(local).dot(axis);
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        return new double[] {min, max};
    }

    // ---------------------------------------------------------------- frustums

    @Test
    void frustumCulling() {
        Mat4 view = Mat4.lookAt(Vec3.of(0, 0, 5), Vec3.ZERO, Vec3.UP);
        Frustum gl = Frustum.fromMatrix(Mat4.perspective(Maths.HALF_PI, 1, 1, 100).mul(view));
        Frustum zo = Frustum.fromMatrixZeroToOne(Mat4.perspectiveZeroToOne(Maths.HALF_PI, 1, 1, 100).mul(view));
        Vec3[] outside = {Vec3.of(-20, 0, 0), Vec3.of(20, 0, 0), Vec3.of(0, -20, 0), Vec3.of(0, 20, 0),
            Vec3.of(0, 0, 4.5), Vec3.of(0, 0, -200)};
        for (Frustum frustum : new Frustum[] {gl, zo}) {
            assertThat(frustum.contains(Vec3.ZERO)).isTrue();
            assertThat(frustum.intersects(new Sphere(Vec3.ZERO, 1))).isTrue();
            assertThat(frustum.intersects(new Aabb(Vec3.splat(-1), Vec3.ONE))).isTrue();
            assertThat(frustum.intersects(Obb.of(Vec3.ZERO, Vec3.ONE, ROTATION))).isTrue();
            for (Vec3 p : outside) {
                assertThat(frustum.contains(p)).as("point %s", p).isFalse();
                assertThat(frustum.intersects(new Sphere(p, 0.1))).as("sphere %s", p).isFalse();
                assertThat(frustum.intersects(Aabb.fromCenterExtents(p, Vec3.splat(0.1)))).as("box %s", p).isFalse();
                assertThat(frustum.intersects(Obb.of(p, Vec3.splat(0.1), ROTATION))).as("obb %s", p).isFalse();
            }
            // A large sphere just outside the left plane's corner region still touches.
            assertThat(frustum.intersectsSphere(Vec3.of(-6, 0, 0), 1.5)).isTrue();
            assertThat(frustum.intersectsAabb(Vec3.of(-6, -1, -1), Vec3.of(-4, 1, 1))).isTrue();
        }
    }
}
