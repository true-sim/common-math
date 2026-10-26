package com.truesim.common.math.interpolation;

/**
 * Maps progress {@code t} in [0, 1] to eased progress, usually with {@code f(0) = 0} and {@code f(1) = 1}.
 * Combine with any value: {@code from.interpolate(to, Easing.BACK_OUT.apply(t))}.
 */
@FunctionalInterface
public interface Easing {

    double apply(double t);

    Easing LINEAR = Easings::linear;
    Easing QUAD_IN = Easings::quadIn;
    Easing QUAD_OUT = Easings::quadOut;
    Easing QUAD_IN_OUT = Easings::quadInOut;
    Easing CUBIC_IN = Easings::cubicIn;
    Easing CUBIC_OUT = Easings::cubicOut;
    Easing CUBIC_IN_OUT = Easings::cubicInOut;
    Easing QUART_IN = Easings::quartIn;
    Easing QUART_OUT = Easings::quartOut;
    Easing QUART_IN_OUT = Easings::quartInOut;
    Easing QUINT_IN = Easings::quintIn;
    Easing QUINT_OUT = Easings::quintOut;
    Easing QUINT_IN_OUT = Easings::quintInOut;
    Easing SINE_IN = Easings::sineIn;
    Easing SINE_OUT = Easings::sineOut;
    Easing SINE_IN_OUT = Easings::sineInOut;
    Easing EXPO_IN = Easings::expoIn;
    Easing EXPO_OUT = Easings::expoOut;
    Easing EXPO_IN_OUT = Easings::expoInOut;
    Easing CIRC_IN = Easings::circIn;
    Easing CIRC_OUT = Easings::circOut;
    Easing CIRC_IN_OUT = Easings::circInOut;
    Easing BACK_IN = Easings::backIn;
    Easing BACK_OUT = Easings::backOut;
    Easing BACK_IN_OUT = Easings::backInOut;
    Easing ELASTIC_IN = Easings::elasticIn;
    Easing ELASTIC_OUT = Easings::elasticOut;
    Easing ELASTIC_IN_OUT = Easings::elasticInOut;
    Easing BOUNCE_IN = Easings::bounceIn;
    Easing BOUNCE_OUT = Easings::bounceOut;
    Easing BOUNCE_IN_OUT = Easings::bounceInOut;

    /** CSS {@code cubic-bezier(x1, y1, x2, y2)}; {@code x1} and {@code x2} must be in [0, 1]. */
    static Easing cubicBezier(double x1, double y1, double x2, double y2) {
        return new CubicBezierEasing(x1, y1, x2, y2);
    }

    /** {@code count} equal steps, jumping at the end of each step (CSS {@code steps(count)}). */
    static Easing steps(int count) {
        return new StepsEasing(count);
    }

    /** The opposite easing, {@code 1 - f(1 - t)}: turns an "in" easing into an "out" easing and vice versa. */
    default Easing reversed() {
        return new ReversedEasing(this);
    }

    /** This easing for the first half and its reverse for the second: turns "in" into "in-out". */
    default Easing mirrored() {
        return new MirroredEasing(this);
    }
}
