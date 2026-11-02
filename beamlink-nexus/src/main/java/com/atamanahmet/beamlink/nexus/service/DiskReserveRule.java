package com.atamanahmet.beamlink.nexus.service;

/**
 * Pure check that a write leaves the reserve free.
 */
public final class DiskReserveRule {

    public static final String CODE = "INSUFFICIENT_DISK";
    public static final String DETAIL =
            "Not enough disk space. Free some space, then resume.";

    private DiskReserveRule() {}

    /**
     * True when usable space covers bytesNeeded plus reserve.
     */
    public static boolean allowed(long usable, long bytesNeeded, long reserve) {
        if (usable < 0L || bytesNeeded < 0L || reserve < 0L) {
            return false;
        }
        if (bytesNeeded > Long.MAX_VALUE - reserve) {
            return false;
        }
        return usable >= bytesNeeded + reserve;
    }
}
