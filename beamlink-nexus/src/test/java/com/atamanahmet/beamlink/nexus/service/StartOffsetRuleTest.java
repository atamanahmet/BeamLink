package com.atamanahmet.beamlink.nexus.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StartOffsetRuleTest {

    @Test
    void newTransferUsesZeroWithoutQuery() {
        assertThat(StartOffsetRule.needsReceiverQuery(0L)).isFalse();
    }

    @Test
    void progressNeedsReceiverQuery() {
        assertThat(StartOffsetRule.needsReceiverQuery(1L)).isTrue();
        assertThat(StartOffsetRule.needsReceiverQuery(82837504L)).isTrue();
    }
}
