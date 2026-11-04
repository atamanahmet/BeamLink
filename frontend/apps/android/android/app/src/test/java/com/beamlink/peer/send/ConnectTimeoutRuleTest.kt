package com.beamlink.peer.send

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

class ConnectTimeoutRuleTest {

    @Test
    fun connectPhaseSocketTimeoutIsConnectTimeout() {
        assertTrue(
            ConnectTimeoutRule.isConnectTimeout(SocketTimeoutException("x"), true),
        )
    }

    @Test
    fun connectPhaseRefusedIsNotConnectTimeout() {
        assertFalse(
            ConnectTimeoutRule.isConnectTimeout(ConnectException("Connection refused"), true),
        )
        assertFalse(
            ConnectTimeoutRule.isConnectTimeout(IOException("refused"), true),
        )
    }

    @Test
    fun readPhaseSocketTimeoutIsNotConnectTimeout() {
        assertFalse(
            ConnectTimeoutRule.isConnectTimeout(SocketTimeoutException("x"), false),
        )
    }

    @Test
    fun connectedThenTimedOutIsNotConnectTimeout() {
        assertFalse(
            ConnectTimeoutRule.isConnectTimeout(SocketTimeoutException("x"), false),
        )
    }
}
