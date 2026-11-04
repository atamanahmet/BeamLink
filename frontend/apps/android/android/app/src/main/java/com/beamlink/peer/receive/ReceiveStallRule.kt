package com.beamlink.peer.receive

import java.time.Duration
import java.time.Instant

/**
 * Pure receive idle stall decision. No IO.
 */
object ReceiveStallRule {

    /**
     * True when ACTIVE and idle longer than the stall timeout.
     */
    fun isStalled(
        status: String,
        lastChunkAt: Instant?,
        now: Instant?,
        stallTimeoutSeconds: Long,
    ): Boolean {
        if (status != ReceiveSession.ACTIVE) {
            return false
        }
        if (lastChunkAt == null || now == null) {
            return false
        }
        return Duration.between(lastChunkAt, now).seconds > stallTimeoutSeconds
    }
}
