package com.beamlink.peer.receive

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory last chunk time for active receive transfers.
 */
class ReceiveStallTracker {

    private val lastChunkAtByTransfer = ConcurrentHashMap<UUID, Instant>()

    /**
     * Records now as the last chunk time for this transfer.
     */
    fun touch(transferId: UUID) {
        lastChunkAtByTransfer[transferId] = Instant.now()
    }

    /**
     * Drops tracking for this transfer.
     */
    fun remove(transferId: UUID) {
        lastChunkAtByTransfer.remove(transferId)
    }

    /**
     * Last chunk time for one transfer, or null if not tracked.
     */
    fun getLastTouch(transferId: UUID): Instant? {
        return lastChunkAtByTransfer[transferId]
    }

    /**
     * Snapshot of tracked receive transfers and last chunk times.
     */
    fun snapshot(): Map<UUID, Instant> {
        return HashMap(lastChunkAtByTransfer)
    }
}
