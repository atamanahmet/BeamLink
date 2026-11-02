package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.util.FileChecksum;

/**
 * Pure checks for the X-Chunk-SHA256 header vs chunk bytes.
 */
public final class ChunkChecksumRule {

    public enum Result {
        OK,
        MISSING,
        MISMATCH
    }

    private ChunkChecksumRule() {}

    /**
     * Returns MISSING, MISMATCH, or OK for the header and chunk bytes.
     */
    public static Result check(String headerHex, byte[] chunk) {
        if (headerHex == null || headerHex.isBlank()) {
            return Result.MISSING;
        }
        if (chunk == null) {
            return Result.MISMATCH;
        }
        String actual = FileChecksum.sha256Hex(chunk);
        if (!FileChecksum.matches(headerHex, actual)) {
            return Result.MISMATCH;
        }
        return Result.OK;
    }
}
