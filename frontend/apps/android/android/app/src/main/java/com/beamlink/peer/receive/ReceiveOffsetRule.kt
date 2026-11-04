package com.beamlink.peer.receive

/**
 * Pure rule for how a chunk offset relates to the expected write position.
 */
object ReceiveOffsetRule {

    enum class Decision {
        ACCEPT,
        DUPLICATE,
        REJECT_EARLY,
        REJECT_OVERLAP,
    }

    /**
     * Classifies chunk range against expected offset.
     */
    fun decide(offset: Long, length: Long, expected: Long): Decision {
        if (length < 0L) {
            throw IllegalArgumentException("length < 0")
        }
        if (offset == expected) {
            return Decision.ACCEPT
        }
        val endExclusive = offset + length
        if (endExclusive <= expected) {
            return Decision.DUPLICATE
        }
        if (offset > expected) {
            return Decision.REJECT_EARLY
        }
        return Decision.REJECT_OVERLAP
    }
}
