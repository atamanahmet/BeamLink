package com.beamlink.peer.receive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pure disk room checks against a temp dir.
 */
class DiskSpaceTest {

    @Test
    fun zeroRequiredIsTrue() {
        val dir = File(System.getProperty("java.io.tmpdir") ?: ".")
        assertTrue(DiskSpace.hasRoom(dir, 0L))
    }

    @Test
    fun hugeRequiredIsFalse() {
        val dir = File(System.getProperty("java.io.tmpdir") ?: ".")
        assertFalse(DiskSpace.hasRoom(dir, Long.MAX_VALUE))
    }
}
