package com.atamanahmet.beamlink.peer.service;

/**
 * Pure rule for sender reaction to OFFSET_MISMATCH.
 */
public final class OffsetResyncRule {

    public enum Decision {
        CONTINUE,
        PAUSE
    }

    private OffsetResyncRule() {}

    /**
     * Next no-progress count after a resync target.
     */
    public static int nextNoProgressCount(
            long expectedOffset, Long lastExpectedOffset, int currentCount) {
        if (lastExpectedOffset != null && lastExpectedOffset == expectedOffset) {
            return currentCount + 1;
        }
        return 1;
    }

    /**
     * CONTINUE when offset is in file and no-progress count is under 3.
     */
    public static Decision decide(long expectedOffset, long fileSize, int noProgressCount) {
        if (expectedOffset < 0L || expectedOffset > fileSize) {
            return Decision.PAUSE;
        }
        if (noProgressCount >= 3) {
            return Decision.PAUSE;
        }
        return Decision.CONTINUE;
    }
}
