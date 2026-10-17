package com.atamanahmet.beamlink.agent.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiveOffsetRuleTest {

    @Test
    void exactMatchIsAccept() {
        assertThat(ReceiveOffsetRule.decide(100L, 50L, 100L))
                .isEqualTo(ReceiveOffsetRule.Decision.ACCEPT);
    }

    @Test
    void fullyBehindIsDuplicate() {
        assertThat(ReceiveOffsetRule.decide(0L, 50L, 100L))
                .isEqualTo(ReceiveOffsetRule.Decision.DUPLICATE);
        assertThat(ReceiveOffsetRule.decide(50L, 50L, 100L))
                .isEqualTo(ReceiveOffsetRule.Decision.DUPLICATE);
    }

    @Test
    void aheadIsRejectEarly() {
        assertThat(ReceiveOffsetRule.decide(150L, 50L, 100L))
                .isEqualTo(ReceiveOffsetRule.Decision.REJECT_EARLY);
    }

    @Test
    void straddlingExpectedIsOverlap() {
        assertThat(ReceiveOffsetRule.decide(80L, 50L, 100L))
                .isEqualTo(ReceiveOffsetRule.Decision.REJECT_OVERLAP);
    }
}
