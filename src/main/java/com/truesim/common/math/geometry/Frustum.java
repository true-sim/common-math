package com.truesim.common.math.geometry;

import com.truesim.common.math.JitInlining;
import com.truesim.common.math.Mat4;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.Vec4;

/**
 * Immutable view frustum: six planes whose normals point inwards, for visibility culling.
 *
 * <pre>{@code
 * Frustum frustum = Frustum.fromMatrix(projection.mul(view));
 * if (frustum.intersects(object.bounds())) { draw(object); }
 * }</pre>
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Frustum(Plane left, Plane right, Plane bottom, Plane top, Plane near, Plane far) {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    /** The frustum of a view-projection matrix with depth in [-1, 1] (OpenGL; see {@link Mat4#perspective}). */
    public static Frustum fromMatrix(Mat4 viewProjection) {
        Vec4 r0 = viewProjection.row(0);
        Vec4 r1 = viewProjection.row(1);
        Vec4 r2 = viewProjection.row(2);
        Vec4 r3 = viewProjection.row(3);
        return new Frustum(plane(r3.add(r0)), plane(r3.sub(r0)), plane(r3.add(r1)), plane(r3.sub(r1)),
            plane(r3.add(r2)), plane(r3.sub(r2)));
    }

    /** The frustum of a view-projection matrix with depth in [0, 1] (Vulkan, Direct3D, Metal). */
    public static Frustum fromMatrixZeroToOne(Mat4 viewProjection) {
        Vec4 r0 = viewProjection.row(0);
        Vec4 r1 = viewProjection.row(1);
        Vec4 r2 = viewProjection.row(2);
        Vec4 r3 = viewProjection.row(3);
        return new Frustum(plane(r3.add(r0)), plane(r3.sub(r0)), plane(r3.add(r1)), plane(r3.sub(r1)),
            plane(r2), plane(r3.sub(r2)));
    }

    /** The plane {@code a x + b y + c z + d >= 0}, normalized. */
    private static Plane plane(Vec4 coefficients) {
        double length = coefficients.xyz().length();
        return new Plane(coefficients.xyz().div(length), -coefficients.w() / length);
    }

    public boolean contains(Vec3 p) {
        return left.signedDistance(p) >= 0 && right.signedDistance(p) >= 0
            && bottom.signedDistance(p) >= 0 && top.signedDistance(p) >= 0
            && near.signedDistance(p) >= 0 && far.signedDistance(p) >= 0;
    }

    /** False only if the sphere is certainly outside. */
    public boolean intersects(Sphere sphere) {
        return intersectsSphere(sphere.center(), sphere.radius());
    }

    /** {@link #intersects(Sphere)} without a {@link Sphere} object. */
    public boolean intersectsSphere(Vec3 center, double radius) {
        return inFront(left, center, radius) && inFront(right, center, radius)
            && inFront(bottom, center, radius) && inFront(top, center, radius)
            && inFront(near, center, radius) && inFront(far, center, radius);
    }

    /** False only if the box is certainly outside (it may report boxes near a corner as intersecting). */
    public boolean intersects(Aabb box) {
        return intersectsAabb(box.min(), box.max());
    }

    /** {@link #intersects(Aabb)} without an {@link Aabb} object. */
    public boolean intersectsAabb(Vec3 min, Vec3 max) {
        return inFront(left, min, max) && inFront(right, min, max) && inFront(bottom, min, max)
            && inFront(top, min, max) && inFront(near, min, max) && inFront(far, min, max);
    }

    /** False only if the oriented box is certainly outside. */
    public boolean intersects(Obb box) {
        return box.inFrontOf(left) && box.inFrontOf(right) && box.inFrontOf(bottom) && box.inFrontOf(top)
            && box.inFrontOf(near) && box.inFrontOf(far);
    }

    private static boolean inFront(Plane plane, Vec3 center, double radius) {
        return Intersections.spherePlane(center, radius, plane.normal(), plane.distance());
    }

    private static boolean inFront(Plane plane, Vec3 min, Vec3 max) {
        return Intersections.aabbPlane(min, max, plane.normal(), plane.distance());
    }
}
