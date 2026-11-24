package com.beamlink.peer.security

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSignaturePayloadTest {

    @Test
    fun registerListed() {
        assertEquals(
            "beamlink-register-v1\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETEST\n$TLS_FINGERPRINT\n9091",
            DeviceSignaturePayload.signedRegisterMessage(PUBLIC_KEY, TLS_FINGERPRINT, TLS_PORT),
        )
    }

    @Test
    fun registerMissingTls() {
        assertEquals(
            "beamlink-register-v1\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETEST\n\n",
            DeviceSignaturePayload.signedRegisterMessage(PUBLIC_KEY, null, null),
        )
    }

    /**
     * Token vector must match the other two sides.
     */
    @Test
    fun tokenListed() {
        assertEquals(
            "beamlink-token-v2\n11111111-2222-4333-8444-555555555555\nchallenge-fixed-value\n$TLS_FINGERPRINT\n9091\n$NEXUS_LEAF",
            DeviceSignaturePayload.signedMessage(DEVICE_ID, CHALLENGE, TLS_FINGERPRINT, TLS_PORT, NEXUS_LEAF),
        )
    }

    @Test
    fun tokenMissingTls() {
        assertEquals(
            "beamlink-token-v2\n11111111-2222-4333-8444-555555555555\nchallenge-fixed-value\n\n\n$NEXUS_LEAF",
            DeviceSignaturePayload.signedMessage(DEVICE_ID, CHALLENGE, null, null, NEXUS_LEAF),
        )
    }

    @Test
    fun tokenEmptyNexusLeaf() {
        assertEquals(
            "beamlink-token-v2\n11111111-2222-4333-8444-555555555555\nchallenge-fixed-value\n$TLS_FINGERPRINT\n9091\n",
            DeviceSignaturePayload.signedMessage(DEVICE_ID, CHALLENGE, TLS_FINGERPRINT, TLS_PORT, null),
        )
    }

    companion object {
        private const val PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETEST"
        private const val DEVICE_ID = "11111111-2222-4333-8444-555555555555"
        private const val CHALLENGE = "challenge-fixed-value"
        private const val TLS_FINGERPRINT =
            "aaaabbbbccccddddeeeeffff0000111122223333444455556666777788889999"
        private const val TLS_PORT = 9091
        private const val NEXUS_LEAF =
            "bbbbccccddddeeeeffff0000111122223333444455556666777788889999aaaabbbb"
    }
}
