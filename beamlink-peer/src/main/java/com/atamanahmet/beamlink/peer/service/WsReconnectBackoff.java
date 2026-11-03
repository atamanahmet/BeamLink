package com.atamanahmet.beamlink.peer.service;

import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakeException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.function.IntUnaryOperator;

public final class WsReconnectBackoff {

    public static final int BASE_SECONDS = 1;
    public static final int FACTOR = 2;
    public static final int CAP_SECONDS = 30;
    public static final int STABLE_SECONDS = 30;

    private static final int MAX_CAUSE_DEPTH = 10;

    private WsReconnectBackoff() {
    }

    /**
     * Full jitter delay in seconds. Throttle waits at least the cap.
     */
    public static int delaySeconds(int attempt, boolean throttled, IntUnaryOperator random) {
        if (throttled) {
            return CAP_SECONDS;
        }
        int shift = Math.min(Math.max(attempt, 0), 10);
        long exp = (long) BASE_SECONDS;
        for (int i = 0; i < shift; i++) {
            if (exp > (long) CAP_SECONDS / FACTOR) {
                exp = CAP_SECONDS;
                break;
            }
            exp *= FACTOR;
        }
        int max = (int) Math.min(CAP_SECONDS, exp);
        int bound = max + 1;
        if (bound <= 1) {
            return 0;
        }
        return random.applyAsInt(bound);
    }

    /**
     * True when the socket stayed up at least STABLE_SECONDS. Null times keep the step.
     */
    public static boolean shouldResetAttempt(Instant connectedAt, Instant droppedAt) {
        if (connectedAt == null || droppedAt == null) {
            return false;
        }
        return Duration.between(connectedAt, droppedAt).getSeconds() >= STABLE_SECONDS;
    }

    /**
     * Zero after a stable period. Same attempt when the drop is early.
     */
    public static int attemptAfterDrop(int attempt, Instant connectedAt, Instant droppedAt) {
        if (shouldResetAttempt(connectedAt, droppedAt)) {
            return 0;
        }
        return Math.max(attempt, 0);
    }

    /**
     * True when the error is HTTP 429 or slow_down.
     */
    public static boolean isThrottled(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof WebSocketClientHandshakeException hs
                    && hs.response() != null
                    && hs.response().status().code() == 429) {
                return true;
            }
            if (current instanceof WebClientResponseException w
                    && w.getStatusCode().value() == 429) {
                return true;
            }
            String msg = current.getMessage();
            if (msg != null && (msg.contains("slow_down") || msg.contains("429"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
