package com.truesim.common.math;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;

/**
 * Immutable 3D transform: scale, then rotate, then translate. Lighter and more precise than a {@link Mat4} for scene
 * hierarchies and rigid bodies. Combining and inverting are exact for uniform scale; with non-uniform scale under
 * rotation the result cannot represent shear and is an approximation (use {@link Mat4} for that).
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Transform(Vec3 translation, Quat rotation, Vec3 scale) implements Interpolatable<Transform> {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    public static final Transform IDENTITY = new Transform(Vec3.ZERO, Quat.IDENTITY, Vec3.ONE);

    /** Memory layout: translation (3 doubles), rotation (4 doubles), scale (3 doubles); 80 bytes. */
    public static final StructLayout LAYOUT = Memory.doubles("tx", "ty", "tz", "rx", "ry", "rz", "rw", "sx", "sy", "sz");

    public static Transform of(Vec3 translation, Quat rotation, Vec3 scale) {
        return new Transform(translation, rotation, scale);
    }

    /** Rigid transform: translation and rotation, unit scale. */
    public static Transform of(Vec3 translation, Quat rotation) {
        return new Transform(translation, rotation, Vec3.ONE);
    }

    public static Transform ofTranslation(Vec3 translation) {
        return new Transform(translation, Quat.IDENTITY, Vec3.ONE);
    }

    public static Transform ofRotation(Quat rotation) {
        return new Transform(Vec3.ZERO, rotation, Vec3.ONE);
    }

    /** Decomposes a translate-rotate-scale matrix (shear is dropped). */
    public static Transform fromMatrix(Mat4 matrix) {
        return new Transform(matrix.extractTranslation(), matrix.extractRotation(), matrix.extractScale());
    }

    // ---------------------------------------------------------------- application

    public Vec3 transformPoint(Vec3 point) {
        return translation.add(rotation.rotate(point.mul(scale)));
    }

    /** Applies rotation and scale, but not translation. */
    public Vec3 transformDirection(Vec3 direction) {
        return rotation.rotate(direction.mul(scale));
    }

    /** Maps a world point back into this transform's local space. */
    public Vec3 inverseTransformPoint(Vec3 point) {
        return rotation.conjugate().rotate(point.sub(translation)).div(scale);
    }

    // ---------------------------------------------------------------- composition

    /** {@code this * child}: applies {@code child} first, then this. For a scene graph, {@code world = parent.mul(local)}. */
    public Transform mul(Transform child) {
        return new Transform(transformPoint(child.translation), rotation.mul(child.rotation), scale.mul(child.scale));
    }

    public Transform inverse() {
        Quat inverseRotation = rotation.conjugate();
        Vec3 inverseScale = Vec3.ONE.div(scale);
        return new Transform(inverseRotation.rotate(translation).mul(inverseScale).negate(), inverseRotation, inverseScale);
    }

    public Transform withTranslation(Vec3 translation) {
        return new Transform(translation, rotation, scale);
    }

    public Transform withRotation(Quat rotation) {
        return new Transform(translation, rotation, scale);
    }

    public Transform withScale(Vec3 scale) {
        return new Transform(translation, rotation, scale);
    }

    /** The rotated {@link Vec3#FORWARD} axis. */
    public Vec3 forward() {
        return rotation.forward();
    }

    public Mat4 toMat4() {
        return Mat4.trs(translation, rotation, scale);
    }

    /** Linear translation and scale, spherical rotation. */
    @Override
    public Transform interpolate(Transform to, double t) {
        return new Transform(
            translation.interpolate(to.translation, t), rotation.slerp(to.rotation, t), scale.interpolate(to.scale, t));
    }

    public boolean approxEquals(Transform other, double epsilon) {
        return translation.approxEquals(other.translation, epsilon)
            && rotation.approxSameRotation(other.rotation, epsilon)
            && scale.approxEquals(other.scale, epsilon);
    }

    // ---------------------------------------------------------------- memory

    public void write(MemorySegment segment, long offset) {
        translation.write(segment, offset);
        rotation.write(segment, offset + 24);
        scale.write(segment, offset + 56);
    }

    public static Transform read(MemorySegment segment, long offset) {
        return new Transform(Vec3.read(segment, offset), Quat.read(segment, offset + 24), Vec3.read(segment, offset + 56));
    }
}
