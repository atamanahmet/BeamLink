package com.beamlink.peer.security

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

class NexusTlsFingerprintTest {

    @Test
    fun fingerprintOfKnownCert() {
        val leaf = selfSigned()
        val hash = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
        val hex = hash.joinToString("") { b -> "%02x".format(b) }
        val expected = hex.substring(0, 32).chunked(4).joinToString(" ")
        assertEquals(expected, NexusTlsFingerprint.of(leaf))
    }

    private fun selfSigned(): X509Certificate {
        Security.addProvider(BouncyCastleProvider())
        val gen = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
        gen.initialize(ECGenParameterSpec("secp256r1"))
        val pair = gen.generateKeyPair()
        val subject = X500Name("CN=known")
        val now = Instant.now()
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(64, SecureRandom()),
            Date.from(now.minus(1, ChronoUnit.DAYS)),
            Date.from(now.plus(365, ChronoUnit.DAYS)),
            subject,
            pair.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withECDSA")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(pair.private)
        return JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(builder.build(signer))
    }
}
