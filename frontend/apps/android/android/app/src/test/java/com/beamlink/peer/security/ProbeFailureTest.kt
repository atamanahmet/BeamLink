package com.beamlink.peer.security

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

class ProbeFailureTest {

    @Test
    fun connectRefusedAndUnreachableAreSameClass() {
        val refused = ProbeFailure.classify(ConnectException("Connection refused"))
        val unreachable = ProbeFailure.classify(ConnectException("Network is unreachable"))
        assertEquals(refused, unreachable)
        assertEquals(ProbeFailure.CANNOT_CONNECT, refused)
    }

    @Test
    fun wrappedConnectExceptionIsCombinedClass() {
        assertEquals(
            ProbeFailure.CANNOT_CONNECT,
            ProbeFailure.classify(RuntimeException("outer", ConnectException("x"))),
        )
    }

    @Test
    fun timeoutAndUnknownHostAreUnreachable() {
        assertEquals(ProbeFailure.UNREACHABLE, ProbeFailure.classify(SocketTimeoutException("t")))
        assertEquals(ProbeFailure.UNREACHABLE, ProbeFailure.classify(UnknownHostException("h")))
    }

    @Test
    fun sslHandshakeIsTlsError() {
        assertEquals(
            ProbeFailure.TLS_ERROR,
            ProbeFailure.classify(SSLHandshakeException("pkix")),
        )
    }

    @Test
    fun certificateExceptionIsTlsError() {
        assertEquals(ProbeFailure.TLS_ERROR, ProbeFailure.classify(CertificateException("bad")))
        assertEquals(
            ProbeFailure.TLS_ERROR,
            ProbeFailure.classify(IOException("wrap", CertificateException("bad"))),
        )
    }

    @Test
    fun ioExceptionRefusedTextIsUnreachable() {
        assertEquals(ProbeFailure.UNREACHABLE, ProbeFailure.classify(IOException("refused")))
    }

    @Test
    fun selfCauseChainTerminates() {
        assertEquals(ProbeFailure.UNREACHABLE, ProbeFailure.classify(SelfCause()))
    }

    private class SelfCause : RuntimeException() {
        override val cause: Throwable
            get() = this
    }
}
