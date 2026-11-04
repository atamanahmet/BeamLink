package com.beamlink.peer.security

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

class PinnedLeafTrustTest {

    @Test
    fun acceptsMatchingLeaf() {
        val leaf = valid("match")
        PinnedLeafTrust.accept(
            arrayOf(leaf),
            { NexusTlsFingerprint.of(leaf) },
            "missing",
            "mismatch",
        )
    }

    @Test
    fun rejectsDifferentLeaf() {
        val expected = valid("a")
        val other = valid("b")
        val ex = assertThrows(CertificateException::class.java) {
            PinnedLeafTrust.accept(
                arrayOf(other),
                { NexusTlsFingerprint.of(expected) },
                "missing",
                "mismatch",
            )
        }
        assertEquals(
            "mismatch expected=${NexusTlsFingerprint.of(expected)} seen=${NexusTlsFingerprint.of(other)}",
            ex.message,
        )
    }

    @Test
    fun rejectsWhenPinMissing() {
        val leaf = valid("any")
        val ex = assertThrows(CertificateException::class.java) {
            PinnedLeafTrust.accept(
                arrayOf(leaf),
                { null },
                "missing",
                "mismatch",
            )
        }
        assertEquals("missing", ex.message)
    }

    private fun valid(cn: String): X509Certificate {
        return selfSigned(
            cn,
            Instant.now().minus(1, ChronoUnit.DAYS),
            Instant.now().plus(365, ChronoUnit.DAYS),
        )
    }

    private fun selfSigned(cn: String, notBefore: Instant, notAfter: Instant): X509Certificate {
        Security.addProvider(BouncyCastleProvider())
        val gen = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
        gen.initialize(ECGenParameterSpec("secp256r1"))
        val pair = gen.generateKeyPair()
        val subject = X500Name("CN=$cn")
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(64, SecureRandom()),
            Date.from(notBefore),
            Date.from(notAfter),
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
