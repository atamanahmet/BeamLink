package com.beamlink.peer.receive

import com.beamlink.peer.exception.InvalidRequestException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure batch disk need from file sizes, ignoring body totalSize.
 */
class BatchDiskNeedTest {

    @Test
    fun tinyTotalSizeWithHugeFilesIsRejected() {
        val needed = BatchDiskNeed.requiredBytes(listOf(1_000_000_000L, 1_000_000_000L), 1L)
        assertEquals(2_000_000_000L, needed)
        assertFalse(DiskReserveRule.allowed(500L, needed, 100L))
    }

    @Test
    fun sumOverflowIsRejected() {
        assertThrows(InvalidRequestException::class.java) {
            BatchDiskNeed.requiredBytes(listOf(Long.MAX_VALUE, 1L), 0L)
        }
    }

    @Test
    fun normalBatchPasses() {
        val needed = BatchDiskNeed.requiredBytes(listOf(100L, 50L), 999L)
        assertEquals(150L, needed)
        assertTrue(DiskReserveRule.allowed(1000L, needed, 100L))
    }

    @Test
    fun singleFileWithEnoughRoomPasses() {
        val needed = BatchDiskNeed.requiredBytes(listOf(40L), 0L)
        assertEquals(40L, needed)
        assertTrue(DiskReserveRule.allowed(200L, needed, 150L))
    }
}
