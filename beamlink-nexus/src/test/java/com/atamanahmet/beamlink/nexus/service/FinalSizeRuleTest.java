package com.atamanahmet.beamlink.nexus.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FinalSizeRuleTest {

    @Test
    void confirmedOffsetMustEqualFileSize() {
        assertThat(FinalSizeRule.matches(100L, 100L)).isTrue();
        assertThat(FinalSizeRule.matches(99L, 100L)).isFalse();
        assertThat(FinalSizeRule.matches(101L, 100L)).isFalse();
        assertThat(FinalSizeRule.matches(0L, 0L)).isTrue();
    }
}
