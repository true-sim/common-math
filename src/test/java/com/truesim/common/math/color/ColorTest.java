package com.truesim.common.math.color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.truesim.common.math.Vec3;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import org.junit.jupiter.api.Test;

class ColorTest {

    private static final double EPS = 1e-9;
    private final Color orange = Color.srgb(0xFF8800);

    @Test
    void srgbRoundTrip() {
        assertThat(orange.toSrgbInt()).isEqualTo(0xFF8800);
        assertThat(Color.srgba(0xFF880080).toSrgbaInt()).isEqualTo(0xFF880080);
        assertThat(Color.srgb(1.0, 0.0, 0.0)).isEqualTo(Color.RED);
        assertThat(Color.srgbToLinear(0.5)).isCloseTo(0.21404114048223255, within(EPS));
        assertThat(Color.srgbToLinear(0.01)).isEqualTo(0.01 / 12.92);
        assertThat(Color.linearToSrgb(0.001)).isCloseTo(0.01292, within(1e-15));
        assertThat(Color.linearToSrgb(Color.srgbToLinear(0.7))).isCloseTo(0.7, within(EPS));
        assertThat(Color.WHITE.toSrgb().approxEquals(Vec3.ONE, 1e-15)).isTrue();
        assertThat(Color.rgb(2, -1, 0.5).toSrgbInt() & 0xFF0000).isEqualTo(0xFF0000); // clamped
    }

    @Test
    void factories() {
        assertThat(Color.rgb(0.1, 0.2, 0.3)).isEqualTo(Color.rgba(0.1, 0.2, 0.3, 1));
        assertThat(Color.TRANSPARENT.a()).isEqualTo(0.0);
        assertThat(Color.BLACK.add(Color.GREEN).add(Color.BLUE).g()).isEqualTo(1.0);
    }

    @Test
    void hsvAllSectors() {
        int[] expected = {0xFF0000, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0000FF, 0xFF00FF};
        for (int sector = 0; sector < 6; sector++) {
            assertThat(Color.hsv(60 * sector, 1, 1).toSrgbInt()).isEqualTo(expected[sector]);
        }
        assertThat(Color.hsv(-60, 1, 1).toSrgbInt()).isEqualTo(0xFF00FF);
        assertThat(Color.hsv(30, 1, 1).toHsv().approxEquals(Vec3.of(30, 1, 1), EPS)).isTrue();
        assertThat(Color.hsv(120, 0.5, 0.8).toHsv().approxEquals(Vec3.of(120, 0.5, 0.8), EPS)).isTrue();
        assertThat(Color.hsv(240, 0.5, 0.8).toHsv().approxEquals(Vec3.of(240, 0.5, 0.8), EPS)).isTrue();
        assertThat(Color.hsv(330, 1, 1).toHsv().approxEquals(Vec3.of(330, 1, 1), EPS)).isTrue();
        assertThat(Color.WHITE.scale(0.5).withAlpha(1).toHsv().y()).isEqualTo(0.0);
        assertThat(Color.BLACK.toHsv()).isEqualTo(Vec3.ZERO);
    }

    @Test
    void oklab() {
        assertThat(Color.WHITE.toOklab().approxEquals(Vec3.of(1, 0, 0), 1e-4)).isTrue();
        assertThat(Color.oklab(1, 0, 0).approxEquals(Color.WHITE, 1e-4)).isTrue();
        Vec3 lch = orange.toOklch();
        assertThat(Color.oklch(lch.x(), lch.y(), lch.z()).approxEquals(orange, 1e-7)).isTrue();
        assertThat(Color.BLUE.toOklch().z()).isBetween(0.0, 360.0);
    }

    @Test
    void mixing() {
        Color mid = Color.BLACK.mix(Color.WHITE, 0.5, ColorSpace.LINEAR);
        assertThat(mid).isEqualTo(Color.BLACK.interpolate(Color.WHITE, 0.5));
        assertThat(Color.BLACK.mix(Color.WHITE, 0.5, ColorSpace.SRGB).toSrgb().x()).isCloseTo(0.5, within(EPS));
        Color oklab = Color.RED.mix(Color.BLUE.withAlpha(0), 0.5, ColorSpace.OKLAB);
        assertThat(oklab.a()).isEqualTo(0.5);
        Color oklch = Color.RED.mix(Color.BLUE, 0.5, ColorSpace.OKLCH);
        assertThat(oklch.toOklch().y()).isGreaterThan(oklab.toOklch().y()); // keeps chroma
        assertThat(Color.RED.mix(Color.BLUE, 0, ColorSpace.OKLCH).approxEquals(Color.RED, 1e-7)).isTrue();
    }

    @Test
    void arithmetic() {
        Color c = Color.rgba(0.2, 0.4, 0.6, 0.5);
        assertThat(c.add(c)).isEqualTo(Color.rgba(0.4, 0.8, 1.2, 1));
        assertThat(c.sub(c)).isEqualTo(Color.TRANSPARENT);
        assertThat(c.scale(2)).isEqualTo(Color.rgba(0.4, 0.8, 1.2, 1));
        assertThat(c.mul(Color.WHITE)).isEqualTo(c);
        assertThat(Color.WHITE.dot(Color.WHITE)).isEqualTo(4.0);
        assertThat(Color.BLACK.distance(Color.WHITE)).isCloseTo(Math.sqrt(3), within(EPS));
        assertThat(c.premultiply()).isEqualTo(Color.rgba(0.1, 0.2, 0.3, 0.5));
        assertThat(c.premultiply().unpremultiply().approxEquals(c, EPS)).isTrue();
        assertThat(Color.TRANSPARENT.unpremultiply()).isEqualTo(Color.TRANSPARENT);
        assertThat(Color.WHITE.luminance()).isCloseTo(1, within(EPS));
        assertThat(Color.rgba(2, -1, 0.5, 3).clamp()).isEqualTo(Color.rgba(1, 0, 0.5, 1));
    }

    @Test
    void comparisons() {
        Color c = Color.rgba(0.1, 0.2, 0.3, 0.4);
        assertThat(c.approxEquals(c, EPS)).isTrue();
        assertThat(c.approxEquals(Color.rgba(1, 0.2, 0.3, 0.4), EPS)).isFalse();
        assertThat(c.approxEquals(Color.rgba(0.1, 1, 0.3, 0.4), EPS)).isFalse();
        assertThat(c.approxEquals(Color.rgba(0.1, 0.2, 1, 0.4), EPS)).isFalse();
        assertThat(c.approxEquals(Color.rgba(0.1, 0.2, 0.3, 1), EPS)).isFalse();
    }

    @Test
    void memory() {
        Color c = Color.rgba(0.25, 0.5, 0.75, 1);
        assertThat(Color.LAYOUT.byteSize()).isEqualTo(32);
        assertThat(Color.FLOAT_LAYOUT.byteSize()).isEqualTo(16);
        assertThat(Color.RGBA8_LAYOUT.byteSize()).isEqualTo(4);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(64);
            c.write(segment, 8);
            assertThat(Color.read(segment, 8)).isEqualTo(c);
            c.writeFloats(segment, 4);
            assertThat(Color.readFloats(segment, 4)).isEqualTo(c);
            orange.writeRgba8(segment, 1);
            assertThat(segment.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 1)).isEqualTo((byte) 0xFF);
            assertThat(Color.readRgba8(segment, 1).toSrgbaInt()).isEqualTo(0xFF8800FF);
        }
        ByteBuffer bytes = ByteBuffer.allocate(64);
        c.write(bytes, 8);
        assertThat(Color.read(bytes, 8)).isEqualTo(c);
        DoubleBuffer doubles = DoubleBuffer.allocate(8);
        c.write(doubles, 2);
        assertThat(Color.read(doubles, 2)).isEqualTo(c);
    }

    @Test
    void colorArray() {
        try (Arena arena = Arena.ofConfined()) {
            for (ColorArray colors : new ColorArray[] {ColorArray.onHeap(3), ColorArray.allocate(arena, 3)}) {
                assertThat(colors.get(0)).isEqualTo(Color.TRANSPARENT);
                colors.fill(Color.WHITE);
                colors.set(2, orange);
                assertThat(ColorArray.wrap(colors.segment()).get(2)).isEqualTo(orange);
                MemorySegment target = arena.allocate(64);
                colors.writeFloats(target, 0);
                assertThat(Color.readFloats(target, 16)).isEqualTo(Color.WHITE);
                colors.writeRgba8(target, 0);
                assertThat(Color.readRgba8(target, 8).toSrgbInt()).isEqualTo(0xFF8800);
            }
        }
    }
}
