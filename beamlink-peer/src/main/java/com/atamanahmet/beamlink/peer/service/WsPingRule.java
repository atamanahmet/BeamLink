package com.atamanahmet.beamlink.peer.service;

import java.time.Duration;
import java.time.Instant;

public final class WsPingRule {

    public static final int PING_INTERVAL_SECONDS = 15;
    public static final int MAX_MISSED_PONGS = 2;

    private WsPingRule() {
    }

    /**
     * True when the socket is up and last pong is older than interval times max missed.
     */
    public static boolean shouldClose(boolean connected, Instant lastPongAt, Instant now) {
        if (!connected) {
            return false;
        }
        if (lastPongAt == null || now == null) {
            return false;
        }
        long elapsed = Duration.between(lastPongAt, now).getSeconds();
        return elapsed >= (long) PING_INTERVAL_SECONDS * MAX_MISSED_PONGS;
    }
}
