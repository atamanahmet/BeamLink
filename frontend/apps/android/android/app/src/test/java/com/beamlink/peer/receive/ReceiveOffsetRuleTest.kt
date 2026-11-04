package com.beamlink.peer.receive

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure offset accept, duplicate, and reject cases.
 */
class ReceiveOffsetRuleTest {

    @Test
    fun acceptAtExpected() {
        assertEquals(
            ReceiveOffsetRule.Decision.ACCEPT,
            ReceiveOffsetRule.decide(100L, 50L, 100L),
        )
    }

    @Test
    fun duplicateFullyBehind() {
        assertEquals(
            ReceiveOffsetRule.Decision.DUPLICATE,
            ReceiveOffsetRule.decide(0L, 50L, 100L),
        )
    }

    @Test
    fun rejectEarly() {
        assertEquals(
            ReceiveOffsetRule.Decision.REJECT_EARLY,
            ReceiveOffsetRule.decide(200L, 10L, 100L),
        )
    }

    @Test
    fun rejectOverlap() {
        assertEquals(
            ReceiveOffsetRule.Decision.REJECT_OVERLAP,
            ReceiveOffsetRule.decide(90L, 20L, 100L),
        )
    }
}
