package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SendCompleteRuleTest {

    @Test
    void zeroByteActiveSendReachesComplete() {
        assertThat(SendCompleteRule.shouldMarkComplete(TransferStatus.ACTIVE, 0L, 0L)).isTrue();
        assertThat(SendCompleteRule.shouldMarkComplete(TransferStatus.ACTIVE, 100L, 100L)).isTrue();
        assertThat(SendCompleteRule.shouldMarkComplete(TransferStatus.ACTIVE, 50L, 100L)).isFalse();
        assertThat(SendCompleteRule.shouldMarkComplete(TransferStatus.PAUSED, 0L, 0L)).isFalse();
        assertThat(SendCompleteRule.shouldMarkComplete(TransferStatus.COMPLETED, 0L, 0L)).isFalse();
    }
}
