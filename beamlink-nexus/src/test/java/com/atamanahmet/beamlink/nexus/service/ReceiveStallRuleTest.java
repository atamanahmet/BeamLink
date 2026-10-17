package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiveStallRuleTest {

    @Test
    void stalledOnlyWhenActiveAndIdleAboveLimit() {
        Instant now = Instant.parse("2026-01-01T00:01:00Z");
        Instant below = Instant.parse("2026-01-01T00:00:30Z");
        Instant equal = Instant.parse("2026-01-01T00:00:00Z");
        Instant above = Instant.parse("2025-12-31T23:59:59Z");

        assertThat(ReceiveStallRule.isStalled(TransferStatus.ACTIVE, below, now, 60L))
                .isFalse();
        assertThat(ReceiveStallRule.isStalled(TransferStatus.ACTIVE, equal, now, 60L))
                .isFalse();
        assertThat(ReceiveStallRule.isStalled(TransferStatus.ACTIVE, above, now, 60L))
                .isTrue();
        assertThat(ReceiveStallRule.isStalled(TransferStatus.PAUSED, above, now, 60L))
                .isFalse();
        assertThat(ReceiveStallRule.isStalled(TransferStatus.COMPLETED, above, now, 60L))
                .isFalse();
        assertThat(ReceiveStallRule.isStalled(TransferStatus.CANCELLED, above, now, 60L))
                .isFalse();
        assertThat(ReceiveStallRule.isStalled(TransferStatus.FAILED, above, now, 60L))
                .isFalse();
        assertThat(ReceiveStallRule.isStalled(TransferStatus.ACTIVE, null, now, 60L))
                .isFalse();
    }
}
