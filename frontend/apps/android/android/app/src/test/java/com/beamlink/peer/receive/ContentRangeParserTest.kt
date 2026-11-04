package com.beamlink.peer.receive

import com.beamlink.peer.exception.InvalidRequestException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Pure Content-Range parse cases.
 */
class ContentRangeParserTest {

    @Test
    fun parsesBytesRange() {
        val range = ContentRangeParser.parse("bytes 0-524287/1000000")
        assertEquals(0L, range.start)
        assertEquals(524288L, range.length)
        assertEquals(1_000_000L, range.total)
    }

    @Test
    fun rejectsBlank() {
        assertThrows(InvalidRequestException::class.java) {
            ContentRangeParser.parse(null)
        }
    }

    @Test
    fun rejectsBadLength() {
        assertThrows(InvalidRequestException::class.java) {
            ContentRangeParser.parse("bytes 10-1/20")
        }
    }
}
