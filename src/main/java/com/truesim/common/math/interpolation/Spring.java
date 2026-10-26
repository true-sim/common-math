package com.truesim.common.math.interpolation;

import com.truesim.common.math.Linear;

/**
 * Critically damped spring: smoothly follows a moving target without overshooting, like a camera that trails a car.
 * Frame-rate independent.
 *
 * <pre>{@code
 * Spring follow = new Spring(0.3);
 * Motion<Vec3> camera = Motion.at(start);
 * camera = follow.step(camera, carPosition, dt);   // every frame
 * }</pre>
 *
 * @param smoothTime approximate time to reach the target, in the same unit as {@code dt}
 */
public record Spring(double smoothTime) {

    public Spring {
        if (!(smoothTime > 0)) {
            throw new IllegalArgumentException("Smooth time must be positive: " + smoothTime);
        }
    }

    /** Advances {@code motion} towards {@code target} by {@code dt}. */
    public <T extends Linear<T>> Motion<T> step(Motion<T> motion, T target, double dt) {
        double omega = 2 / smoothTime;
        double x = omega * dt;
        double decay = 1 / (1 + x + 0.48 * x * x + 0.235 * x * x * x); // approximates exp(-x)
        T change = motion.value().sub(target);
        T temp = motion.velocity().add(change.scale(omega)).scale(dt);
        T velocity = motion.velocity().sub(temp.scale(omega)).scale(decay);
        T value = target.add(change.add(temp).scale(decay));
        return new Motion<>(value, velocity);
    }
}
