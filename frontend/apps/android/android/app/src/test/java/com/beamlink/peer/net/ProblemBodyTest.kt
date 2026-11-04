package com.beamlink.peer.net

import com.beamlink.peer.send.ConnectTimeoutText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProblemBodyTest {

    @Test
    fun fullBodyReadsFields() {
        val body = """
            {"title":"Target unreachable","detail":"Target peer unreachable",
            "code":"TARGET_UNREACHABLE","reason":"TLS_TRUST","correlationId":"cid-1","status":502}
        """.trimIndent()
        val problem = ProblemBody.parse(body, 502)
        assertEquals("Target unreachable", problem.title)
        assertEquals("Target peer unreachable", problem.detail)
        assertEquals("TARGET_UNREACHABLE", problem.code)
        assertEquals("TLS_TRUST", problem.reason)
        assertEquals("cid-1", problem.correlationId)
        assertEquals(502, problem.status)
        assertEquals("Target peer unreachable\nRef: cid-1", ProblemBody.userText(body, 502))
    }

    @Test
    fun missingDetailUsesGeneric() {
        val body = """{"title":"Bad request","code":"BAD_REQUEST","reason":"UNKNOWN"}"""
        val problem = ProblemBody.parse(body, 400)
        assertNull(problem.detail)
        assertEquals("Request failed (400)", ProblemBody.userText(body, 400))
    }

    @Test
    fun nonJsonUsesGeneric() {
        val problem = ProblemBody.parse("not-json", 500)
        assertNull(problem.detail)
        assertEquals("Request failed (500)", ProblemBody.userText("not-json", 500))
    }

    @Test
    fun emptyBodyUsesGeneric() {
        assertEquals("Request failed (502)", ProblemBody.userText("", 502))
        assertEquals("Request failed (401)", ProblemBody.userText(null, 401))
        assertEquals("Request failed (400)", ProblemBody.userText("   ", 400))
    }

    @Test
    fun connectTimeoutReasonWinsOverCodeAndDetail() {
        val withCode = """
            {"code":"TARGET_UNREACHABLE","detail":"Target peer unreachable",
            "reason":"CONNECT_TIMEOUT","correlationId":"cid-1"}
        """.trimIndent()
        val noCode = """{"detail":"Target peer unreachable","reason":"CONNECT_TIMEOUT"}"""
        assertEquals(
            ConnectTimeoutText.MESSAGE + "\nRef: cid-1",
            ProblemBody.userText(withCode, 504),
        )
        assertEquals(ConnectTimeoutText.MESSAGE, ProblemBody.userText(noCode, 504))
    }

    @Test
    fun otherReasonKeepsDetailWhenCodePresent() {
        val body = """
            {"code":"TARGET_UNREACHABLE","detail":"Target peer unreachable","reason":"TIMEOUT"}
        """.trimIndent()
        assertEquals("Target peer unreachable", ProblemBody.userText(body, 504))
    }

    @Test
    fun correlationIdPresentAppendsRef() {
        val body = """{"detail":"Not enough disk space","correlationId":"abc"}"""
        assertEquals("Not enough disk space\nRef: abc", ProblemBody.userText(body, 507))
    }

    @Test
    fun correlationIdAbsentNoRef() {
        val body = """{"detail":"Not enough disk space"}"""
        assertEquals("Not enough disk space", ProblemBody.userText(body, 507))
    }
}
