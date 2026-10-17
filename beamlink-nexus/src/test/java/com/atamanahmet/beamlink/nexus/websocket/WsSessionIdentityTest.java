package com.atamanahmet.beamlink.nexus.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WsSessionIdentityTest {

    @Test
    void onlySameInstanceIsRegistered() {
        WebSocketSession a = mock(WebSocketSession.class);
        WebSocketSession b = mock(WebSocketSession.class);

        assertThat(WsSessionIdentity.isRegisteredSession(a, a)).isTrue();
        assertThat(WsSessionIdentity.isRegisteredSession(a, b)).isFalse();
        assertThat(WsSessionIdentity.isRegisteredSession(null, a)).isFalse();
        assertThat(WsSessionIdentity.isRegisteredSession(a, null)).isFalse();
        assertThat(WsSessionIdentity.isRegisteredSession(null, null)).isFalse();
    }
}
