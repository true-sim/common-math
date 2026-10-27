package com.truesim.common.math;

import static com.truesim.testing.AllocationAssertions.assertAllocationFree;

import com.truesim.common.math.color.Color;
import com.truesim.common.math.color.ColorArray;
import com.truesim.common.math.color.ColorSpace;
import com.truesim.common.math.interpolation.Easing;
import java.lang.foreign.Arena;
import org.junit.jupiter.api.Test;

/** Verifies that hot paths do not allocate once the JIT has compiled them (temporary values are scalar-replaced). */
class AllocationFreeTest {

    private double sink;

    @Test
    void transcendentalFunctions() {
        double[] x = {0.7};
        assertAllocationFree(() -> {
            x[0] += 1e-7;
            sink += Maths.sin(x[0]) + Maths.cos(x[0]) + Maths.atan2(x[0], 2) + Maths.exp(x[0]) + Maths.log(x[0])
                + Maths.pow(x[0], 2.3) + Maths.cbrt(x[0]);
        });
    }

    @Test
    void vectorChains() {
        Vec3 velocity = Vec3.of(1, 2, 3);
        Vec3[] position = {Vec3.ZERO};
        assertAllocationFree(() -> {
            Vec3 p = position[0];
            Vec3 next = p.addScaled(velocity, 0.016).cross(Vec3.UP).normalize().interpolate(velocity, 0.5);
            sink += next.length() + next.xz().length();
        });
    }

    @Test
    void rotationsAndMatrices() {
        Quat q = Quat.fromYawPitchRoll(0.1, 0.2, 0.3);
        Mat4 m = Mat4.trs(Vec3.of(1, 2, 3), q, Vec3.ONE);
        assertAllocationFree(() -> {
            Quat r = q.mul(q).slerp(Quat.IDENTITY, 0.3);
            Vec3 v = r.rotate(Vec3.FORWARD);
            Mat4 n = m.mul(m).inverse();
            sink += n.transformPoint(v).x() + n.determinant();
        });
    }

    @Test
    void arraysReadAndWriteMemoryDirectly() {
        try (Arena arena = Arena.ofConfined()) {
            Vec3Array positions = Vec3Array.allocate(arena, 256);
            Vec3Array velocities = Vec3Array.onHeap(256);
            velocities.fill(Vec3.of(1, 0, -1));
            Mat4Array transforms = Mat4Array.allocate(arena, 16);
            Mat4Array locals = Mat4Array.onHeap(16);
            locals.fill(Mat4.translation(Vec3.UNIT_X));
            transforms.fill(Mat4.IDENTITY);
            assertAllocationFree(() -> {
                for (int i = 0; i < positions.size(); i++) {
                    positions.set(i, positions.get(i).addScaled(velocities.get(i), 0.016));
                }
                for (int i = 1; i < transforms.size(); i++) {
                    transforms.setProduct(i, transforms, i - 1, locals, i);
                }
                locals.set(0, Mat4.rotationY(0.01)); // fresh matrices go through set, which is free
                transforms.setProduct(0, transforms, 0, locals, 0);
                transforms.transformPoints(3, positions, 0, 64);
                transforms.transformDirections(4, velocities, 0, 64);
            }, 2_000, 1_000, 20);
        }
    }

    @Test
    void transformsAndQuaternionExtras() {
        Transform parent = Transform.of(Vec3.of(1, 2, 3), Quat.fromYawPitchRoll(0.1, 0.2, 0.3), Vec3.splat(2));
        Transform local = Transform.of(Vec3.UNIT_X, Quat.fromAxisAngle(Vec3.UP, 0.5));
        TransformArray world = TransformArray.onHeap(32);
        TransformArray locals = TransformArray.onHeap(32);
        Vec3Array vertices = Vec3Array.onHeap(64);
        assertAllocationFree(() -> {
            for (int i = 1; i < world.size(); i++) {
                world.setProduct(i, world, i - 1, locals, i);
            }
            locals.set(0, local); // fresh values go through set, which is free
            world.transformPoints(5, vertices, 0, 64);
        }, 5_000, 1_000, 20);
        assertAllocationFree(() -> {
            Transform t = parent.mul(local).inverse();
            Quat q = t.rotation().integrate(Vec3.of(0, 1, 0), 0.016).rotateTowards(Quat.IDENTITY, 0.01);
            sink += t.transformPoint(Vec3.ONE).x() + q.toYawPitchRoll().y() + q.twist(Vec3.UP).w();
        }, 5_000, 1_000, 20);
    }

    @Test
    void randomAndNoise() {
        com.truesim.common.math.random.Rng rng = com.truesim.common.math.random.Rng.seeded(1);
        com.truesim.common.math.random.Noise noise = com.truesim.common.math.random.Noise.seeded(1);
        assertAllocationFree(() -> {
            Vec3 p = rng.nextVec3InSphere().add(rng.nextVec3OnSphere());
            sink += rng.nextGaussian() + rng.nextInt(10) + rng.nextRotation().w()
                + noise.simplex(p.x(), p.y(), p.z()) + noise.perlin(p.x(), p.y()) + noise.fractal(p.x(), p.z(), 4);
        });
    }

    @Test
    void geometryQueries() {
        Mat4 viewProjection = Mat4.perspective(1.2, 1.5, 0.1, 500).mul(Mat4.lookAt(Vec3.of(0, 2, 10), Vec3.ZERO, Vec3.UP));
        com.truesim.common.math.geometry.Frustum frustum = com.truesim.common.math.geometry.Frustum.fromMatrix(viewProjection);
        Vec3Array centers = Vec3Array.onHeap(256);
        for (int i = 0; i < centers.size(); i++) {
            centers.set(i, Vec3.of(i % 16 - 8, 0, i / 16 - 8));
        }
        com.truesim.common.math.geometry.Triangle triangle = new com.truesim.common.math.geometry.Triangle(
            Vec3.ZERO, Vec3.UNIT_X, Vec3.UNIT_Y);
        assertAllocationFree(() -> {
            int visible = 0;
            for (int i = 0; i < centers.size(); i++) {
                Vec3 c = centers.get(i);
                if (frustum.intersectsSphere(c, 0.5) && frustum.intersectsAabb(c.sub(0.5, 0.5, 0.5), c.add(0.5, 0.5, 0.5))) {
                    visible++;
                }
            }
            sink += visible;
        }, 2_000, 1_000, 20);
        assertAllocationFree(() -> {
            com.truesim.common.math.geometry.Ray ray = new com.truesim.common.math.geometry.Ray(Vec3.of(0.2, 0.2, 5), Vec3.FORWARD);
            sink += triangle.intersect(ray) + new com.truesim.common.math.geometry.Aabb(Vec3.ZERO, Vec3.ONE).intersect(ray)
                + com.truesim.common.math.geometry.Intersections.rayObb(0.2, 0.2, 5, 0, 0, -1, 0, 0, 0, 1, 1, 1, 0, 0, 0, 1);
        });
    }

    @Test
    void colorsAndEasing() {
        ColorArray colors = ColorArray.onHeap(64);
        colors.fill(Color.srgb(0x3366FF));
        assertAllocationFree(() -> {
            for (int i = 0; i < colors.size(); i++) {
                double t = Easing.CUBIC_IN_OUT.apply(i / 64.0);
                colors.set(i, colors.get(i).mix(Color.WHITE, t, ColorSpace.OKLAB));
            }
        }, 1_000, 500, 20);
    }
}
