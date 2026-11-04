package com.beamlink.peer.receive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sender 507, receive pause fields, and resume re-check.
 */
class DiskPauseRuleTest {

    @Test
    fun senderTreats507AsPauseWithNoRetry() {
        assertTrue(DiskPauseRule.senderNoRetry(507))
        assertFalse(DiskPauseRule.senderNoRetry(500))
        assertFalse(DiskPauseRule.senderNoRetry(409))
    }

    @Test
    fun receiverDiskPauseIsTargetInsufficientDisk() {
        assertTrue(
            DiskPauseRule.isReceiverDiskPause(
                ReceiveSession.PAUSED,
                "TARGET",
                DiskReserve.CODE,
            ),
        )
        assertFalse(
            DiskPauseRule.isReceiverDiskPause(
                ReceiveSession.ACTIVE,
                "TARGET",
                DiskReserve.CODE,
            ),
        )
        assertFalse(
            DiskPauseRule.isReceiverDiskPause(
                ReceiveSession.PAUSED,
                "SOURCE",
                DiskReserve.CODE,
            ),
        )
    }

    @Test
    fun resumeStayPausedWhenNoRoom() {
        assertTrue(DiskPauseRule.resumeStayPaused(false))
        assertFalse(DiskPauseRule.resumeStayPaused(true))
    }
}
