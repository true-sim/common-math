/**
 * Deterministic 2D/3D math built from immutable values.
 *
 * <p>All math lives on immutable records ({@link com.truesim.common.math.Vec3}, {@link com.truesim.common.math.Quat},
 * {@link com.truesim.common.math.Mat4}, ...). They are value-based: never compare them with {@code ==} or
 * synchronize on them, so they can become Valhalla value classes without API changes. Temporary values are
 * removed by the JIT's escape analysis; state that changes every step belongs in the array types
 * ({@link com.truesim.common.math.Vec3Array}, ...), which store values directly in memory.
 *
 * <p>All functions give bit-identical results on every platform: {@link com.truesim.common.math.Maths} replaces
 * {@code java.lang.Math}.
 */
package com.truesim.common.math;
