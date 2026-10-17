package com.atamanahmet.beamlink.nexus.service;

/**
 * Pure rule for how a chunk offset relates to the expected write position.
 */
public final class ReceiveOffsetRule {

    public enum Decision {
        ACCEPT,
        DUPLICATE,
        REJECT_EARLY,
        REJECT_OVERLAP
    }

    private ReceiveOffsetRule() {}

    /**
     * Classifies chunk range against expected offset.
     */
    public static Decision decide(long offset, long length, long expected) {
        if (length < 0L) {
            throw new IllegalArgumentException("length < 0");
        }
        if (offset == expected) {
            return Decision.ACCEPT;
        }
        long endExclusive = offset + length;
        if (endExclusive <= expected) {
            return Decision.DUPLICATE;
        }
        if (offset > expected) {
            return Decision.REJECT_EARLY;
        }
        return Decision.REJECT_OVERLAP;
    }
}
