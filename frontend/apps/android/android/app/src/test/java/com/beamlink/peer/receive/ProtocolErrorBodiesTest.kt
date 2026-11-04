package com.beamlink.peer.receive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProtocolErrorBodiesTest {

    @Test
    fun invalidFileSizeIsProblemDetail() {
        val json = ProtocolErrorBodies.toJson(
            ProtocolException("INVALID_FILE_SIZE", "Invalid file size: -1", 400),
        )
        assertEquals("about:blank", json.getString("type"))
        assertEquals("Transfer protocol error", json.getString("title"))
        assertEquals(400, json.getInt("status"))
        assertEquals("Invalid file size: -1", json.getString("detail"))
        assertEquals("INVALID_FILE_SIZE", json.getString("code"))
        assertEquals("UNKNOWN", json.getString("reason"))
        assertFalse(json.getString("correlationId").isBlank())
        assertFalse(json.has("message"))
        assertFalse(json.has("success"))
        assertFalse(json.has("error"))
    }

    @Test
    fun offsetMismatchHasExpected() {
        val json = ProtocolErrorBodies.toJson(
            ProtocolException("OFFSET_MISMATCH", "Unexpected offset", 409, expectedOffset = 10L),
        )
        assertEquals("Offset mismatch", json.getString("title"))
        assertEquals("OFFSET_MISMATCH", json.getString("code"))
        assertEquals("Unexpected offset", json.getString("detail"))
        assertEquals(10L, json.getLong("expectedOffset"))
        assertFalse(json.has("message"))
    }

    @Test
    fun pausedIncludesPausedBy() {
        val json = ProtocolErrorBodies.toJson(
            ProtocolException("TRANSFER_PAUSED", "Transfer is paused", 409, pausedBy = "SOURCE"),
        )
        assertEquals("TRANSFER_PAUSED", json.getString("code"))
        assertEquals("Transfer is paused", json.getString("detail"))
        assertEquals("SOURCE", json.getString("pausedBy"))
        assertFalse(json.has("message"))
    }

    @Test
    fun insufficientDiskKeepsCode() {
        val json = ProtocolErrorBodies.toJson(
            ProtocolException("INSUFFICIENT_DISK", DiskReserve.DETAIL, 507),
        )
        assertEquals("INSUFFICIENT_DISK", json.getString("code"))
        assertEquals(DiskReserve.DETAIL, json.getString("detail"))
        assertEquals("UNKNOWN", json.getString("reason"))
        assertFalse(json.has("message"))
    }

    @Test
    fun sessionNotFoundKeepsCode() {
        val json = ProtocolErrorBodies.toJson(
            ProtocolException("SESSION_NOT_FOUND", "Receiver lost the transfer, send again", 404),
        )
        assertEquals("SESSION_NOT_FOUND", json.getString("code"))
        assertEquals("Receiver lost the transfer, send again", json.getString("detail"))
        assertEquals(404, json.getInt("status"))
        assertFalse(json.has("message"))
    }

    @Test
    fun ofWritesAuthShape() {
        val json = ProtocolErrorBodies.of(
            401,
            "Unauthorized",
            "Missing peer token headers",
            "UNAUTHORIZED",
        )
        assertEquals("Unauthorized", json.getString("title"))
        assertEquals("Missing peer token headers", json.getString("detail"))
        assertEquals("UNAUTHORIZED", json.getString("code"))
        assertEquals("UNKNOWN", json.getString("reason"))
        assertFalse(json.getString("correlationId").isBlank())
        assertFalse(json.has("message"))
    }
}
