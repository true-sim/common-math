package com.truesim.common.math.curve;

import com.truesim.common.math.JitInlining;
import com.truesim.common.math.Quat;
import com.truesim.common.math.Vec3;

/**
 * A position with an orthonormal orientation along a curve: {@code forward} is the tangent, {@code up} and
 * {@code right} complete a right-handed basis ({@code right = forward x up}).
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Frame(Vec3 position, Vec3 forward, Vec3 up, Vec3 right) {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    /** The rotation that turns {@link Vec3#FORWARD} into {@code forward} and {@link Vec3#UP} into {@code up}. */
    public Quat orientation() {
        return Quat.lookRotation(forward, up);
    }

    /** This frame rolled around {@code forward} by {@code radians}, e.g. to bank a track. */
    public Frame banked(double radians) {
        Quat roll = Quat.fromAxisAngle(forward, radians);
        return new Frame(position, forward, roll.rotate(up), roll.rotate(right));
    }
}
