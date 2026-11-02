package com.atamanahmet.beamlink.peer.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiskPauseRuleTest {

    @Test
    void senderTreats507AsPauseWithNoRetry() {
        assertThat(DiskPauseRule.senderNoRetry(507)).isTrue();
        assertThat(DiskPauseRule.senderNoRetry(500)).isFalse();
        assertThat(DiskPauseRule.senderNoRetry(409)).isFalse();
    }

    @Test
    void receiverDiskPauseIsTargetInsufficientDisk() {
        assertThat(DiskPauseRule.isReceiverDiskPause(
                "PAUSED", "TARGET", DiskReserveRule.CODE)).isTrue();
        assertThat(DiskPauseRule.isReceiverDiskPause(
                "ACTIVE", "TARGET", DiskReserveRule.CODE)).isFalse();
        assertThat(DiskPauseRule.isReceiverDiskPause(
                "PAUSED", "SOURCE", DiskReserveRule.CODE)).isFalse();
    }

    @Test
    void resumeStayPausedWhenNoRoom() {
        assertThat(DiskPauseRule.resumeStayPaused(false)).isTrue();
        assertThat(DiskPauseRule.resumeStayPaused(true)).isFalse();
    }
}
