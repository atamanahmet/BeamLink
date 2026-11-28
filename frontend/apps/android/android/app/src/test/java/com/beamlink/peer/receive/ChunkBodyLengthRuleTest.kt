package com.beamlink.peer.receive

import com.beamlink.peer.config.DeviceDefaults
import com.beamlink.peer.exception.InvalidRequestException
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Pure Content-Length vs Content-Range length checks before and after receive.
 */
class ChunkBodyLengthRuleTest {

    @Test
    fun rejectsBodyLargerThanCap() {
        val over = DeviceDefaults.CHUNK_SIZE_BYTES.toLong() + 1L
        assertThrows(InvalidRequestException::class.java) {
            ChunkBodyLengthRule.requireBeforeReceive(over, over)
        }
    }

    @Test
    fun rejectsHeaderLengthNotEqualToRange() {
        assertThrows(InvalidRequestException::class.java) {
            ChunkBodyLengthRule.requireBeforeReceive(100L, 50L)
        }
    }

    @Test
    fun rejectsMissingContentLength() {
        assertThrows(InvalidRequestException::class.java) {
            ChunkBodyLengthRule.requireBeforeReceive(null, 50L)
        }
    }

    @Test
    fun acceptsFullChunk() {
        val len = DeviceDefaults.CHUNK_SIZE_BYTES.toLong()
        ChunkBodyLengthRule.requireBeforeReceive(len, len)
        ChunkBodyLengthRule.requireAfterReceive(DeviceDefaults.CHUNK_SIZE_BYTES, len)
    }

    @Test
    fun acceptsSmallerLastChunk() {
        ChunkBodyLengthRule.requireBeforeReceive(100L, 100L)
        ChunkBodyLengthRule.requireAfterReceive(100, 100L)
    }

    @Test
    fun rejectsBodySizeNotEqualToRange() {
        assertThrows(InvalidRequestException::class.java) {
            ChunkBodyLengthRule.requireAfterReceive(50, 100L)
        }
    }
}
