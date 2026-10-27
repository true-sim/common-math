package com.truesim.common.math.numeric;

import com.truesim.common.math.Linear;
import com.truesim.common.math.interpolation.Motion;

/**
 * Numerical integration of motion: advances position and velocity by a time step under an {@link Acceleration}.
 *
 * <pre>{@code
 * Acceleration<Vec3> gravity = (position, velocity, time) -> Vec3.of(0, -9.81, 0);
 * body = Integrator.VELOCITY_VERLET.step(body, gravity, time, dt);
 * }</pre>
 */
public enum Integrator {

    /**
     * Velocity first, then position with the new velocity. One acceleration evaluation per step, stable for
     * oscillating systems: the usual choice for game physics.
     */
    SEMI_IMPLICIT_EULER {
        @Override
        public <T extends Linear<T>> Motion<T> step(Motion<T> motion, Acceleration<T> acceleration, double time, double dt) {
            T velocity = motion.velocity().add(acceleration.at(motion.value(), motion.velocity(), time).scale(dt));
            return new Motion<>(motion.value().add(velocity.scale(dt)), velocity);
        }
    },

    /**
     * Second-order accurate with two acceleration evaluations per step; conserves energy well, e.g. for orbits and
     * stiff springs. Velocity-dependent forces use a predicted velocity.
     */
    VELOCITY_VERLET {
        @Override
        public <T extends Linear<T>> Motion<T> step(Motion<T> motion, Acceleration<T> acceleration, double time, double dt) {
            T a0 = acceleration.at(motion.value(), motion.velocity(), time);
            T position = motion.value().add(motion.velocity().scale(dt)).add(a0.scale(0.5 * dt * dt));
            T a1 = acceleration.at(position, motion.velocity().add(a0.scale(dt)), time + dt);
            return new Motion<>(position, motion.velocity().add(a0.add(a1).scale(0.5 * dt)));
        }
    },

    /** Classic fourth-order Runge-Kutta: four evaluations per step, very accurate for smooth forces. */
    RK4 {
        @Override
        public <T extends Linear<T>> Motion<T> step(Motion<T> motion, Acceleration<T> acceleration, double time, double dt) {
            T x = motion.value();
            T v = motion.velocity();
            double half = 0.5 * dt;
            T k1v = acceleration.at(x, v, time);
            T k1x = v;
            T k2v = acceleration.at(x.add(k1x.scale(half)), v.add(k1v.scale(half)), time + half);
            T k2x = v.add(k1v.scale(half));
            T k3v = acceleration.at(x.add(k2x.scale(half)), v.add(k2v.scale(half)), time + half);
            T k3x = v.add(k2v.scale(half));
            T k4v = acceleration.at(x.add(k3x.scale(dt)), v.add(k3v.scale(dt)), time + dt);
            T k4x = v.add(k3v.scale(dt));
            double sixth = dt / 6;
            return new Motion<>(
                x.add(k1x.add(k2x.scale(2)).add(k3x.scale(2)).add(k4x).scale(sixth)),
                v.add(k1v.add(k2v.scale(2)).add(k3v.scale(2)).add(k4v).scale(sixth)));
        }
    };

    /** Advances {@code motion} from {@code time} to {@code time + dt}. */
    public abstract <T extends Linear<T>> Motion<T> step(Motion<T> motion, Acceleration<T> acceleration, double time, double dt);
}
