package com.atamanahmet.beamlink.nexus.websocket;

import com.atamanahmet.beamlink.nexus.websocket.enums.WsMessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class NexusUiWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper;

    private final Set<WebSocketSession> uiSessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        uiSessions.add(session);
        log.info("UI connected via WS: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        uiSessions.remove(session);
        log.info("UI WS disconnected: {}", session.getId());
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        log.debug("Ignoring WS message from UI session {}", session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("UI WS transport error on session {}: {}", session.getId(), exception.getMessage());
        uiSessions.remove(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * Push a typed envelope to all connected UI sessions.
     */
    public <T> void push(WsMessageType type, T payload) {
        if (uiSessions.isEmpty()) return;

        WsEnvelope<T> envelope = WsEnvelope.of(type, payload);
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.error("Failed to serialize UI push payload for type {}: {}", type, e.getMessage());
            return;
        }

        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : uiSessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    log.warn("Failed to push to UI session {}: {}", session.getId(), e.getMessage());
                    uiSessions.remove(session);
                }
            }
        }
    }
}