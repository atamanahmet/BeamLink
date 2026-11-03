package com.atamanahmet.beamlink.peer.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WsConnectGuardTest {

    @Test
    void connected() {
        assertThat(WsConnectGuard.shouldConnect(true, false, false)).isFalse();
        assertThat(WsConnectGuard.shouldConnect(true, true, true)).isFalse();
    }

    @Test
    void connecting() {
        assertThat(WsConnectGuard.shouldConnect(false, true, false)).isFalse();
    }

    @Test
    void scheduled() {
        assertThat(WsConnectGuard.shouldConnect(false, false, true)).isFalse();
    }

    @Test
    void idle() {
        assertThat(WsConnectGuard.shouldConnect(false, false, false)).isTrue();
    }
}
