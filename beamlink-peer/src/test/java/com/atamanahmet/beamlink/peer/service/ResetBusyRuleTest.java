package com.atamanahmet.beamlink.peer.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResetBusyRuleTest {

    @Test
    void activeAndPendingBlockReset() {
        assertThat(ResetBusyRule.isBlocking("ACTIVE")).isTrue();
        assertThat(ResetBusyRule.isBlocking("PENDING")).isTrue();
        assertThat(ResetBusyRule.isBlocking("PAUSED")).isFalse();
        assertThat(ResetBusyRule.isBlocking("COMPLETED")).isFalse();
        assertThat(ResetBusyRule.isBusy(List.of("PAUSED", "ACTIVE"))).isTrue();
        assertThat(ResetBusyRule.isBusy(List.of("PENDING"))).isTrue();
        assertThat(ResetBusyRule.isBusy(List.of("COMPLETED", "FAILED"))).isFalse();
        assertThat(ResetBusyRule.isBusy(List.of())).isFalse();
        assertThat(ResetBusyRule.isBusy(null)).isFalse();
    }

    @Test
    void forceSkipsBusyRuleAndPausedNeverBlocks() {
        assertThat(ResetBusyRule.isBusy(List.of("ACTIVE", "PENDING"), false)).isTrue();
        assertThat(ResetBusyRule.isBusy(List.of("ACTIVE", "PENDING"), true)).isFalse();
        assertThat(ResetBusyRule.isBusy(List.of("PAUSED"), false)).isFalse();
        assertThat(ResetBusyRule.blockingCount(List.of("ACTIVE", "PENDING", "PAUSED"))).isEqualTo(2);
        assertThat(ResetBusyRule.blockingCount(null)).isZero();
    }
}
