package com.truesim.common.math.color;

import com.truesim.common.math.JitInlining;
import com.truesim.common.math.Linear;
import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

/**
 * Immutable color in linear RGB with straight (not premultiplied) alpha. Components are nominally in [0, 1]; values
 * above 1 are allowed for HDR.
 *
 * <p>Arithmetic and {@link #interpolate} work in linear RGB, which is physically correct for lighting. Use
 * {@link #mix(Color, double, ColorSpace)} for perceptual blends.
 *
 * <p>Value-based: do not use {@code ==} or synchronize on instances.
 */
public record Color(double r, double g, double b, double a) implements Linear<Color> {

    static {
        JitInlining.install(); // allocation-free on HotSpot, see JitInlining
    }

    public static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    public static final Color BLACK = new Color(0, 0, 0, 1);
    public static final Color WHITE = new Color(1, 1, 1, 1);
    public static final Color RED = new Color(1, 0, 0, 1);
    public static final Color GREEN = new Color(0, 1, 0, 1);
    public static final Color BLUE = new Color(0, 0, 1, 1);

    /** Memory layout: four doubles r, g, b, a (32 bytes). */
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE.withName("r"), ValueLayout.JAVA_DOUBLE.withName("g"),
        ValueLayout.JAVA_DOUBLE.withName("b"), ValueLayout.JAVA_DOUBLE.withName("a"));
    /** Memory layout for GPUs: four floats r, g, b, a (16 bytes). */
    public static final StructLayout FLOAT_LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_FLOAT.withName("r"), ValueLayout.JAVA_FLOAT.withName("g"),
        ValueLayout.JAVA_FLOAT.withName("b"), ValueLayout.JAVA_FLOAT.withName("a"));
    /** Memory layout for 8-bit textures: four sRGB-encoded bytes r, g, b, a (4 bytes). */
    public static final StructLayout RGBA8_LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_BYTE.withName("r"), ValueLayout.JAVA_BYTE.withName("g"),
        ValueLayout.JAVA_BYTE.withName("b"), ValueLayout.JAVA_BYTE.withName("a"));

    private static final ValueLayout.OfDouble DOUBLE = ValueLayout.JAVA_DOUBLE_UNALIGNED;
    private static final ValueLayout.OfFloat FLOAT = ValueLayout.JAVA_FLOAT_UNALIGNED;

    // ---------------------------------------------------------------- factories

    /** Opaque color from linear components. */
    public static Color rgb(double r, double g, double b) {
        return new Color(r, g, b, 1);
    }

    /** Color from linear components. */
    public static Color rgba(double r, double g, double b, double a) {
        return new Color(r, g, b, a);
    }

    /** Opaque color from sRGB hex as used in CSS and image editors, e.g. {@code 0xFF8800}. */
    public static Color srgb(int rgb) {
        return srgba((rgb << 8) | 0xff);
    }

    /** Color from sRGB hex with alpha, e.g. {@code 0xFF880080}. */
    public static Color srgba(int rgba) {
        return new Color(
            srgbToLinear(((rgba >>> 24) & 0xff) / 255.0),
            srgbToLinear(((rgba >>> 16) & 0xff) / 255.0),
            srgbToLinear(((rgba >>> 8) & 0xff) / 255.0),
            (rgba & 0xff) / 255.0);
    }

    /** Opaque color from gamma-encoded sRGB components in [0, 1]. */
    public static Color srgb(double r, double g, double b) {
        return new Color(srgbToLinear(r), srgbToLinear(g), srgbToLinear(b), 1);
    }

    /**
     * Opaque color from HSV in sRGB space.
     *
     * @param hue degrees, any value (wrapped into [0, 360))
     * @param saturation in [0, 1]
     * @param value in [0, 1]
     */
    public static Color hsv(double hue, double saturation, double value) {
        double h = Maths.wrap(hue, 0, 360) / 60;
        double c = value * saturation;
        double x = c * (1 - Math.abs(h % 2 - 1));
        double m = value - c;
        return switch ((int) h) {
            case 0 -> srgb(c + m, x + m, m);
            case 1 -> srgb(x + m, c + m, m);
            case 2 -> srgb(m, c + m, x + m);
            case 3 -> srgb(m, x + m, c + m);
            case 4 -> srgb(x + m, m, c + m);
            default -> srgb(c + m, m, x + m);
        };
    }

    /** Opaque color from Oklab lightness {@code l} in [0, 1] and opponent axes {@code a} (green-red), {@code b} (blue-yellow). */
    public static Color oklab(double l, double a, double b) {
        double l1 = l + 0.3963377774 * a + 0.2158037573 * b;
        double m1 = l - 0.1055613458 * a - 0.0638541728 * b;
        double s1 = l - 0.0894841775 * a - 1.2914855480 * b;
        double lc = l1 * l1 * l1;
        double mc = m1 * m1 * m1;
        double sc = s1 * s1 * s1;
        return new Color(
            4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc,
            -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc,
            -0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc,
            1);
    }

    /** Opaque color from Oklch: lightness, chroma and hue in degrees. */
    public static Color oklch(double l, double chroma, double hue) {
        double radians = Maths.toRadians(hue);
        return oklab(l, chroma * Maths.cos(radians), chroma * Maths.sin(radians));
    }

    // ---------------------------------------------------------------- transfer functions

    /** Decodes one sRGB component to linear. */
    public static double srgbToLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Maths.pow((c + 0.055) / 1.055, 2.4);
    }

    /** Encodes one linear component to sRGB. */
    public static double linearToSrgb(double c) {
        return c <= 0.0031308 ? 12.92 * c : 1.055 * Maths.pow(c, 1 / 2.4) - 0.055;
    }

    // ---------------------------------------------------------------- conversions

    /** Gamma-encoded sRGB components (r, g, b). */
    public Vec3 toSrgb() {
        return new Vec3(linearToSrgb(r), linearToSrgb(g), linearToSrgb(b));
    }

    /** sRGB hex without alpha, e.g. {@code 0xFF8800}. Components are clamped to [0, 1]. */
    public int toSrgbInt() {
        return toSrgbaInt() >>> 8;
    }

    /** sRGB hex with alpha, e.g. {@code 0xFF880080}. Components are clamped to [0, 1]. */
    public int toSrgbaInt() {
        return (toByte(linearToSrgb(r)) << 24) | (toByte(linearToSrgb(g)) << 16) | (toByte(linearToSrgb(b)) << 8) | toByte(a);
    }

    /** HSV in sRGB space: (hue in degrees [0, 360), saturation, value). */
    public Vec3 toHsv() {
        Vec3 srgb = toSrgb();
        double max = Math.max(srgb.x(), Math.max(srgb.y(), srgb.z()));
        double min = Math.min(srgb.x(), Math.min(srgb.y(), srgb.z()));
        double delta = max - min;
        double hue;
        if (delta == 0) {
            hue = 0;
        } else if (max == srgb.x()) {
            hue = 60 * Maths.wrap((srgb.y() - srgb.z()) / delta, 0, 6);
        } else if (max == srgb.y()) {
            hue = 60 * ((srgb.z() - srgb.x()) / delta + 2);
        } else {
            hue = 60 * ((srgb.x() - srgb.y()) / delta + 4);
        }
        return new Vec3(hue, max == 0 ? 0 : delta / max, max);
    }

    /** Oklab (lightness, a, b). */
    public Vec3 toOklab() {
        double l = Maths.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Maths.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Maths.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new Vec3(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s);
    }

    /** Oklch (lightness, chroma, hue in degrees [0, 360)). */
    public Vec3 toOklch() {
        Vec3 lab = toOklab();
        double hue = Maths.wrap(Maths.toDegrees(Maths.atan2(lab.z(), lab.y())), 0, 360);
        return new Vec3(lab.x(), Maths.hypot(lab.y(), lab.z()), hue);
    }

    // ---------------------------------------------------------------- arithmetic (linear RGB, including alpha)

    @Override
    public Color add(Color other) {
        return new Color(r + other.r, g + other.g, b + other.b, a + other.a);
    }

    @Override
    public Color sub(Color other) {
        return new Color(r - other.r, g - other.g, b - other.b, a - other.a);
    }

    @Override
    public Color scale(double factor) {
        return new Color(r * factor, g * factor, b * factor, a * factor);
    }

    /** Component-wise product, e.g. tinting or applying light color. */
    public Color mul(Color other) {
        return new Color(r * other.r, g * other.g, b * other.b, a * other.a);
    }

    @Override
    public double dot(Color other) {
        return r * other.r + g * other.g + b * other.b + a * other.a;
    }

    @Override
    public double distance(Color other) {
        double dr = r - other.r;
        double dg = g - other.g;
        double db = b - other.b;
        double da = a - other.a;
        return Math.sqrt(dr * dr + dg * dg + db * db + da * da);
    }

    /** Linear-RGB interpolation. */
    @Override
    public Color interpolate(Color to, double t) {
        return new Color(Maths.lerp(r, to.r, t), Maths.lerp(g, to.g, t), Maths.lerp(b, to.b, t), Maths.lerp(a, to.a, t));
    }

    /** Blends towards {@code to} in the given color space; alpha always blends linearly. */
    public Color mix(Color to, double t, ColorSpace space) {
        double alpha = Maths.lerp(a, to.a, t);
        return switch (space) {
            case LINEAR -> interpolate(to, t);
            case SRGB -> {
                Vec3 from = toSrgb();
                Vec3 target = to.toSrgb();
                Vec3 mixed = from.interpolate(target, t);
                yield srgb(mixed.x(), mixed.y(), mixed.z()).withAlpha(alpha);
            }
            case OKLAB -> {
                Vec3 mixed = toOklab().interpolate(to.toOklab(), t);
                yield oklab(mixed.x(), mixed.y(), mixed.z()).withAlpha(alpha);
            }
            case OKLCH -> {
                Vec3 from = toOklch();
                Vec3 target = to.toOklch();
                double hue = Maths.toDegrees(Maths.lerpAngle(Maths.toRadians(from.z()), Maths.toRadians(target.z()), t));
                yield oklch(Maths.lerp(from.x(), target.x(), t), Maths.lerp(from.y(), target.y(), t), hue).withAlpha(alpha);
            }
        };
    }

    // ---------------------------------------------------------------- alpha and adjustments

    public Color withAlpha(double alpha) {
        return new Color(r, g, b, alpha);
    }

    /** Color channels multiplied by alpha, as used by premultiplied-alpha blending. */
    public Color premultiply() {
        return new Color(r * a, g * a, b * a, a);
    }

    /** Inverse of {@link #premultiply()}; fully transparent colors become {@link #TRANSPARENT}. */
    public Color unpremultiply() {
        return a == 0 ? TRANSPARENT : new Color(r / a, g / a, b / a, a);
    }

    /** Relative luminance (Rec. 709 / sRGB primaries). */
    public double luminance() {
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    /** All components clamped to [0, 1]. */
    public Color clamp() {
        return new Color(Maths.clamp01(r), Maths.clamp01(g), Maths.clamp01(b), Maths.clamp01(a));
    }

    public boolean approxEquals(Color other, double epsilon) {
        return Maths.approxEquals(r, other.r, epsilon)
            && Maths.approxEquals(g, other.g, epsilon)
            && Maths.approxEquals(b, other.b, epsilon)
            && Maths.approxEquals(a, other.a, epsilon);
    }

    // ---------------------------------------------------------------- memory

    public void write(MemorySegment segment, long offset) {
        segment.set(DOUBLE, offset, r);
        segment.set(DOUBLE, offset + 8, g);
        segment.set(DOUBLE, offset + 16, b);
        segment.set(DOUBLE, offset + 24, a);
    }

    public static Color read(MemorySegment segment, long offset) {
        return new Color(
            segment.get(DOUBLE, offset), segment.get(DOUBLE, offset + 8),
            segment.get(DOUBLE, offset + 16), segment.get(DOUBLE, offset + 24));
    }

    /** Writes linear r, g, b, a as floats, e.g. for vertex colors or HDR textures. */
    public void writeFloats(MemorySegment segment, long offset) {
        segment.set(FLOAT, offset, (float) r);
        segment.set(FLOAT, offset + 4, (float) g);
        segment.set(FLOAT, offset + 8, (float) b);
        segment.set(FLOAT, offset + 12, (float) a);
    }

    public static Color readFloats(MemorySegment segment, long offset) {
        return new Color(
            segment.get(FLOAT, offset), segment.get(FLOAT, offset + 4),
            segment.get(FLOAT, offset + 8), segment.get(FLOAT, offset + 12));
    }

    /** Writes sRGB-encoded r, g, b and linear alpha as four bytes (an {@code RGBA8} texel). */
    public void writeRgba8(MemorySegment segment, long offset) {
        segment.set(ValueLayout.JAVA_BYTE, offset, (byte) toByte(linearToSrgb(r)));
        segment.set(ValueLayout.JAVA_BYTE, offset + 1, (byte) toByte(linearToSrgb(g)));
        segment.set(ValueLayout.JAVA_BYTE, offset + 2, (byte) toByte(linearToSrgb(b)));
        segment.set(ValueLayout.JAVA_BYTE, offset + 3, (byte) toByte(a));
    }

    public static Color readRgba8(MemorySegment segment, long offset) {
        return new Color(
            srgbToLinear((segment.get(ValueLayout.JAVA_BYTE, offset) & 0xff) / 255.0),
            srgbToLinear((segment.get(ValueLayout.JAVA_BYTE, offset + 1) & 0xff) / 255.0),
            srgbToLinear((segment.get(ValueLayout.JAVA_BYTE, offset + 2) & 0xff) / 255.0),
            (segment.get(ValueLayout.JAVA_BYTE, offset + 3) & 0xff) / 255.0);
    }

    public void write(ByteBuffer buffer, int byteOffset) {
        buffer.putDouble(byteOffset, r).putDouble(byteOffset + 8, g)
            .putDouble(byteOffset + 16, b).putDouble(byteOffset + 24, a);
    }

    public static Color read(ByteBuffer buffer, int byteOffset) {
        return new Color(buffer.getDouble(byteOffset), buffer.getDouble(byteOffset + 8),
            buffer.getDouble(byteOffset + 16), buffer.getDouble(byteOffset + 24));
    }

    public void write(DoubleBuffer buffer, int index) {
        buffer.put(index, r).put(index + 1, g).put(index + 2, b).put(index + 3, a);
    }

    public static Color read(DoubleBuffer buffer, int index) {
        return new Color(buffer.get(index), buffer.get(index + 1), buffer.get(index + 2), buffer.get(index + 3));
    }

    private static int toByte(double c) {
        return (int) Math.round(Maths.clamp01(c) * 255);
    }
}
