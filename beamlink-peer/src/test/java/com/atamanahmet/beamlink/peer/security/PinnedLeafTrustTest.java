package com.atamanahmet.beamlink.peer.security;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PinnedLeafTrustTest {

    @Test
    void acceptsMatchingLeaf() throws Exception {
        X509Certificate leaf = valid("match");
        PinnedLeafTrust.accept(
                new X509Certificate[]{leaf},
                () -> NexusTlsFingerprint.of(leaf),
                "missing",
                "mismatch");
    }

    @Test
    void rejectsDifferentLeaf() throws Exception {
        X509Certificate expected = valid("a");
        X509Certificate other = valid("b");
        assertThatThrownBy(() -> PinnedLeafTrust.accept(
                new X509Certificate[]{other},
                () -> NexusTlsFingerprint.of(expected),
                "missing",
                "mismatch"))
                .isInstanceOf(CertificateException.class)
                .hasMessageContaining("mismatch")
                .hasMessageContaining(NexusTlsFingerprint.of(expected))
                .hasMessageContaining(NexusTlsFingerprint.of(other));
    }

    @Test
    void sslEngineAcceptsMatchingLeaf() throws Exception {
        X509Certificate leaf = valid("match");
        PinnedLeafTrust trust = new PinnedLeafTrust(
                () -> NexusTlsFingerprint.of(leaf), "missing", "mismatch");
        trust.checkServerTrusted(new X509Certificate[]{leaf}, "ECDHE_ECDSA", engine());
    }

    @Test
    void sslEngineRejectsDifferentLeaf() throws Exception {
        X509Certificate expected = valid("a");
        X509Certificate other = valid("b");
        PinnedLeafTrust trust = new PinnedLeafTrust(
                () -> NexusTlsFingerprint.of(expected), "missing", "mismatch");
        assertThatThrownBy(() -> trust.checkServerTrusted(
                new X509Certificate[]{other}, "ECDHE_ECDSA", engine()))
                .isInstanceOf(CertificateException.class)
                .hasMessageContaining("mismatch")
                .hasMessageContaining(NexusTlsFingerprint.of(expected))
                .hasMessageContaining(NexusTlsFingerprint.of(other));
    }

    @Test
    void rejectsWhenPinMissing() throws Exception {
        X509Certificate leaf = valid("any");
        assertThatThrownBy(() -> PinnedLeafTrust.accept(
                new X509Certificate[]{leaf},
                () -> null,
                "missing",
                "mismatch"))
                .isInstanceOf(CertificateException.class)
                .hasMessage("missing");
    }

    /**
     * Dummy SSLEngine so the extended overload is the one under test.
     */
    private static SSLEngine engine() throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, null, new SecureRandom());
        return ctx.createSSLEngine();
    }

    private static X509Certificate valid(String cn) throws Exception {
        return TestEcCerts.selfSigned(
                cn,
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS));
    }
}
