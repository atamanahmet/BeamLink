package com.atamanahmet.beamlink.agent.service;

/**
 * Pure rule for whether send start offset needs a receiver /offset query.
 */
public final class StartOffsetRule {

    private StartOffsetRule() {}

    /**
     * True when local row already has progress and must sync from receiver.
     */
    public static boolean needsReceiverQuery(long localConfirmedOffset) {
        return localConfirmedOffset > 0L;
    }
}
