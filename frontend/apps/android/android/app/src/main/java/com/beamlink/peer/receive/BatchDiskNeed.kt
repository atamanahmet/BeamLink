package com.beamlink.peer.receive

import com.beamlink.peer.exception.InvalidRequestException

/**
 * Bytes needed for a batch disk check from file sizes only.
 */
object BatchDiskNeed {

    /**
     * Sums file sizes with overflow checks. Body totalSize is ignored.
     */
    @Suppress("UNUSED_PARAMETER")
    fun requiredBytes(fileSizes: List<Long>, totalSize: Long = 0L): Long {
        var required = 0L
        for (size in fileSizes) {
            FileSizeRule.requireValid(size)
            try {
                required = Math.addExact(required, size)
            } catch (_: ArithmeticException) {
                throw InvalidRequestException("Batch size overflow")
            }
        }
        return required
    }
}
