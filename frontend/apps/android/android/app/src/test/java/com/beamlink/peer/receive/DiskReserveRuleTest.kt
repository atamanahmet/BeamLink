package com.beamlink.peer.receive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure reserve boundary checks.
 */
class DiskReserveRuleTest {

    @Test
    fun exactBoundaryIsAllowed() {
        assertTrue(DiskReserveRule.allowed(200L, 50L, 150L))
    }

    @Test
    fun oneByteOverIsRefused() {
        assertFalse(DiskReserveRule.allowed(200L, 51L, 150L))
    }

    @Test
    fun sizeZeroStillNeedsReserve() {
        assertTrue(DiskReserveRule.allowed(150L, 0L, 150L))
        assertFalse(DiskReserveRule.allowed(149L, 0L, 150L))
    }

    @Test
    fun reserveBiggerThanDiskIsRefused() {
        assertFalse(DiskReserveRule.allowed(100L, 0L, 200L))
    }
}
