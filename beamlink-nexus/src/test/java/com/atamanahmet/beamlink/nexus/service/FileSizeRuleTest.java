package com.atamanahmet.beamlink.nexus.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileSizeRuleTest {

    @Test
    void zeroIsAllowedMissingAndNegativeAreNot() {
        assertThat(FileSizeRule.invalid(0L)).isFalse();
        assertThat(FileSizeRule.invalid(1L)).isFalse();
        assertThat(FileSizeRule.invalid(Long.MAX_VALUE)).isFalse();
        assertThat(FileSizeRule.invalid(-1L)).isTrue();
        assertThat(FileSizeRule.invalid(null)).isTrue();
    }
}
