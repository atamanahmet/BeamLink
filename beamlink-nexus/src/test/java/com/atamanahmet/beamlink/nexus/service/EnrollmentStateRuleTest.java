package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnrollmentStateRuleTest {

    @Test
    void pendingMapsPending() {
        assertThat(EnrollmentStateRule.mapState(DeviceState.PENDING_APPROVAL))
                .isEqualTo(EnrollmentStateRule.Outcome.PENDING);
    }

    @Test
    void rejectedMapsRejected() {
        assertThat(EnrollmentStateRule.mapState(DeviceState.REJECTED))
                .isEqualTo(EnrollmentStateRule.Outcome.REJECTED);
    }

    @Test
    void unknownWhenNull() {
        assertThat(EnrollmentStateRule.mapState(null))
                .isEqualTo(EnrollmentStateRule.Outcome.UNKNOWN);
    }

    @Test
    void approvedMapsApproved() {
        assertThat(EnrollmentStateRule.mapState(DeviceState.APPROVED))
                .isEqualTo(EnrollmentStateRule.Outcome.APPROVED);
    }

    @Test
    void slowDownWhenFasterThanInterval() {
        assertThat(EnrollmentStateRule.DEFAULT_INTERVAL_SECONDS).isEqualTo(15);
        assertThat(EnrollmentStateRule.isSlowDown(5_000L, 1_000L, 5)).isTrue();
        assertThat(EnrollmentStateRule.isSlowDown(7_000L, 1_000L, 5)).isFalse();
        assertThat(EnrollmentStateRule.isSlowDown(1_000L, null, 5)).isFalse();
        assertThat(EnrollmentStateRule.isSlowDown(10_000L, 1_000L, 15)).isTrue();
        assertThat(EnrollmentStateRule.isSlowDown(16_000L, 1_000L, 15)).isFalse();
        assertThat(EnrollmentStateRule.bumpInterval(5)).isEqualTo(10);
        assertThat(EnrollmentStateRule.bumpInterval(15)).isEqualTo(20);
    }

    @Test
    void bumpIntervalCapsAtSixty() {
        assertThat(EnrollmentStateRule.bumpInterval(55)).isEqualTo(60);
        assertThat(EnrollmentStateRule.bumpInterval(60)).isEqualTo(60);
    }
}
