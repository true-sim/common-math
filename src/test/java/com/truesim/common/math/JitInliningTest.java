package com.truesim.common.math;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JitInliningTest {

    @Test
    void installsOnHotSpot() {
        assertThat(JitInlining.install()).isTrue();
        assertThat(JitInlining.DIRECTIVES).contains("+com/truesim/common/math/Mat4.*");
    }

    @Test
    void canBeDisabled() {
        assertThat(JitInlining.install("false", JitInlining.DIAGNOSTIC_COMMAND)).isFalse();
    }

    @Test
    void skipsWithoutDiagnosticCommands() {
        assertThat(JitInlining.install("true", "com.example:type=Missing")).isFalse();
    }
}
