package com.atamanahmet.beamlink.agent.service;

/**
 * Pure helpers for speed limit override and Mb/s to bytes/s.
 */
public final class SpeedLimitMath {

    /** 1 Mb/s = 1_000_000 bits/s / 8 = 125_000 bytes/s. */
    public static final long BYTES_PER_MBPS = 125_000L;

    private SpeedLimitMath() {}

    /**
     * Override wins when set, else the settings default. Both null means unlimited.
     */
    public static Double resolveFileLimit(Double overrideMbps, Double defaultMbps) {
        if (overrideMbps != null) {
            return overrideMbps;
        }
        return defaultMbps;
    }

    /**
     * Converts decimal megabits per second to bytes per second.
     */
    public static long toBytesPerSecond(double mbps) {
        return Math.max(1L, (long) (mbps * BYTES_PER_MBPS));
    }
}
