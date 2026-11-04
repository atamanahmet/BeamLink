package com.beamlink.peer.register

import org.junit.Assert.assertEquals
import org.junit.Test

class EnrollmentPollOwnerRuleTest {

    @Test
    fun pendingStarts() {
        assertEquals(
            EnrollmentPollOwnerRule.Command.START,
            EnrollmentPollOwnerRule.onPersistedState(NexusPrefs.STATE_PENDING),
        )
    }

    @Test
    fun approvedStops() {
        assertEquals(
            EnrollmentPollOwnerRule.Command.STOP,
            EnrollmentPollOwnerRule.onPersistedState(NexusPrefs.STATE_APPROVED),
        )
    }

    @Test
    fun rejectedStops() {
        assertEquals(
            EnrollmentPollOwnerRule.Command.STOP,
            EnrollmentPollOwnerRule.onPersistedState(NexusPrefs.STATE_REJECTED),
        )
    }

    @Test
    fun resetStops() {
        assertEquals(
            EnrollmentPollOwnerRule.Command.STOP,
            EnrollmentPollOwnerRule.onPersistedState(NexusPrefs.STATE_NOT_CONFIGURED),
        )
    }

    @Test
    fun backgroundPausesWhenPending() {
        assertEquals(
            EnrollmentPollOwnerRule.Command.PAUSE,
            EnrollmentPollOwnerRule.onBackground(NexusPrefs.STATE_PENDING),
        )
        assertEquals(
            EnrollmentPollOwnerRule.Command.NONE,
            EnrollmentPollOwnerRule.onBackground(NexusPrefs.STATE_APPROVED),
        )
    }

    @Test
    fun foregroundResumesImmediateWhenPending() {
        assertEquals(
            EnrollmentPollOwnerRule.Command.RESUME_NOW,
            EnrollmentPollOwnerRule.onForeground(NexusPrefs.STATE_PENDING),
        )
        assertEquals(
            EnrollmentPollOwnerRule.Command.NONE,
            EnrollmentPollOwnerRule.onForeground(NexusPrefs.STATE_APPROVED),
        )
    }

    @Test
    fun restartWithPersistedPendingStarts() {
        assertEquals(
            EnrollmentPollOwnerRule.Command.START,
            EnrollmentPollOwnerRule.onPersistedState(NexusPrefs.STATE_PENDING),
        )
    }
}
