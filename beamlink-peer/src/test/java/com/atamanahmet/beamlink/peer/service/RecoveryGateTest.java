package com.atamanahmet.beamlink.peer.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryGateTest {

    @Test
    void oneRecoveryPerGap() {
        assertThat(RecoveryGate.canRecover(true, 40_000L, 0L)).isTrue();
        assertThat(RecoveryGate.canRecover(true, 20_000L, 0L)).isFalse();
        assertThat(RecoveryGate.canRecover(true, 30_000L, 0L)).isTrue();
    }

    @Test
    void neverRegistersByItselfWhenNotApproved() {
        assertThat(RecoveryGate.canRecover(false, 40_000L, 0L)).isFalse();
    }
}
