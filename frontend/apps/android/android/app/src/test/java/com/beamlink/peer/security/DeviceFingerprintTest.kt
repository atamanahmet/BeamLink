package com.beamlink.peer.security

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec

class DeviceFingerprintTest {

    @Test
    fun fingerprintKnownVector() {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        val pair = gen.generateKeyPair()
        val spki = pair.public.encoded
        val hash = MessageDigest.getInstance("SHA-256").digest(spki)
        val hex = hash.joinToString("") { b -> "%02x".format(b) }
        val expected = hex.substring(0, 32).chunked(4).joinToString(" ")
        assertEquals(expected, DeviceKey.fingerprint(spki))
    }
}
