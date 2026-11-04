package com.beamlink.peer.register

/**
 * One token recovery per gap when local state is APPROVED.
 */
object RecoveryGate {

    const val RECOVERY_MIN_GAP = 30_000L

    /**
     * True when local APPROVED and the last recovery is older than RECOVERY_MIN_GAP.
     */
    fun canRecover(localApproved: Boolean, nowMs: Long, lastRecoveryMs: Long): Boolean {
        if (!localApproved) {
            return false
        }
        return nowMs - lastRecoveryMs >= RECOVERY_MIN_GAP
    }
}
