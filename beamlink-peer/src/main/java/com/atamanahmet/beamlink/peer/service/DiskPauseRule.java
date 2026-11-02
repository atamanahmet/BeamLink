package com.atamanahmet.beamlink.peer.service;

/**
 * Pure disk-pause decisions. No IO.
 */
public final class DiskPauseRule {

    private DiskPauseRule() {}

    /**
     * True when the sender must pause and must not retry this chunk.
     */
    public static boolean senderNoRetry(int status) {
        return status == 507;
    }

    /**
     * True when resume must keep PAUSED because the reserve is still too small.
     */
    public static boolean resumeStayPaused(boolean hasRoom) {
        return !hasRoom;
    }

    /**
     * True when this receive row is a TARGET disk pause.
     */
    public static boolean isReceiverDiskPause(
            String status, String pausedBy, String failureReason) {
        return "PAUSED".equals(status)
                && "TARGET".equals(pausedBy)
                && DiskReserveRule.CODE.equals(failureReason);
    }
}
