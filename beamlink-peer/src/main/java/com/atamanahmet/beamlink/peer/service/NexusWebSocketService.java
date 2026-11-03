package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.event.WsConnectionEvent;
import com.atamanahmet.beamlink.peer.event.WsMessageEvent;
import com.atamanahmet.beamlink.peer.exception.ErrorLog;
import com.atamanahmet.beamlink.peer.security.NexusTlsPin;
import com.atamanahmet.beamlink.peer.websocket.WsEnvelope;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexusWebSocketService {

    private final DeviceService deviceService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    @Qualifier("nexusWsHttpClient")
    private final HttpClient nexusWsHttpClient;
    private final NexusTlsPin nexusTlsPin;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final AtomicBoolean reconnectEnabled = new AtomicBoolean(true);
    private final AtomicBoolean throttleReconnect = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);
    private final AtomicReference<WebSocketSession> sessionRef = new AtomicReference<>();
    private final AtomicReference<Instant> connectedAt = new AtomicReference<>();
    private ReactorNettyWebSocketClient client;

    private static final String WS_PATH = "/ws/devices";

    private volatile String nexusBaseUrl;

    /**
     * Builds the WS client on the pinned HTTP client.
     */
    @PostConstruct
    void initClient() {
        client = new ReactorNettyWebSocketClient(nexusWsHttpClient);
    }

    /**
     * Starts one handshake. No-op when connected, connecting, or backoff is waiting.
     */
    public void connect(String nexusUrl) {
        boolean backoffWaiting = reconnectEnabled.get() && reconnectScheduled.get();
        if (!WsConnectGuard.shouldConnect(connected.get(), connecting.get(), backoffWaiting)) {
            log.debug("WS connect skipped (connected={}, connecting={}, scheduled={})",
                    connected.get(), connecting.get(), backoffWaiting);
            return;
        }
        if (!connecting.compareAndSet(false, true)) {
            log.debug("WS connect skipped, already connecting.");
            return;
        }
        reconnectEnabled.set(true);
        if (!nexusTlsPin.hasPin()) {
            log.warn("Cannot connect WS, nexus TLS pin is not confirmed.");
            connecting.set(false);
            return;
        }

        String token = deviceService.getAuthToken();
        if (token == null || token.isBlank()) {
            log.warn("Cannot connect WS, no auth token yet.");
            connecting.set(false);
            return;
        }

        nexusBaseUrl = nexusUrl;

        try {
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
                        log.warn("WS connection failed: {}", ErrorLog.formatChain(err));
                        connecting.set(false);
                        connected.set(false);
                        if (WsReconnectBackoff.isThrottled(err)) {
                            throttleReconnect.set(true);
                        }
                        scheduleReconnect();
                    })
                    .subscribe(null, err -> log.warn("WS connect subscribe error: {}", err.getMessage()));
        } catch (RuntimeException e) {
            connecting.set(false);
            throw e;
        }
    }

    /**
     * Session up. RFC ping every interval. Missed pongs close and reuse terminate path.
     */
    private Mono<Void> handleSession(WebSocketSession session) {
        sessionRef.set(session);
        connecting.set(false);
        connected.set(true);
        reconnectScheduled.set(false);
        connectedAt.set(Instant.now());

        log.info("Connected to Nexus WS at {}", session.getHandshakeInfo().getUri());
        eventPublisher.publishEvent(new WsConnectionEvent(this, WsConnectionEvent.Type.CONNECTED));

        AtomicReference<Instant> lastPongAt = new AtomicReference<>(Instant.now());
        Disposable pingLoop = Flux.interval(Duration.ofSeconds(WsPingRule.PING_INTERVAL_SECONDS))
                .subscribe(tick -> {
                    if (WsPingRule.shouldClose(
                            connected.get() && session.isOpen(),
                            lastPongAt.get(),
                            Instant.now())) {
                        log.warn("WS ping timeout, closing session");
                        session.close().subscribe();
                        return;
                    }
                    sendPing(session);
                });

        AtomicReference<Throwable> fail = new AtomicReference<>();
        return session.receive()
                .doOnNext(msg -> {
                    if (msg.getType() == WebSocketMessage.Type.PONG) {
                        lastPongAt.set(Instant.now());
                    }
                })
                .filter(msg -> msg.getType() == WebSocketMessage.Type.TEXT)
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(this::handleMessage)
                .doOnError(fail::set)
                .doOnTerminate(() -> {
                    pingLoop.dispose();
                    Instant droppedAt = Instant.now();
                    reconnectAttempt.set(WsReconnectBackoff.attemptAfterDrop(
                            reconnectAttempt.get(), connectedAt.getAndSet(null), droppedAt));
                    connecting.set(false);
                    connected.set(false);
                    sessionRef.set(null);
                    log.warn("WS session terminated. Scheduling reconnect... {}", ErrorLog.formatChain(fail.get()));
                    eventPublisher.publishEvent(new WsConnectionEvent(this, WsConnectionEvent.Type.DISCONNECTED));
                    scheduleReconnect();
                })
                .then();
    }

    /**
     * Sends one RFC 6455 ping frame. Empty payload.
     */
    private void sendPing(WebSocketSession session) {
        if (session == null || !session.isOpen()) {
            return;
        }
        session.send(Mono.just(session.pingMessage(factory -> factory.wrap(new byte[0]))))
                .subscribe(null, err -> log.debug("WS ping send failed: {}", err.getMessage()));
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
     * Closes the WS session and stops reconnect until connect() is called again.
     */
    public void disconnect() {
        reconnectEnabled.set(false);
        reconnectScheduled.set(true);
        connecting.set(false);
        connected.set(false);
        connectedAt.set(null);
        WebSocketSession session = sessionRef.getAndSet(null);
        if (session != null && session.isOpen()) {
            session.close().subscribe();
        }
        log.info("Nexus WS disconnected by reset.");
    }

    /**
     * One reconnect delay. Full jitter, or the cap when the last fail was throttled.
     */
    private void scheduleReconnect() {
        if (!reconnectEnabled.get()) {
            return;
        }
        if (!reconnectScheduled.compareAndSet(false, true)) {
            log.debug("Reconnect already scheduled, skipping duplicate.");
            return;
        }

        boolean throttled = throttleReconnect.getAndSet(false);
        int attempt = reconnectAttempt.getAndIncrement();
        int delaySeconds = WsReconnectBackoff.delaySeconds(
                attempt,
                throttled,
                bound -> ThreadLocalRandom.current().nextInt(bound));
        log.info("WS reconnect in {}s (attempt {}, throttled={})", delaySeconds, attempt, throttled);

        Mono.delay(Duration.ofSeconds(delaySeconds))
                .doOnNext(l -> log.info("Attempting WS reconnect..."))
                .doOnError(err -> {
                    log.warn("Reconnect error: {}", err.getMessage());
                    reconnectScheduled.set(false);
                })
                .onErrorComplete()
                .subscribe(
                        l -> {
                            reconnectScheduled.set(false);
                            connect(nexusBaseUrl);
                        },
                        err -> log.warn("WS reconnect subscribe error: {}", err.getMessage()));
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
                    .subscribe(null, err -> log.warn("WS send subscribe error: {}", err.getMessage()));
        } catch (Exception e) {
            log.error("Failed to serialize WS message: {}", e.getMessage());
        }
    }

    public boolean isConnected() {
        return connected.get();
    }
}