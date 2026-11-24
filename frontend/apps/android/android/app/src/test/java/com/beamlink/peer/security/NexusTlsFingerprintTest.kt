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
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

class NexusTlsFingerprintTest {

    private val canonical =
        "aaaabbbbccccddddeeeeffff0000111122223333444455556666777788889999"

    @Test
    fun canonicalizeSpaced() {
        assertEquals(
            canonical,
            NexusTlsFingerprint.canonicalize(
                "aaaa bbbb cccc dddd eeee ffff 0000 1111 2222 3333 4444 5555 6666 7777 8888 9999",
            ),
        )
    }

    @Test
    fun canonicalizeColons() {
        assertEquals(
            canonical,
            NexusTlsFingerprint.canonicalize(
                "aaaa:bbbb:cccc:dddd:eeee:ffff:0000:1111:2222:3333:4444:5555:6666:7777:8888:9999",
            ),
        )
    }

    @Test
    fun canonicalizeUpper() {
        assertEquals(
            canonical,
            NexusTlsFingerprint.canonicalize(
                "AAAABBBBCCCCDDDDEEEEFFFF0000111122223333444455556666777788889999",
            ),
        )
    }

    @Test
    fun canonicalizeRejectsBadLength() {
        assertThrows(IllegalArgumentException::class.java) {
            NexusTlsFingerprint.canonicalize("abcd")
        }
    }

    @Test
    fun canonicalizeRejectsBadChars() {
        assertThrows(IllegalArgumentException::class.java) {
            NexusTlsFingerprint.canonicalize(
                "aaaabbbbccccddddeeeeffff00001111222233334444555566667777888899zz",
            )
        }
    }

    @Test
    fun ofIsFullSha256Hex() {
        val leaf = selfSigned()
        val hash = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
        val hex = hash.joinToString("") { b -> "%02x".format(b) }
        assertEquals(hex, NexusTlsFingerprint.of(leaf))
        assertEquals(64, NexusTlsFingerprint.of(leaf).length)
    }

    @Test
    fun formatIsFourLinesOfSixteen() {
        assertEquals(
            "aaaabbbbccccdddd\neeeeffff00001111\n2222333344445555\n6666777788889999",
            NexusTlsFingerprint.format(canonical),
        )
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
