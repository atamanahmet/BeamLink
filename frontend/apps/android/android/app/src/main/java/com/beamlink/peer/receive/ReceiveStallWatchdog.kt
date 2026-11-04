package com.beamlink.peer.receive

import com.beamlink.peer.config.DeviceDefaults
import java.time.Instant
import java.util.UUID

/**
 * Pauses stalled receive transfers through the normal pause path.
 */
class ReceiveStallWatchdog(
    private val store: ReceiveSessionStore,
    private val tracker: ReceiveStallTracker,
    private val clock: () -> Instant = { Instant.now() },
    private val pause: (UUID) -> Unit,
) {

    /**
     * Checks tracked receives for idle stalls.
     * Re-checks last touch right before pause so a late chunk cancels it.
     */
    fun checkStalls() {
        val now = clock()
        for ((transferId, lastChunkAt) in tracker.snapshot()) {
            val session = store.get(transferId)
            if (session == null) {
                tracker.remove(transferId)
                continue
            }
            if (!ReceiveStallRule.isStalled(
                    session.status,
                    lastChunkAt,
                    now,
                    DeviceDefaults.STALL_TIMEOUT_SECONDS,
                )
            ) {
                continue
            }
            val latest = tracker.getLastTouch(transferId)
            val recheckNow = clock()
            if (latest == null || !ReceiveStallRule.isStalled(
                    session.status,
                    latest,
                    recheckNow,
                    DeviceDefaults.STALL_TIMEOUT_SECONDS,
                )
            ) {
                continue
            }
            try {
                pause(transferId)
            } catch (_: Exception) {
            }
            tracker.remove(transferId)
        }
    }

    companion object {
        const val CHECK_INTERVAL_MS = 15_000L
    }
}
