package com.beamlink.peer.receive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Zero byte active transfer reaches complete.
 */
class SendCompleteRuleTest {

    @Test
    fun zeroByteActiveSendReachesComplete() {
        assertTrue(SendCompleteRule.shouldMarkComplete(ReceiveSession.ACTIVE, 0L, 0L))
        assertTrue(SendCompleteRule.shouldMarkComplete(ReceiveSession.ACTIVE, 100L, 100L))
        assertFalse(SendCompleteRule.shouldMarkComplete(ReceiveSession.ACTIVE, 50L, 100L))
        assertFalse(SendCompleteRule.shouldMarkComplete(ReceiveSession.PAUSED, 0L, 0L))
        assertFalse(SendCompleteRule.shouldMarkComplete(ReceiveSession.COMPLETED, 0L, 0L))
    }
}
