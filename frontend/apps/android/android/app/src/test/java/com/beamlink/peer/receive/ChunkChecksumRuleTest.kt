package com.beamlink.peer.receive

import com.beamlink.peer.util.FileChecksum
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure X-Chunk-SHA256 header checks.
 */
class ChunkChecksumRuleTest {

    @Test
    fun missingHeader() {
        assertEquals(
            ChunkChecksumRule.Result.MISSING,
            ChunkChecksumRule.check(null, byteArrayOf(1)),
        )
    }

    @Test
    fun mismatch() {
        assertEquals(
            ChunkChecksumRule.Result.MISMATCH,
            ChunkChecksumRule.check("00", byteArrayOf(1)),
        )
    }

    @Test
    fun okIgnoreCase() {
        val chunk = "abc".toByteArray()
        val hex = FileChecksum.sha256Hex(chunk).uppercase()
        assertEquals(ChunkChecksumRule.Result.OK, ChunkChecksumRule.check(hex, chunk))
    }
}
