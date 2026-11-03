package com.atamanahmet.beamlink.peer.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnrollmentPollRuleTest {

    @Test
    void networkKeepsPending() {
        assertThat(EnrollmentPollRule.onError(null, null, false))
                .isEqualTo(EnrollmentPollRule.Action.KEEP_PENDING);
    }

    @Test
    void tooManyKeepsPending() {
        assertThat(EnrollmentPollRule.onError(429, "slow_down", false))
                .isEqualTo(EnrollmentPollRule.Action.KEEP_PENDING);
        assertThat(EnrollmentPollRule.onError(429, null, false))
                .isEqualTo(EnrollmentPollRule.Action.KEEP_PENDING);
    }

    @Test
    void fiveXxKeepsPending() {
        assertThat(EnrollmentPollRule.onError(502, null, false))
                .isEqualTo(EnrollmentPollRule.Action.KEEP_PENDING);
        assertThat(EnrollmentPollRule.onError(500, null, false))
                .isEqualTo(EnrollmentPollRule.Action.KEEP_PENDING);
        assertThat(EnrollmentPollRule.onError(503, "nope", false))
                .isEqualTo(EnrollmentPollRule.Action.KEEP_PENDING);
    }

    @Test
    void stopOnDenied() {
        assertThat(EnrollmentPollRule.onError(403, "access_denied", false))
                .isEqualTo(EnrollmentPollRule.Action.SET_REJECTED_STOP);
    }

    @Test
    void pendingKeepsPolling() {
        assertThat(EnrollmentPollRule.onError(400, "authorization_pending", false))
                .isEqualTo(EnrollmentPollRule.Action.KEEP_PENDING);
        assertThat(EnrollmentPollRule.onError(400, "slow_down", false))
                .isEqualTo(EnrollmentPollRule.Action.KEEP_PENDING);
    }

    @Test
    void invalidGrantRetriesOnceThenStops() {
        assertThat(EnrollmentPollRule.onError(401, "invalid_grant", false))
                .isEqualTo(EnrollmentPollRule.Action.RETRY_CHALLENGE_ONCE);
        assertThat(EnrollmentPollRule.onError(401, "invalid_grant", true))
                .isEqualTo(EnrollmentPollRule.Action.STOP);
    }

    @Test
    void fourXxMissingOrUnknownStops() {
        assertThat(EnrollmentPollRule.onError(400, null, false))
                .isEqualTo(EnrollmentPollRule.Action.STOP);
        assertThat(EnrollmentPollRule.onError(400, "", false))
                .isEqualTo(EnrollmentPollRule.Action.STOP);
        assertThat(EnrollmentPollRule.onError(400, "nope", false))
                .isEqualTo(EnrollmentPollRule.Action.STOP);
    }

    @Test
    void slowDownAddsFiveAndSticks() {
        EnrollmentPollRule.Wait first = EnrollmentPollRule.nextWait(
                5, 5, 0, 400, "slow_down", bound -> 0);
        assertThat(first.sleepSeconds()).isEqualTo(10);
        assertThat(first.stickySeconds()).isEqualTo(10);
        assertThat(first.failureCount()).isEqualTo(0);
        EnrollmentPollRule.Wait second = EnrollmentPollRule.nextWait(
                5, first.stickySeconds(), first.failureCount(), 429, "slow_down", bound -> 0);
        assertThat(second.sleepSeconds()).isEqualTo(15);
        assertThat(second.stickySeconds()).isEqualTo(15);
    }

    @Test
    void slowDownCapsAtSixty() {
        EnrollmentPollRule.Wait atCap = EnrollmentPollRule.nextWait(
                5, 55, 0, 400, "slow_down", bound -> 0);
        assertThat(atCap.sleepSeconds()).isEqualTo(60);
        assertThat(atCap.stickySeconds()).isEqualTo(60);
        EnrollmentPollRule.Wait stays = EnrollmentPollRule.nextWait(
                5, 60, 0, 429, "slow_down", bound -> 0);
        assertThat(stays.sleepSeconds()).isEqualTo(60);
        assertThat(stays.stickySeconds()).isEqualTo(60);
    }

    @Test
    void floorAtBaseWhenRandomZero() {
        EnrollmentPollRule.Wait min = EnrollmentPollRule.nextWait(
                5, 5, 0, null, null, bound -> 0);
        assertThat(min.sleepSeconds()).isEqualTo(5);
        assertThat(min.failureCount()).isEqualTo(1);
        assertThat(min.stickySeconds()).isEqualTo(5);
    }

    @Test
    void networkAndFiveXxFullJitterFromBase() {
        EnrollmentPollRule.Wait max = EnrollmentPollRule.nextWait(
                5, 5, 1, 502, null, bound -> bound - 1);
        assertThat(max.sleepSeconds()).isEqualTo(10);
        assertThat(max.failureCount()).isEqualTo(2);
        EnrollmentPollRule.Wait cap = EnrollmentPollRule.nextWait(
                5, 5, 3, 429, null, bound -> bound - 1);
        assertThat(cap.sleepSeconds()).isEqualTo(30);
        assertThat(cap.failureCount()).isEqualTo(4);
    }

    @Test
    void twoHundredResetsFailures() {
        EnrollmentPollRule.Wait wait = EnrollmentPollRule.nextWait(
                5, 15, 4, 200, null, bound -> 0);
        assertThat(wait.failureCount()).isEqualTo(0);
        assertThat(wait.stickySeconds()).isEqualTo(5);
        assertThat(wait.sleepSeconds()).isEqualTo(5);
    }

    @Test
    void pendingUsesCurrentInterval() {
        EnrollmentPollRule.Wait atBase = EnrollmentPollRule.nextWait(
                5, 5, 0, 400, "authorization_pending", bound -> 0);
        assertThat(atBase.sleepSeconds()).isEqualTo(5);
        assertThat(atBase.stickySeconds()).isEqualTo(5);
        EnrollmentPollRule.Wait sticky = EnrollmentPollRule.nextWait(
                5, 15, 0, 400, "authorization_pending", bound -> 0);
        assertThat(sticky.sleepSeconds()).isEqualTo(15);
        assertThat(sticky.stickySeconds()).isEqualTo(15);
    }

    @Test
    void serverIntervalUsed() {
        int server = EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS;
        EnrollmentPollRule.Wait wait = EnrollmentPollRule.nextWait(
                server, server, 0, 400, "authorization_pending", bound -> 0);
        assertThat(wait.sleepSeconds()).isEqualTo(server);
        assertThat(wait.stickySeconds()).isEqualTo(server);
        EnrollmentPollRule.Wait below = EnrollmentPollRule.nextWait(
                server, 1, 0, 400, "authorization_pending", bound -> 0);
        assertThat(below.sleepSeconds()).isEqualTo(server);
    }

    @Test
    void slowDownAddsFiveTwice() {
        int server = EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS;
        EnrollmentPollRule.Wait first = EnrollmentPollRule.nextWait(
                server, server, 0, 400, "slow_down", bound -> 0);
        assertThat(first.sleepSeconds()).isEqualTo(server + 5);
        assertThat(first.stickySeconds()).isEqualTo(server + 5);
        EnrollmentPollRule.Wait second = EnrollmentPollRule.nextWait(
                server, first.stickySeconds(), first.failureCount(), 429, "slow_down", bound -> 0);
        assertThat(second.sleepSeconds()).isEqualTo(server + 10);
        assertThat(second.stickySeconds()).isEqualTo(server + 10);
    }

    @Test
    void resetOnNewPeriod() {
        int server = EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS;
        EnrollmentPollRule.Wait approved = EnrollmentPollRule.nextWait(
                server, server + 10, 3, 200, null, bound -> 0);
        assertThat(approved.sleepSeconds()).isEqualTo(server);
        assertThat(approved.stickySeconds()).isEqualTo(server);
        assertThat(approved.failureCount()).isEqualTo(0);
        EnrollmentPollRule.Wait freshPending = EnrollmentPollRule.nextWait(
                server, server, 0, 400, "authorization_pending", bound -> 0);
        assertThat(freshPending.sleepSeconds()).isEqualTo(server);
        assertThat(freshPending.stickySeconds()).isEqualTo(server);
    }

    @Test
    void defaultOnlyBeforeFirstResponse() {
        assertThat(EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS).isEqualTo(15);
        EnrollmentPollRule.Wait before = EnrollmentPollRule.nextWait(
                EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS,
                EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS,
                0,
                400,
                "authorization_pending",
                bound -> 0);
        assertThat(before.sleepSeconds()).isEqualTo(15);
        EnrollmentPollRule.Wait afterSlow = EnrollmentPollRule.nextWait(
                15, 15, 0, 400, "slow_down", bound -> 0);
        assertThat(afterSlow.sleepSeconds()).isEqualTo(20);
        assertThat(afterSlow.sleepSeconds()).isNotEqualTo(EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS);
    }
}
