package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure rule for receive-side idle stall detection.
 */
public final class ReceiveStallRule {

    private ReceiveStallRule() {}

    /**
     * True when ACTIVE and idle longer than the stall timeout.
     */
    public static boolean isStalled(
            TransferStatus status,
            Instant lastChunkAt,
            Instant now,
            long stallTimeoutSeconds) {
        if (status != TransferStatus.ACTIVE) {
            return false;
        }
        if (lastChunkAt == null || now == null) {
            return false;
        }
        return Duration.between(lastChunkAt, now).getSeconds() > stallTimeoutSeconds;
    }
}
