package com.truesim.common.math.spatial;

/**
 * Mutable result of {@link Bvh#raycast}: the nearest primitive hit and its distance along the ray. Reuse one
 * instance per thread so raycasts allocate nothing.
 */
public final class RayHit {

    private int primitive = -1;
    private double distance = Double.POSITIVE_INFINITY;

    /** Index of the primitive hit, or -1 if nothing was hit. */
    public int primitive() {
        return primitive;
    }

    /** Ray parameter of the hit, or {@link Double#POSITIVE_INFINITY} if nothing was hit. */
    public double distance() {
        return distance;
    }

    public boolean isHit() {
        return primitive >= 0;
    }

    void set(int primitive, double distance) {
        this.primitive = primitive;
        this.distance = distance;
    }
}
