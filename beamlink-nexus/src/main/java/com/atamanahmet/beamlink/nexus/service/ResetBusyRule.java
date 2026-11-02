package com.atamanahmet.beamlink.nexus.service;

/**
 * Decides when reset must return 409.
 */
public final class ResetBusyRule {

    private ResetBusyRule() {
    }

    /**
     * True when this status blocks reset.
     */
    public static boolean isBlocking(String status) {
        return "ACTIVE".equals(status) || "PENDING".equals(status);
    }

    /**
     * True when any listed status is ACTIVE or PENDING.
     */
    public static boolean isBusy(Iterable<String> statuses) {
        return isBusy(statuses, false);
    }

    /**
     * Same as isBusy, but force=true never blocks. PAUSED never blocks.
     */
    public static boolean isBusy(Iterable<String> statuses, boolean force) {
        if (force) {
            return false;
        }
        if (statuses == null) {
            return false;
        }
        for (String status : statuses) {
            if (isBlocking(status)) {
                return true;
            }
        }
        return false;
    }

    /**
     * How many listed statuses are ACTIVE or PENDING.
     */
    public static int blockingCount(Iterable<String> statuses) {
        if (statuses == null) {
            return 0;
        }
        int count = 0;
        for (String status : statuses) {
            if (isBlocking(status)) {
                count++;
            }
        }
        return count;
    }
}
