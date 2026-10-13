package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.event.WsConnectionEvent;
import com.atamanahmet.beamlink.agent.event.WsMessageEvent;
import com.atamanahmet.beamlink.agent.websocket.WsEnvelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexusWebSocketService {

    private final AgentService agentService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final AtomicReference<WebSocketSession> sessionRef = new AtomicReference<>();
    private final ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();

    private static final Duration RECONNECT_DELAY = Duration.ofSeconds(10);
    private static final String WS_PATH = "/ws/agents";

    private volatile String nexusBaseUrl;

    public void connect(String nexusUrl) {
        if (connected.get()) {
            log.debug("Already connected, skipping.");
            return;
        }

        String token = agentService.getAuthToken();
        if (token == null || token.isBlank()) {
            log.warn("Cannot connect WS, no auth token yet.");
            return;
        }

        nexusBaseUrl = nexusUrl;

        /* http → ws, https → wss */
        URI uri = URI.create(
                nexusUrl.replaceFirst("^http://", "ws://")
                        .replaceFirst("^https://", "wss://") + WS_PATH
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Token", token);

        log.info("Connecting to Nexus WS at {}...", uri);

        client.execute(uri, headers, this::handleSession)
                .doOnError(err -> {
                    log.warn("WS connection failed: {}", err.getMessage());
                    connected.set(false);
                    scheduleReconnect();
                })
                .subscribe();
    }

    /**
     * Called when WS session is established.
     * Listens for incoming messages until session closes.
     */
    private Mono<Void> handleSession(WebSocketSession session) {
        sessionRef.set(session);
        connected.set(true);
        reconnectScheduled.set(false);

        log.info("Connected to Nexus WS at {}", session.getHandshakeInfo().getUri());
        eventPublisher.publishEvent(new WsConnectionEvent(this, WsConnectionEvent.Type.CONNECTED));

        return session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(this::handleMessage)
                .doOnTerminate(() -> {
                    connected.set(false);
                    sessionRef.set(null);
                    log.warn("WS session terminated. Scheduling reconnect...");
                    eventPublisher.publishEvent(new WsConnectionEvent(this, WsConnectionEvent.Type.DISCONNECTED));
                    scheduleReconnect();
                })
                .then();
    }

    private Mono<Void> handleMessage(String raw) {
        try {
            WsEnvelope<JsonNode> envelope = objectMapper.readValue(
                    raw,
                    objectMapper.getTypeFactory().constructParametricType(WsEnvelope.class, JsonNode.class)
            );
            if (envelope.getType() != null) {
                eventPublisher.publishEvent(new WsMessageEvent(this, envelope));
            }
        } catch (Exception e) {
            log.error("Failed to parse WS message: {}", e.getMessage());
        }
        return Mono.empty();
    }

    /**
     * Prevents duplicate reconnect attempts with AtomicBoolean guard.
     */
    private void scheduleReconnect() {
        if (!reconnectScheduled.compareAndSet(false, true)) {
            log.debug("Reconnect already scheduled, skipping duplicate.");
            return;
        }

        Mono.delay(RECONNECT_DELAY)
                .doOnNext(l -> log.info("Attempting WS reconnect..."))
                .doOnError(err -> {
                    log.warn("Reconnect error: {}", err.getMessage());
                    reconnectScheduled.set(false);
                })
                .onErrorComplete()
                .subscribe(l -> {
                    reconnectScheduled.set(false);
                    connect(nexusBaseUrl);
                });
    }

    public void send(Object dto) {
        WebSocketSession session = sessionRef.get();
        if (session == null || !session.isOpen()) {
            log.warn("Cannot send, WS session is not open.");
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(dto);
            session.send(Mono.just(session.textMessage(json)))
                    .doOnError(ex -> {
                        log.error("WS send failed: {}", ex.getMessage());
                        eventPublisher.publishEvent(new WsConnectionEvent(this, WsConnectionEvent.Type.DISCONNECTED));
                    })
                    .subscribe();
        } catch (Exception e) {
            log.error("Failed to serialize WS message: {}", e.getMessage());
        }
    }

    public boolean isConnected() {
        return connected.get();
    }
}