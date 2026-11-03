package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.util.FileChecksum;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkChecksumRuleTest {

    @Test
    void acceptsMatchingHash() {
        byte[] chunk = "abc".getBytes(StandardCharsets.UTF_8);
        String hex = FileChecksum.sha256Hex(chunk);

        assertThat(ChunkChecksumRule.check(hex, chunk)).isEqualTo(ChunkChecksumRule.Result.OK);
    }

    @Test
    void rejectsWrongHash() {
        byte[] chunk = "abc".getBytes(StandardCharsets.UTF_8);

        assertThat(ChunkChecksumRule.check("deadbeef", chunk))
                .isEqualTo(ChunkChecksumRule.Result.MISMATCH);
    }

    @Test
    void rejectsMissingHeader() {
        byte[] chunk = "abc".getBytes(StandardCharsets.UTF_8);

        assertThat(ChunkChecksumRule.check(null, chunk)).isEqualTo(ChunkChecksumRule.Result.MISSING);
        assertThat(ChunkChecksumRule.check("  ", chunk)).isEqualTo(ChunkChecksumRule.Result.MISSING);
    }
}
