package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.domain.enums.DeviceState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnregisteredEnrollRuleTest {

    @Test
    void enrollsWhenReadyAndIdle() {
        assertThat(UnregisteredEnrollRule.shouldEnroll(
                true, true, DeviceState.UNREGISTERED, false)).isTrue();
    }

    @Test
    void skipsWhenInProgress() {
        assertThat(UnregisteredEnrollRule.shouldEnroll(
                true, true, DeviceState.UNREGISTERED, true)).isFalse();
    }

    @Test
    void skipsWhenPinOrUrlMissing() {
        assertThat(UnregisteredEnrollRule.shouldEnroll(
                false, true, DeviceState.UNREGISTERED, false)).isFalse();
        assertThat(UnregisteredEnrollRule.shouldEnroll(
                true, false, DeviceState.UNREGISTERED, false)).isFalse();
    }

    @Test
    void skipsWhenNotUnregistered() {
        assertThat(UnregisteredEnrollRule.shouldEnroll(
                true, true, DeviceState.APPROVED, false)).isFalse();
        assertThat(UnregisteredEnrollRule.shouldEnroll(
                true, true, DeviceState.PENDING_APPROVAL, false)).isFalse();
    }
}
