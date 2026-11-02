package com.atamanahmet.beamlink.nexus.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;

class NexusTlsFingerprintTest {

    @TempDir
    Path dir;

    @Test
    void groupsFirst32HexCharsBy4() throws Exception {
        NexusTlsCert cert = new NexusTlsCert(dir.resolve("nexus-tls.p12"), dir.resolve("nexus-tls.pass"));
        cert.loadOrCreate();
        X509Certificate leaf = cert.getLeaf();
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
