package com.atamanahmet.beamlink.peer.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FileChecksumTest {

    @Test
    void sha256HexChunkMatchesKnownVector() {
        byte[] chunk = "abc".getBytes(StandardCharsets.UTF_8);

        assertThat(FileChecksum.sha256Hex(chunk))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void matchesIsCaseInsensitive() {
        String lower = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        String upper = lower.toUpperCase();

        assertThat(FileChecksum.matches(lower, upper)).isTrue();
        assertThat(FileChecksum.matches(lower, "deadbeef")).isFalse();
        assertThat(FileChecksum.matches(null, lower)).isFalse();
    }
}
