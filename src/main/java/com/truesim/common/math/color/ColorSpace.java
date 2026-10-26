package com.truesim.common.math.color;

/** Color space in which {@link Color#mix} blends. */
public enum ColorSpace {
    /** Linear RGB: physically correct light mixing; can look too bright in the middle. */
    LINEAR,
    /** Gamma-encoded sRGB: how most image editors and CSS blend by default. */
    SRGB,
    /** Oklab: perceptually even blends without hue shifts or dark/grey middles. */
    OKLAB,
    /** Oklch: Oklab in polar form, blending hue along the shortest arc (vivid gradients). */
    OKLCH
}
