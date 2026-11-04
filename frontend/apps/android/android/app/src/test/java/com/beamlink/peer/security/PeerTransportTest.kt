package com.beamlink.peer.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PeerTransportTest {

    @Test
    fun listedGivesHttpsAndPinNeverHttp() {
        val t = PeerTransport.of("aaaa bbbb", 9091, 9090)
        assertEquals("https", t.scheme)
        assertEquals(9091, t.port)
        assertEquals("aaaa bbbb", t.pin)
        assertEquals("https://10.0.0.2:9091", t.baseUrl("10.0.0.2"))
    }

    @Test
    fun noneRefusesWithoutFingerprint() {
        val ex = assertThrows(PeerTransport.MissingPinException::class.java) {
            PeerTransport.of(null, null, 9090)
        }
        assertEquals("Peer TLS pin missing", ex.message)
    }

    @Test
    fun listedMissingPortThrows() {
        val ex = assertThrows(IllegalStateException::class.java) {
            PeerTransport.of("aaaa bbbb", null, 9090)
        }
        assertEquals("Peer TLS pin listed but tlsPort missing", ex.message)
    }
}
