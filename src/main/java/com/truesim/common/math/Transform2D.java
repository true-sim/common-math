package com.truesim.common.math;

/**
 * Immutable 2D transform: scale, then rotate counter-clockwise by {@code rotation} radians, then translate.
 * Combining and inverting are exact for uniform scale.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Transform2D(Vec2 translation, double rotation, Vec2 scale) implements Interpolatable<Transform2D> {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    public static final Transform2D IDENTITY = new Transform2D(Vec2.ZERO, 0, Vec2.ONE);

    public static Transform2D of(Vec2 translation, double rotation, Vec2 scale) {
        return new Transform2D(translation, rotation, scale);
    }

    /** Rigid transform: translation and rotation, unit scale. */
    public static Transform2D of(Vec2 translation, double rotation) {
        return new Transform2D(translation, rotation, Vec2.ONE);
    }

    public Vec2 transformPoint(Vec2 point) {
        return translation.add(point.mul(scale).rotate(rotation));
    }

    /** Applies rotation and scale, but not translation. */
    public Vec2 transformDirection(Vec2 direction) {
        return direction.mul(scale).rotate(rotation);
    }

    public Vec2 inverseTransformPoint(Vec2 point) {
        return point.sub(translation).rotate(-rotation).div(scale);
    }

    /** {@code this * child}: applies {@code child} first, then this. */
    public Transform2D mul(Transform2D child) {
        return new Transform2D(transformPoint(child.translation), rotation + child.rotation, scale.mul(child.scale));
    }

    public Transform2D inverse() {
        Vec2 inverseScale = Vec2.ONE.div(scale);
        return new Transform2D(translation.rotate(-rotation).mul(inverseScale).negate(), -rotation, inverseScale);
    }

    /** As a 3x3 affine matrix (see {@link Mat3#transformPoint2D}). */
    public Mat3 toMat3() {
        return Mat3.translation2D(translation).mul(Mat3.rotation2D(rotation)).mul(Mat3.scaling2D(scale));
    }

    /** Linear translation and scale; rotation along the shortest arc. */
    @Override
    public Transform2D interpolate(Transform2D to, double t) {
        return new Transform2D(
            translation.interpolate(to.translation, t),
            Maths.lerpAngle(rotation, to.rotation, t),
            scale.interpolate(to.scale, t));
    }

    public boolean approxEquals(Transform2D other, double epsilon) {
        return translation.approxEquals(other.translation, epsilon)
            && Math.abs(Maths.deltaAngle(rotation, other.rotation)) <= epsilon
            && scale.approxEquals(other.scale, epsilon);
    }
}
