package com.atamanahmet.beamlink.nexus.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkipSourceEntryRuleTest {

    @Test
    void skipsUnreadableOrHiddenAndSystem() {
        assertThat(SkipSourceEntryRule.shouldSkip(true, false, false)).isFalse();
        assertThat(SkipSourceEntryRule.shouldSkip(true, true, false)).isFalse();
        assertThat(SkipSourceEntryRule.shouldSkip(true, false, true)).isFalse();
        assertThat(SkipSourceEntryRule.shouldSkip(true, true, true)).isTrue();
        assertThat(SkipSourceEntryRule.shouldSkip(false, false, false)).isTrue();
        assertThat(SkipSourceEntryRule.shouldSkip(false, true, true)).isTrue();
    }
}
