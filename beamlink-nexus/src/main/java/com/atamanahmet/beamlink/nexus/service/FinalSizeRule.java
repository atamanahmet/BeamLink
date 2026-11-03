package com.atamanahmet.beamlink.nexus.service;

/**
 * Pure rule for last-chunk confirmed size vs expected file size.
 */
public final class FinalSizeRule {

    private FinalSizeRule() {}

    /**
     * True when confirmed bytes equal the expected file size.
     */
    public static boolean matches(long confirmedOffset, long fileSize) {
        return confirmedOffset == fileSize;
    }
}
