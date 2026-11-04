package com.beamlink.peer.send

import com.beamlink.peer.net.ProblemBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException

class ConnectTimeoutTextTest {

    @Test
    fun connectTimeoutToLanHostUsesFirewallSentence() {
        val error = ConnectTimeoutException(SocketTimeoutException("x"))
        assertEquals(ConnectTimeoutText.MESSAGE, ConnectTimeoutText.of(error, "192.168.1.5"))
    }

    @Test
    fun refusedStaysNull() {
        assertNull(
            ConnectTimeoutText.of(ConnectException("Connection refused"), "192.168.1.5"),
        )
    }

    @Test
    fun readTimeoutStaysNull() {
        assertNull(
            ConnectTimeoutText.of(SocketTimeoutException("read timed out"), "192.168.1.5"),
        )
    }

    @Test
    fun loopbackConnectTimeoutStaysNull() {
        val error = ConnectTimeoutException(SocketTimeoutException("x"))
        assertNull(ConnectTimeoutText.of(error, "127.0.0.1"))
        assertNull(ConnectTimeoutText.of(error, "localhost"))
    }

    @Test
    fun userTextMatchesFirewallSentence() {
        val withCode = """{"code":"TARGET_UNREACHABLE","detail":"x","reason":"CONNECT_TIMEOUT"}"""
        val noCode = """{"detail":"x","reason":"CONNECT_TIMEOUT"}"""
        assertEquals(ConnectTimeoutText.MESSAGE, ProblemBody.userText(withCode, 504))
        assertEquals(ConnectTimeoutText.MESSAGE, ProblemBody.userText(noCode, 504))
    }

    @Test
    fun unknownExceptionStaysNull() {
        assertNull(ConnectTimeoutText.of(IllegalStateException("other"), "192.168.1.5"))
    }
}
