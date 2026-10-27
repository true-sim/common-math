package com.truesim.common.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class JitInliningTest {

    @Test
    void installsOnHotSpot() {
        assertThat(JitInlining.install()).isTrue();
        assertThat(JitInlining.DIRECTIVES).contains("\"+com/truesim/common/math/Mat4.*\"");
    }

    @Test
    void canBeDisabled() {
        assertThat(JitInlining.install("false", JitInlining.DIAGNOSTIC_COMMAND)).isFalse();
    }

    @Test
    void skipsWithoutDiagnosticCommands() {
        assertThat(JitInlining.install("true", "com.example:type=Missing")).isFalse();
    }

    @Test
    void includesPatternsOfOtherLibraries() {
        assertThat(JitInlining.include("+com/example/Query.run", "-com/example/Query.solve")).isTrue();
        // Already included: nothing to install.
        assertThat(JitInlining.include("+com/example/Query.run")).isTrue();
        assertThat(JitInlining.include(false, JitInlining.DIAGNOSTIC_COMMAND, "+com/example/Other.*")).isFalse();
        assertThat(JitInlining.include(true, "com.example:type=Missing", "+com/example/Missing.*")).isFalse();
        assertThat(JitInlining.directives(List.of("+com/example/Query.run")))
            .startsWith("[{ match: \"*.*\", inline: [\"-com/truesim/common/math/Fdlibm.*\", ")
            .endsWith(", \"+com/example/Query.run\"] }]");
    }

    @Test
    void rejectsMalformedPatterns() {
        assertThatIllegalArgumentException().isThrownBy(() -> JitInlining.include("com/example/Query.run"));
        assertThatIllegalArgumentException().isThrownBy(() -> JitInlining.include("+com/example/\"], x"));
    }
}
