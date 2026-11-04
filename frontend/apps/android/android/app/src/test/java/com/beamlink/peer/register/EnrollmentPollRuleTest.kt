package com.beamlink.peer.register

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EnrollmentPollRuleTest {

    @Test
    fun networkKeepsPending() {
        assertEquals(EnrollmentPollRule.Action.KEEP_PENDING, EnrollmentPollRule.onError(null, null, false))
    }

    @Test
    fun tooManyKeepsPending() {
        assertEquals(EnrollmentPollRule.Action.KEEP_PENDING, EnrollmentPollRule.onError(429, "slow_down", false))
        assertEquals(EnrollmentPollRule.Action.KEEP_PENDING, EnrollmentPollRule.onError(429, null, false))
    }

    @Test
    fun fiveXxKeepsPending() {
        assertEquals(EnrollmentPollRule.Action.KEEP_PENDING, EnrollmentPollRule.onError(502, null, false))
        assertEquals(EnrollmentPollRule.Action.KEEP_PENDING, EnrollmentPollRule.onError(500, null, false))
        assertEquals(EnrollmentPollRule.Action.KEEP_PENDING, EnrollmentPollRule.onError(503, "nope", false))
    }

    @Test
    fun stopOnDenied() {
        assertEquals(
            EnrollmentPollRule.Action.SET_REJECTED_STOP,
            EnrollmentPollRule.onError(403, "access_denied", false),
        )
    }

    @Test
    fun pendingKeepsPolling() {
        assertEquals(
            EnrollmentPollRule.Action.KEEP_PENDING,
            EnrollmentPollRule.onError(400, "authorization_pending", false),
        )
        assertEquals(EnrollmentPollRule.Action.KEEP_PENDING, EnrollmentPollRule.onError(400, "slow_down", false))
    }

    @Test
    fun invalidGrantRetriesOnceThenStops() {
        assertEquals(
            EnrollmentPollRule.Action.RETRY_CHALLENGE_ONCE,
            EnrollmentPollRule.onError(401, "invalid_grant", false),
        )
        assertEquals(EnrollmentPollRule.Action.STOP, EnrollmentPollRule.onError(401, "invalid_grant", true))
    }

    @Test
    fun fourXxMissingOrUnknownStops() {
        assertEquals(EnrollmentPollRule.Action.STOP, EnrollmentPollRule.onError(400, null, false))
        assertEquals(EnrollmentPollRule.Action.STOP, EnrollmentPollRule.onError(400, "", false))
        assertEquals(EnrollmentPollRule.Action.STOP, EnrollmentPollRule.onError(400, "nope", false))
    }

    @Test
    fun slowDownAddsFiveAndSticks() {
        val first = EnrollmentPollRule.nextWait(5, 5, 0, 400, "slow_down") { 0 }
        assertEquals(10, first.sleepSeconds)
        assertEquals(10, first.stickySeconds)
        assertEquals(0, first.failureCount)
        val second = EnrollmentPollRule.nextWait(
            5, first.stickySeconds, first.failureCount, 429, "slow_down",
        ) { 0 }
        assertEquals(15, second.sleepSeconds)
        assertEquals(15, second.stickySeconds)
    }

    @Test
    fun slowDownCapsAtSixty() {
        val atCap = EnrollmentPollRule.nextWait(5, 55, 0, 400, "slow_down") { 0 }
        assertEquals(60, atCap.sleepSeconds)
        assertEquals(60, atCap.stickySeconds)
        val stays = EnrollmentPollRule.nextWait(5, 60, 0, 429, "slow_down") { 0 }
        assertEquals(60, stays.sleepSeconds)
        assertEquals(60, stays.stickySeconds)
    }

    @Test
    fun floorAtBaseWhenRandomZero() {
        val min = EnrollmentPollRule.nextWait(5, 5, 0, null, null) { 0 }
        assertEquals(5, min.sleepSeconds)
        assertEquals(1, min.failureCount)
        assertEquals(5, min.stickySeconds)
    }

    @Test
    fun networkAndFiveXxFullJitterFromBase() {
        val max = EnrollmentPollRule.nextWait(5, 5, 1, 502, null) { bound -> bound - 1 }
        assertEquals(10, max.sleepSeconds)
        assertEquals(2, max.failureCount)
        val cap = EnrollmentPollRule.nextWait(5, 5, 3, 429, null) { bound -> bound - 1 }
        assertEquals(30, cap.sleepSeconds)
        assertEquals(4, cap.failureCount)
    }

    @Test
    fun twoHundredResetsFailures() {
        val wait = EnrollmentPollRule.nextWait(5, 15, 4, 200, null) { 0 }
        assertEquals(0, wait.failureCount)
        assertEquals(5, wait.stickySeconds)
        assertEquals(5, wait.sleepSeconds)
    }

    @Test
    fun pendingUsesCurrentInterval() {
        val atBase = EnrollmentPollRule.nextWait(5, 5, 0, 400, "authorization_pending") { 0 }
        assertEquals(5, atBase.sleepSeconds)
        assertEquals(5, atBase.stickySeconds)
        val sticky = EnrollmentPollRule.nextWait(5, 15, 0, 400, "authorization_pending") { 0 }
        assertEquals(15, sticky.sleepSeconds)
        assertEquals(15, sticky.stickySeconds)
    }

    @Test
    fun serverIntervalUsed() {
        val server = EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS
        val wait = EnrollmentPollRule.nextWait(server, server, 0, 400, "authorization_pending") { 0 }
        assertEquals(server, wait.sleepSeconds)
        assertEquals(server, wait.stickySeconds)
        val below = EnrollmentPollRule.nextWait(server, 1, 0, 400, "authorization_pending") { 0 }
        assertEquals(server, below.sleepSeconds)
    }

    @Test
    fun slowDownAddsFiveTwice() {
        val server = EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS
        val first = EnrollmentPollRule.nextWait(server, server, 0, 400, "slow_down") { 0 }
        assertEquals(server + 5, first.sleepSeconds)
        assertEquals(server + 5, first.stickySeconds)
        val second = EnrollmentPollRule.nextWait(
            server, first.stickySeconds, first.failureCount, 429, "slow_down",
        ) { 0 }
        assertEquals(server + 10, second.sleepSeconds)
        assertEquals(server + 10, second.stickySeconds)
    }

    @Test
    fun resetOnNewPeriod() {
        val server = EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS
        val approved = EnrollmentPollRule.nextWait(server, server + 10, 3, 200, null) { 0 }
        assertEquals(server, approved.sleepSeconds)
        assertEquals(server, approved.stickySeconds)
        assertEquals(0, approved.failureCount)
        val freshPending = EnrollmentPollRule.nextWait(server, server, 0, 400, "authorization_pending") { 0 }
        assertEquals(server, freshPending.sleepSeconds)
        assertEquals(server, freshPending.stickySeconds)
    }

    @Test
    fun defaultOnlyBeforeFirstResponse() {
        assertEquals(15, EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS)
        val before = EnrollmentPollRule.nextWait(
            EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS,
            EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS,
            0,
            400,
            "authorization_pending",
        ) { 0 }
        assertEquals(15, before.sleepSeconds)
        val afterSlow = EnrollmentPollRule.nextWait(15, 15, 0, 400, "slow_down") { 0 }
        assertEquals(20, afterSlow.sleepSeconds)
        assertNotEquals(EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS, afterSlow.sleepSeconds)
    }
}
