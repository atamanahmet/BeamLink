package com.atamanahmet.beamlink.nexus.websocket;

import org.springframework.web.socket.WebSocketSession;

/**
 * Compares registered and closing WebSocket sessions by identity.
 */
public final class WsSessionIdentity {

    private WsSessionIdentity() {}

    /**
     * True when the closing session is still the registered one.
     */
    public static boolean isRegisteredSession(
            WebSocketSession registered, WebSocketSession closing) {
        return registered != null && closing != null && registered == closing;
    }
}
