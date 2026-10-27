package com.truesim.common.math.geometry;

import com.truesim.common.math.JitInlining;
import com.truesim.common.math.Mat4;
import com.truesim.common.math.Quat;
import com.truesim.common.math.Transform;
import com.truesim.common.math.Vec3;

/**
 * Immutable oriented bounding box: a box of {@code halfExtents} around {@code center}, rotated by {@code rotation}.
 * Fits rotated objects much tighter than an {@link Aabb}.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Obb(Vec3 center, Vec3 halfExtents, Quat rotation) {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    public static Obb of(Vec3 center, Vec3 halfExtents, Quat rotation) {
        return new Obb(center, halfExtents, rotation);
    }

    /** The world-space box of a local axis-aligned box placed by {@code transform}. */
    public static Obb of(Aabb local, Transform transform) {
        return new Obb(transform.transformPoint(local.center()), local.extents().mul(transform.scale().abs()),
            transform.rotation());
    }

    /** An axis-aligned box as an oriented box. */
    public static Obb of(Aabb box) {
        return new Obb(box.center(), box.extents(), Quat.IDENTITY);
    }

    /** This box moved into world space with {@code transform}. */
    public Obb transform(Transform transform) {
        return new Obb(transform.transformPoint(center), halfExtents.mul(transform.scale().abs()),
            transform.rotation().mul(rotation));
    }

    /** The matrix that maps the cube from (-1, -1, -1) to (1, 1, 1) onto this box. */
    public Mat4 toMat4() {
        return Mat4.trs(center, rotation, halfExtents);
    }

    /** The smallest axis-aligned box containing this box. */
    public Aabb bounds() {
        Vec3 extent = rotation.right().scale(halfExtents.x()).abs()
            .add(rotation.up().scale(halfExtents.y()).abs())
            .add(rotation.rotate(Vec3.UNIT_Z).scale(halfExtents.z()).abs());
        return Aabb.fromCenterExtents(center, extent);
    }

    public boolean contains(Vec3 p) {
        return Intersections.pointObb(p, center, halfExtents, rotation);
    }

    /** The closest point in the box. */
    public Vec3 closestPoint(Vec3 p) {
        Vec3 local = rotation.conjugate().rotate(p.sub(center)).clamp(halfExtents.negate(), halfExtents);
        return center.add(rotation.rotate(local));
    }

    public boolean intersects(Obb other) {
        return Intersections.obbObb(center, halfExtents, rotation, other.center, other.halfExtents, other.rotation);
    }

    public boolean intersects(Aabb box) {
        return Intersections.obbObb(center, halfExtents, rotation, box.center(), box.extents(), Quat.IDENTITY);
    }

    /** Whether any part of the box is in front of {@code plane}. */
    public boolean inFrontOf(Plane plane) {
        return Intersections.obbPlane(center, halfExtents, rotation, plane.normal(), plane.distance());
    }

    /** Where the ray enters the box (0 if it starts inside), or {@link Double#POSITIVE_INFINITY} if it misses. */
    public double intersect(Ray ray) {
        return Intersections.rayObb(ray.origin(), ray.direction(), center, halfExtents, rotation);
    }
}
