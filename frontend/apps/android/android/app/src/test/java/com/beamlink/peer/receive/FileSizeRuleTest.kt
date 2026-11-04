package com.beamlink.peer.receive

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure fileSize field checks. Zero is allowed.
 */
class FileSizeRuleTest {

    @Test
    fun zeroIsAllowedMissingAndNegativeAreNot() {
        assertFalse(FileSizeRule.invalid(0L))
        assertFalse(FileSizeRule.invalid(1L))
        assertFalse(FileSizeRule.invalid(Long.MAX_VALUE))
        assertTrue(FileSizeRule.invalid(-1L))
        assertTrue(FileSizeRule.invalid(null))
    }

    @Test
    fun parseRejectsMissingNegativeAndNonNumeric() {
        assertEquals(0L, FileSizeRule.parse(JSONObject().put("fileSize", 0)))
        try {
            FileSizeRule.parse(JSONObject())
            throw AssertionError("expected ProtocolException")
        } catch (e: ProtocolException) {
            assertEquals(FileSizeRule.CODE, e.code)
            assertEquals(400, e.status)
        }
        try {
            FileSizeRule.parse(JSONObject().put("fileSize", -1))
            throw AssertionError("expected ProtocolException")
        } catch (e: ProtocolException) {
            assertEquals(FileSizeRule.CODE, e.code)
            assertEquals(400, e.status)
        }
        try {
            FileSizeRule.parse(JSONObject().put("fileSize", "nope"))
            throw AssertionError("expected ProtocolException")
        } catch (e: ProtocolException) {
            assertEquals(FileSizeRule.CODE, e.code)
            assertEquals(400, e.status)
        }
    }
}
