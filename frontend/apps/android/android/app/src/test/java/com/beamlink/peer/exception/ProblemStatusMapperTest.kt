package com.beamlink.peer.exception

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Status, code, and content type for the four Ktor error classes.
 */
class ProblemStatusMapperTest {

    @Test
    fun invalidRequestIs400() {
        val mapped = ProblemStatusMapper.map(InvalidRequestException("pausedBy required"))
        assertEquals(400, mapped.status)
        assertEquals("INVALID_REQUEST", mapped.code)
        assertEquals("application/problem+json", mapped.contentType)
    }

    @Test
    fun illegalArgumentIs500() {
        val mapped = ProblemStatusMapper.map(IllegalArgumentException("boom"))
        assertEquals(500, mapped.status)
        assertEquals("INTERNAL_ERROR", mapped.code)
        assertEquals("application/problem+json", mapped.contentType)
    }

    @Test
    fun otherThrowableIs500() {
        val mapped = ProblemStatusMapper.map(IllegalStateException("nope"))
        assertEquals(500, mapped.status)
        assertEquals("INTERNAL_ERROR", mapped.code)
        assertEquals("application/problem+json", mapped.contentType)
    }

    @Test
    fun malformedJsonIs400() {
        val mapped = ProblemStatusMapper.map(JSONException("bad json"))
        assertEquals(400, mapped.status)
        assertEquals("INVALID_REQUEST", mapped.code)
        assertEquals("application/problem+json", mapped.contentType)
    }
}
