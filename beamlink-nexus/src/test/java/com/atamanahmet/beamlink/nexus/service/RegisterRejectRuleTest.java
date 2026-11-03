package com.atamanahmet.beamlink.nexus.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRejectRuleTest {

    @Test
    void receiveInProgressKeepsFailed() {
        assertThat(RegisterRejectRule.keepFailed("RECEIVE_IN_PROGRESS")).isTrue();
        assertThat(RegisterRejectRule.keepFailed("INSUFFICIENT_DISK")).isFalse();
        assertThat(RegisterRejectRule.keepFailed(null)).isFalse();
    }

    @Test
    void visibleMessagePrefersJsonBody() {
        assertThat(RegisterRejectRule.visibleMessage(
                "Another receive is already active",
                "fallback")).isEqualTo("Another receive is already active");
        assertThat(RegisterRejectRule.visibleMessage("  ", "fallback")).isEqualTo("fallback");
        assertThat(RegisterRejectRule.visibleMessage(null, "fallback")).isEqualTo("fallback");
    }
}
