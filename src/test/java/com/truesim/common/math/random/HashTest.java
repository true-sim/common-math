package com.truesim.common.math.random;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The PCG hash, as unsigned 32-bit integers, and floats from it. */
class HashTest {

    @Test
    void theHashIsPcgOnUnsignedIntegers() {
        assertThat(Hash.pcg(0)).isEqualTo(129708002);
        assertThat(Hash.pcg(1)).isEqualTo(0xa8beea3c);
        assertThat(Hash.pcg(42)).isEqualTo(0x48f432ff);
        assertThat(Hash.pcg(0xFFFFFFFF)).isEqualTo(0xe62a4902);
        assertThat(Hash.combine(1, 2)).isEqualTo(0x7deda5d8);
        assertThat(Hash.combine(7, 3)).isEqualTo(0x47a42b00);
    }

    @Test
    void floatsAreTheTop24BitsInZeroToOne() {
        assertThat(Hash.unit(0)).isZero();
        assertThat(Hash.unit(0xFFFFFFFF)).isEqualTo(1 - 0x1p-24f).isLessThan(1);
        assertThat(Hash.unit(0x000000FF)).isZero();
        assertThat(Hash.random(7, 3)).isEqualTo((0x47a42b00 >>> 8) * 0x1p-24f);
    }

    /** The vectors scene-effects' simulator and the engine's particle kernel are checked against, copied unchanged. */
    @Test
    void theHashMatchesTheEffectsVectors() throws IOException {
        int checked = 0;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                HashTest.class.getResourceAsStream("hash-vectors.csv"), StandardCharsets.UTF_8))) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                if (line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split(",", -1);
                int a = Integer.parseUnsignedInt(f[1], 16);
                int expected = Integer.parseUnsignedInt(f[3], 16);
                int actual = switch (f[0]) {
                    case "pcg" -> Hash.pcg(a);
                    case "combine" -> Hash.combine(a, Integer.parseUnsignedInt(f[2], 16));
                    default -> Float.floatToRawIntBits(Hash.random(a, Integer.parseUnsignedInt(f[2], 16)));
                };
                assertThat(Integer.toHexString(actual)).as(line).isEqualTo(Integer.toHexString(expected));
                checked++;
            }
        }
        assertThat(checked).isEqualTo(26);
    }
}
