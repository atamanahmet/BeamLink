package com.beamlink.peer.receive

import com.beamlink.peer.util.FileChecksum

/**
 * Pure checks for the X-Chunk-SHA256 header vs chunk bytes.
 */
object ChunkChecksumRule {

    enum class Result {
        OK,
        MISSING,
        MISMATCH,
    }

    /**
     * Returns MISSING, MISMATCH, or OK for the header and chunk bytes.
     */
    fun check(headerHex: String?, chunk: ByteArray?): Result {
        if (headerHex.isNullOrBlank()) {
            return Result.MISSING
        }
        if (chunk == null) {
            return Result.MISMATCH
        }
        val actual = FileChecksum.sha256Hex(chunk)
        if (!headerHex.trim().equals(actual, ignoreCase = true)) {
            return Result.MISMATCH
        }
        return Result.OK
    }
}
