package com.atamanahmet.beamlink.peer.service;

import java.util.function.IntUnaryOperator;

/**
 * Token poll outcomes for device enrollment.
 */
public final class EnrollmentPollRule {

    public static final int DEFAULT_INTERVAL_SECONDS = 15;
    public static final int BACKOFF_CAP_SECONDS = 30;
    public static final int SLOW_DOWN_CAP_SECONDS = 60;

    public enum Action {
        STORE_TOKENS,
        KEEP_PENDING,
        SET_REJECTED_STOP,
        RETRY_CHALLENGE_ONCE,
        STOP
    }

    /**
     * Next sleep, sticky interval, and failure count after a poll.
     */
    public record Wait(int sleepSeconds, int stickySeconds, int failureCount) {
    }

    private EnrollmentPollRule() {
    }

    /**
     * Maps HTTP status and error body. Null status is a network exception.
     */
    public static Action onError(Integer httpStatus, String error, boolean grantRetried) {
        if (httpStatus == null || httpStatus == 429 || httpStatus >= 500) {
            return Action.KEEP_PENDING;
        }
        if ("authorization_pending".equals(error) || "slow_down".equals(error)) {
            return Action.KEEP_PENDING;
        }
        if ("access_denied".equals(error)) {
            return Action.SET_REJECTED_STOP;
        }
        if ("invalid_grant".equals(error) && !grantRetried) {
            return Action.RETRY_CHALLENGE_ONCE;
        }
        return Action.STOP;
    }

    /**
     * Next poll wait. Floor is base. slow_down adds 5 s, cap 60 s.
     * Network, 429, and 5xx use full jitter, cap 30 s. 200 resets.
     */
    public static Wait nextWait(
            int baseSeconds,
            int stickySeconds,
            int failureCount,
            Integer httpStatus,
            String error,
            IntUnaryOperator random) {
        int base = Math.max(1, baseSeconds);
        int sticky = Math.max(base, stickySeconds);
        if (httpStatus != null && httpStatus == 200) {
            return new Wait(base, base, 0);
        }
        if ("slow_down".equals(error)) {
            int next = Math.min(SLOW_DOWN_CAP_SECONDS, sticky + 5);
            return new Wait(next, next, failureCount);
        }
        if (httpStatus == null
                || (httpStatus == 429 && !"slow_down".equals(error))
                || httpStatus >= 500) {
            int nextFail = failureCount + 1;
            int shift = Math.min(nextFail - 1, 10);
            int exp = base * (1 << shift);
            int cap = Math.min(BACKOFF_CAP_SECONDS, exp);
            int sleep = Math.max(base, random.applyAsInt(cap + 1));
            return new Wait(sleep, sticky, nextFail);
        }
        return new Wait(sticky, sticky, failureCount);
    }
}
