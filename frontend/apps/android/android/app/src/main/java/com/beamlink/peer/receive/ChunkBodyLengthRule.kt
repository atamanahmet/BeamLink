package com.beamlink.peer.receive

import com.beamlink.peer.config.DeviceDefaults
import com.beamlink.peer.exception.InvalidRequestException

/**
 * Content-Length must match Content-Range length and stay within the chunk cap.
 */
object ChunkBodyLengthRule {

    /**
     * Rejects a missing, oversize, or mismatched Content-Length before the body is read.
     */
    fun requireBeforeReceive(contentLength: Long?, rangeLength: Long) {
        if (contentLength == null ||
            contentLength > DeviceDefaults.CHUNK_SIZE_BYTES ||
            contentLength != rangeLength
        ) {
            throw InvalidRequestException("Invalid Content-Length for chunk: $contentLength")
        }
    }

    /**
     * Rejects a body whose size drifted from the Content-Range length.
     */
    fun requireAfterReceive(bodySize: Int, rangeLength: Long) {
        if (bodySize.toLong() != rangeLength) {
            throw InvalidRequestException("Invalid Content-Length for chunk: $bodySize")
        }
    }
}
