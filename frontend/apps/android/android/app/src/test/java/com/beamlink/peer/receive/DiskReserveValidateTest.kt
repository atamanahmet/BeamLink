package com.beamlink.peer.receive

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Bounds for the phone min-free-space setting.
 */
class DiskReserveValidateTest {

    @Test
    fun minIsValid() {
        assertEquals(DiskReserve.MIN_BYTES, DiskReserve.validate(DiskReserve.MIN_BYTES))
    }

    @Test
    fun maxIsValid() {
        assertEquals(DiskReserve.MAX_BYTES, DiskReserve.validate(DiskReserve.MAX_BYTES))
    }

    @Test(expected = IllegalArgumentException::class)
    fun belowMinFails() {
        DiskReserve.validate(DiskReserve.MIN_BYTES - 1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aboveMaxFails() {
        DiskReserve.validate(DiskReserve.MAX_BYTES + 1L)
    }
}
