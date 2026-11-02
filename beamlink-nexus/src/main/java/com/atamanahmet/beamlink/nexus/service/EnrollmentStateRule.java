package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;

/**
 * Maps nexus device state and poll timing to token poll outcomes.
 */
public final class EnrollmentStateRule {

    public static final int DEFAULT_INTERVAL_SECONDS = 15;
    public static final int SLOW_DOWN_CAP_SECONDS = 60;

    public enum Outcome {
        APPROVED,
        PENDING,
        REJECTED,
        UNKNOWN,
        SLOW_DOWN
    }

    private EnrollmentStateRule() {
    }

    /**
     * State mapping before slow_down. Unknown when the row is missing.
     */
    public static Outcome mapState(DeviceState state) {
        if (state == null) {
            return Outcome.UNKNOWN;
        }
        if (state == DeviceState.REJECTED) {
            return Outcome.REJECTED;
        }
        if (state == DeviceState.PENDING_APPROVAL) {
            return Outcome.PENDING;
        }
        if (state == DeviceState.APPROVED) {
            return Outcome.APPROVED;
        }
        return Outcome.UNKNOWN;
    }

    /**
     * True when the client polled sooner than interval seconds.
     */
    public static boolean isSlowDown(long nowMs, Long lastPollMs, int intervalSeconds) {
        if (lastPollMs == null) {
            return false;
        }
        return nowMs - lastPollMs < intervalSeconds * 1000L;
    }

    /**
     * Next interval after a slow_down response. Cap is 60 s.
     */
    public static int bumpInterval(int previousSeconds) {
        return Math.min(SLOW_DOWN_CAP_SECONDS, previousSeconds + 5);
    }
}
