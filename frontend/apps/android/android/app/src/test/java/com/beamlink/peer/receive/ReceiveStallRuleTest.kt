package com.beamlink.peer.receive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Idle under, at, and over the limit. Already paused or finished is not stalled.
 */
class ReceiveStallRuleTest {

    @Test
    fun stalledOnlyWhenActiveAndIdleAboveLimit() {
        val now = Instant.parse("2026-01-01T00:01:00Z")
        val below = Instant.parse("2026-01-01T00:00:30Z")
        val equal = Instant.parse("2026-01-01T00:00:00Z")
        val above = Instant.parse("2025-12-31T23:59:59Z")

        assertFalse(ReceiveStallRule.isStalled(ReceiveSession.ACTIVE, below, now, 60L))
        assertFalse(ReceiveStallRule.isStalled(ReceiveSession.ACTIVE, equal, now, 60L))
        assertTrue(ReceiveStallRule.isStalled(ReceiveSession.ACTIVE, above, now, 60L))
        assertFalse(ReceiveStallRule.isStalled(ReceiveSession.PAUSED, above, now, 60L))
        assertFalse(ReceiveStallRule.isStalled(ReceiveSession.COMPLETED, above, now, 60L))
        assertFalse(ReceiveStallRule.isStalled(ReceiveSession.CANCELLED, above, now, 60L))
        assertFalse(ReceiveStallRule.isStalled(ReceiveSession.FAILED, above, now, 60L))
        assertFalse(ReceiveStallRule.isStalled(ReceiveSession.ACTIVE, null, now, 60L))
    }
}
