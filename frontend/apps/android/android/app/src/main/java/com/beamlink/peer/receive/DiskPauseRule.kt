package com.beamlink.peer.receive

/**
 * Pure disk-pause decisions. No IO.
 */
object DiskPauseRule {

    /**
     * True when the sender must pause and must not retry this chunk.
     */
    fun senderNoRetry(status: Int): Boolean {
        return status == 507
    }

    /**
     * True when resume must keep PAUSED because the reserve is still too small.
     */
    fun resumeStayPaused(hasRoom: Boolean): Boolean {
        return !hasRoom
    }

    /**
     * True when this receive row is a TARGET disk pause.
     */
    fun isReceiverDiskPause(
        status: String,
        pausedBy: String?,
        failureReason: String?,
    ): Boolean {
        return status == ReceiveSession.PAUSED
            && pausedBy == "TARGET"
            && failureReason == DiskReserve.CODE
    }
}
