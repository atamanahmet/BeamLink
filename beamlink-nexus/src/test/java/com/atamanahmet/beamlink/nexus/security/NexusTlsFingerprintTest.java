package com.atamanahmet.beamlink.nexus.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NexusTlsFingerprintTest {

    private static final String CANONICAL =
            "aaaabbbbccccddddeeeeffff0000111122223333444455556666777788889999";

    @TempDir
    Path dir;

    static Stream<Arguments> canonicalizeCases() {
        return Stream.of(
                Arguments.of(
                        "spaced",
                        "aaaa bbbb cccc dddd eeee ffff 0000 1111 2222 3333 4444 5555 6666 7777 8888 9999",
                        CANONICAL),
                Arguments.of(
                        "colons",
                        "aaaa:bbbb:cccc:dddd:eeee:ffff:0000:1111:2222:3333:4444:5555:6666:7777:8888:9999",
                        CANONICAL),
                Arguments.of(
                        "upper",
                        "AAAABBBBCCCCDDDDEEEEFFFF0000111122223333444455556666777788889999",
                        CANONICAL),
                Arguments.of("plain", CANONICAL, CANONICAL));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("canonicalizeCases")
    void canonicalizeAcceptsForms(String name, String input, String expected) {
        assertThat(NexusTlsFingerprint.canonicalize(input)).isEqualTo(expected);
    }

    @Test
    void canonicalizeRejectsBadLength() {
        assertThatThrownBy(() -> NexusTlsFingerprint.canonicalize("abcd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64 hex");
        assertThatThrownBy(() -> NexusTlsFingerprint.canonicalize(
                        "aaaabbbbccccddddeeeeffff0000111122223333444455556666777788889999aa"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canonicalizeRejectsBadChars() {
        assertThatThrownBy(() -> NexusTlsFingerprint.canonicalize(
                        "aaaabbbbccccddddeeeeffff00001111222233334444555566667777888899zz"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofIsFullSha256Hex() throws Exception {
        NexusTlsCert cert = new NexusTlsCert(dir.resolve("nexus-tls.p12"), dir.resolve("nexus-tls.pass"));
        cert.loadOrCreate();
        X509Certificate leaf = cert.getLeaf();
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(leaf.getEncoded());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        assertThat(NexusTlsFingerprint.of(leaf)).isEqualTo(hex.toString());
        assertThat(NexusTlsFingerprint.canonical(leaf)).isEqualTo(hex.toString());
        assertThat(NexusTlsFingerprint.of(leaf)).hasSize(64);
    }

    @Test
    void formatIsFourLinesOfSixteen() {
        assertThat(NexusTlsFingerprint.format(CANONICAL))
                .isEqualTo(
                        "aaaabbbbccccdddd\neeeeffff00001111\n2222333344445555\n6666777788889999");
    }
}
