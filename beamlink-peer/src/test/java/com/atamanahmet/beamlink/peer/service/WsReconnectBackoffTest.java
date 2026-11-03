package com.atamanahmet.beamlink.peer.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WsReconnectBackoffTest {

    @Test
    void growth() {
        assertThat(WsReconnectBackoff.delaySeconds(0, false, bound -> bound - 1)).isEqualTo(1);
        assertThat(WsReconnectBackoff.delaySeconds(1, false, bound -> bound - 1)).isEqualTo(2);
        assertThat(WsReconnectBackoff.delaySeconds(2, false, bound -> bound - 1)).isEqualTo(4);
        assertThat(WsReconnectBackoff.delaySeconds(3, false, bound -> bound - 1)).isEqualTo(8);
        assertThat(WsReconnectBackoff.delaySeconds(4, false, bound -> bound - 1)).isEqualTo(16);
        assertThat(WsReconnectBackoff.delaySeconds(5, false, bound -> bound - 1)).isEqualTo(30);
    }

    @Test
    void cap() {
        assertThat(WsReconnectBackoff.delaySeconds(20, false, bound -> bound - 1))
                .isEqualTo(WsReconnectBackoff.CAP_SECONDS);
    }

    @Test
    void jitterInsideRange() {
        int max = 8;
        assertThat(WsReconnectBackoff.delaySeconds(3, false, bound -> 0)).isEqualTo(0);
        assertThat(WsReconnectBackoff.delaySeconds(3, false, bound -> bound - 1)).isEqualTo(max);
        int mid = WsReconnectBackoff.delaySeconds(3, false, bound -> 3);
        assertThat(mid).isBetween(0, max);
    }

    @Test
    void reset() {
        assertThat(WsReconnectBackoff.delaySeconds(0, false, bound -> bound - 1)).isEqualTo(1);
        assertThat(WsReconnectBackoff.delaySeconds(6, false, bound -> bound - 1))
                .isEqualTo(WsReconnectBackoff.CAP_SECONDS);
        assertThat(WsReconnectBackoff.delaySeconds(0, false, bound -> bound - 1)).isEqualTo(1);
    }

    @Test
    void dropBefore30sKeepsTheStep() {
        Instant up = Instant.parse("2026-01-01T00:00:00Z");
        Instant drop = up.plusSeconds(29);
        assertThat(WsReconnectBackoff.shouldResetAttempt(up, drop)).isFalse();
        assertThat(WsReconnectBackoff.attemptAfterDrop(4, up, drop)).isEqualTo(4);
        assertThat(WsReconnectBackoff.shouldResetAttempt(null, drop)).isFalse();
        assertThat(WsReconnectBackoff.attemptAfterDrop(4, null, drop)).isEqualTo(4);
    }

    @Test
    void dropAfter30sResets() {
        Instant up = Instant.parse("2026-01-01T00:00:00Z");
        Instant drop = up.plusSeconds(30);
        assertThat(WsReconnectBackoff.shouldResetAttempt(up, drop)).isTrue();
        assertThat(WsReconnectBackoff.attemptAfterDrop(4, up, drop)).isEqualTo(0);
        assertThat(WsReconnectBackoff.attemptAfterDrop(
                4, up, up.plusSeconds(WsReconnectBackoff.STABLE_SECONDS + 1))).isEqualTo(0);
    }

    @Test
    void throttleWaitsAtLeastCap() {
        assertThat(WsReconnectBackoff.delaySeconds(0, true, bound -> 0))
                .isEqualTo(WsReconnectBackoff.CAP_SECONDS);
        assertThat(WsReconnectBackoff.delaySeconds(2, true, bound -> 0))
                .isEqualTo(WsReconnectBackoff.CAP_SECONDS);
        assertThat(WsReconnectBackoff.isThrottled(new RuntimeException("slow_down"))).isTrue();
        assertThat(WsReconnectBackoff.isThrottled(new RuntimeException("nope"))).isFalse();
        WebClientResponseException tooMany = WebClientResponseException.create(
                429, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
        assertThat(WsReconnectBackoff.isThrottled(tooMany)).isTrue();
        assertThat(WsReconnectBackoff.isThrottled(new RuntimeException("wrap", tooMany))).isTrue();
    }
}
