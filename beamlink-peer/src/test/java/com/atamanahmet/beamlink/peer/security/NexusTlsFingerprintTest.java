package com.atamanahmet.beamlink.peer.security;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class NexusTlsFingerprintTest {

    @Test
    void groupsFirst32HexCharsBy4() throws Exception {
        X509Certificate leaf = TestEcCerts.selfSigned(
                "fp",
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS));
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(leaf.getEncoded());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        String expected = hex.substring(0, 32).replaceAll("(.{4})", "$1 ").trim();
        assertThat(NexusTlsFingerprint.of(leaf)).isEqualTo(expected);
        assertThat(NexusTlsFingerprint.of(leaf).split(" ")).hasSize(8);
    }
}
