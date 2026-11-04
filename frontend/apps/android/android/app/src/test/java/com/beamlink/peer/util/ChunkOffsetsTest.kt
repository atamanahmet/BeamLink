package com.beamlink.peer.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure chunk offset and Content-Range math.
 */
class ChunkOffsetsTest {

    @Test
    fun contentRangeFirstChunk() {
        assertEquals("bytes 0-524287/1000000", ChunkOffsets.contentRange(0L, 524288, 1_000_000L))
    }

    @Test
    fun contentRangeMiddleChunk() {
        assertEquals("bytes 100-199/500", ChunkOffsets.contentRange(100L, 100, 500L))
    }

    @Test
    fun nextOffsetAdvances() {
        assertEquals(524288L, ChunkOffsets.nextOffset(0L, 524288))
        assertEquals(600L, ChunkOffsets.nextOffset(500L, 100))
    }
}
