package com.atamanahmet.beamlink.peer.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WsPingRuleTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:01:00Z");

    @Test
    void pongInTime() {
        Instant lastPong = Instant.parse("2026-01-01T00:00:50Z");
        assertThat(WsPingRule.shouldClose(true, lastPong, NOW)).isFalse();
    }

    @Test
    void oneMissed() {
        Instant lastPong = Instant.parse("2026-01-01T00:00:45Z");
        assertThat(WsPingRule.shouldClose(true, lastPong, NOW)).isFalse();
    }

    @Test
    void twoMissedCloses() {
        Instant lastPong = Instant.parse("2026-01-01T00:00:30Z");
        assertThat(WsPingRule.shouldClose(true, lastPong, NOW)).isTrue();
    }

    @Test
    void pongResetsTheCount() {
        Instant stale = Instant.parse("2026-01-01T00:00:30Z");
        assertThat(WsPingRule.shouldClose(true, stale, NOW)).isTrue();
        assertThat(WsPingRule.shouldClose(true, NOW, NOW)).isFalse();
    }

    @Test
    void notConnected() {
        Instant lastPong = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(WsPingRule.shouldClose(false, lastPong, NOW)).isFalse();
        assertThat(WsPingRule.shouldClose(true, null, NOW)).isFalse();
        assertThat(WsPingRule.shouldClose(true, lastPong, null)).isFalse();
    }
}
