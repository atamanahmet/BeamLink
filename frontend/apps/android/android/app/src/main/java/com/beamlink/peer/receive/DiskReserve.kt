package com.beamlink.peer.receive

/**
 * Phone disk-reserve constants. Same bounds as desktop.
 */
object DiskReserve {
    const val DEFAULT_BYTES = 1_073_741_824L
    const val MIN_BYTES = 268_435_456L
    const val MAX_BYTES = 1_099_511_627_776L
    const val DETAIL = "Not enough disk space. Free some space, then resume."
    const val CODE = "INSUFFICIENT_DISK"

    /**
     * Rejects a value outside min and max.
     */
    fun validate(bytes: Long): Long {
        if (bytes < MIN_BYTES || bytes > MAX_BYTES) {
            throw IllegalArgumentException(
                "minFreeSpaceBytes must be between $MIN_BYTES and $MAX_BYTES",
            )
        }
        return bytes
    }
}
