/**
 * Easing functions and generic animation over any {@link com.truesim.common.math.Interpolatable} value:
 * {@link com.truesim.common.math.interpolation.Tween}, {@link com.truesim.common.math.interpolation.Keyframes} and
 * {@link com.truesim.common.math.interpolation.Spring}.
 *
 * <p>Easing is a plain {@code double -> double} function, so it combines with any type:
 * {@code a.interpolate(b, Easing.CUBIC_IN_OUT.apply(t))}. The generic classes are meant for animation; inner
 * simulation loops should call the concrete methods directly, which the JIT can always optimize.
 */
package com.truesim.common.math.interpolation;
