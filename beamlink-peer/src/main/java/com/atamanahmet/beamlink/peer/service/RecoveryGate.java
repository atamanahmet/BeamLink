package com.atamanahmet.beamlink.peer.service;

/**
 * One token recovery per gap when local state is APPROVED.
 */
public final class RecoveryGate {

    public static final long RECOVERY_MIN_GAP = 30_000L;

    private RecoveryGate() {
    }

    /**
     * True when local APPROVED and the last recovery is older than RECOVERY_MIN_GAP.
     */
    public static boolean canRecover(boolean localApproved, long nowMs, long lastRecoveryMs) {
        if (!localApproved) {
            return false;
        }
        return nowMs - lastRecoveryMs >= RECOVERY_MIN_GAP;
    }
}
