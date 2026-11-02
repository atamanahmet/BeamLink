package com.atamanahmet.beamlink.nexus.websocket;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.exception.ErrorLog;
import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.dto.LogSyncRequest;
import com.atamanahmet.beamlink.nexus.dto.PeerDTO;
import com.atamanahmet.beamlink.nexus.dto.StatusUpdatePayload;
import com.atamanahmet.beamlink.nexus.mapper.PeerMapper;
import com.atamanahmet.beamlink.nexus.service.DeviceSessionService;
import com.atamanahmet.beamlink.nexus.service.FileTransferService;
import com.atamanahmet.beamlink.nexus.service.NexusService;
import com.atamanahmet.beamlink.nexus.service.PeerListService;
import com.atamanahmet.beamlink.nexus.service.TransferLogService;
import com.atamanahmet.beamlink.nexus.service.TransferSenderService;
import com.atamanahmet.beamlink.nexus.websocket.enums.WsMessageType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceWebSocketHandler implements WebSocketHandler {

    private final NexusConfig nexusConfig;
    private final DeviceSessionService deviceSessionService;
    private final PeerListService peerListService;
    private final TransferLogService transferLogService;
    private final TransferSenderService transferSenderService;
    private final FileTransferService fileTransferService;
    private final ObjectMapper objectMapper;
    private final NexusService nexusService;
    private final PeerMapper peerMapper;

    private final Map<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID deviceId = extractDeviceId(session);
        if (deviceId == null) {
            closeQuietly(session);
            return;
        }

        WebSocketSession previous = sessions.put(deviceId, session);
        if (previous != null && previous != session) {
            closeQuietly(previous, CloseStatus.POLICY_VIOLATION.withReason("replaced"));
        }
        deviceSessionService.markOnline(deviceId);
        log.info("Device {} connected via WS", deviceId);
        sendCurrentPeerList(session, deviceId);
        resumePausedTransfers(deviceId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        UUID deviceId = extractDeviceId(session);
        if (deviceId == null) return;

        WebSocketSession registered = sessions.get(deviceId);
        if (!WsSessionIdentity.isRegisteredSession(registered, session)) {
            log.debug("Ignoring stale WS close for device {}", deviceId);
            return;
        }
        if (!sessions.remove(deviceId, session)) {
            return;
        }
        deviceSessionService.markOffline(deviceId);
        broadcastDeviceOffline(deviceId);
        log.info("Device {} WS disconnected: {}", deviceId, closeStatus);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        UUID deviceId = extractDeviceId(session);
        log.error("WS transport error for device {}: {}", deviceId, ErrorLog.formatChain(exception));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        UUID deviceId = extractDeviceId(session);
        if (deviceId == null) return;

        try {
            WsEnvelope<JsonNode> envelope = objectMapper.readValue(
                    message.getPayload().toString(),
                    objectMapper.getTypeFactory().constructParametricType(WsEnvelope.class, JsonNode.class)
            );

            WsMessageType type = envelope.getType();

            switch (type) {
                case HEARTBEAT -> handleHeartbeat(session, deviceId, envelope.getPayload());
                case STATUS_UPDATE -> handleStatusUpdate(session, deviceId, envelope.getPayload());
                case LOG_SYNC -> handleLogSync(deviceId, envelope.getPayload());
                default -> log.warn("Unhandled WS type '{}' from device {}", type, deviceId);
            }
        } catch (Exception e) {
            log.error("Failed to handle WS message from device {}: {}", deviceId, e.getMessage());
        }
    }

    private void handleHeartbeat(WebSocketSession session, UUID deviceId, JsonNode payload) {
        deviceSessionService.touchLastSeen(deviceId);

        long peerVersion = payload != null && payload.has("peerVersion")
                ? payload.get("peerVersion").asLong(-1L)
                : -1L;

        if (peerVersion < peerListService.getCurrentVersion()) {
            sendCurrentPeerList(session, deviceId);
        }

        log.debug("Heartbeat from device {}", deviceId);
    }

    private void handleStatusUpdate(WebSocketSession session, UUID deviceId, JsonNode payload) {
        try {
            StatusUpdatePayload update = objectMapper.treeToValue(payload, StatusUpdatePayload.class);
            boolean addressChanged = deviceSessionService.applyStatusUpdate(deviceId, update);

            if (addressChanged) {
                peerListService.incrementVersion();
                log.info("Device {} address changed, broadcasting peer list update", deviceId);
                broadcastPeerListUpdate(deviceId);
            }

            if (update.getPeerVersion() < peerListService.getCurrentVersion()) {
                sendCurrentPeerList(session, deviceId);
            }
        } catch (Exception e) {
            log.error("Failed to handle status_update from device {}: {}", deviceId, e.getMessage());
        }
    }

    private void handleLogSync(UUID deviceId, JsonNode payload) {
        try {
            List<LogSyncRequest> logs = objectMapper.treeToValue(
                    payload,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, LogSyncRequest.class)
            );

            if (logs == null || logs.isEmpty()) return;

            List<UUID> syncedIds = transferLogService.sync(deviceId, logs);
            log.info("Log sync from device {}: {}/{} logs accepted", deviceId, syncedIds.size(), logs.size());
        } catch (Exception e) {
            log.error("Failed to handle log_sync from device {}: {}", deviceId, e.getMessage());
        }
    }

    private void sendCurrentPeerList(WebSocketSession session, UUID excludeDeviceId) {
        try {
            List<PeerDTO> peers = buildPeerList(excludeDeviceId);
            WsEnvelope<List<PeerDTO>> envelope = WsEnvelope.of(
                    WsMessageType.PEER_LIST_UPDATE,
                    peers,
                    peerListService.getCurrentVersion()
            );
            send(session, envelope);
            log.debug("Sent peer list ({} peers) to device {}", peers.size(), excludeDeviceId);
        } catch (Exception e) {
            log.error("Failed to send peer list to device {}: {}", excludeDeviceId, e.getMessage());
        }
    }

    public void broadcastPeerListUpdate(UUID excludeDeviceId) {
        List<UUID> targets = connectedDevicesExcept(excludeDeviceId);
        log.info("Broadcasting PEER_LIST_UPDATE to {} device(s)", targets.size());

        for (UUID deviceId : targets) {
            WebSocketSession session = sessions.get(deviceId);
            if (isOpen(session)) {
                sendCurrentPeerList(session, deviceId);
            }
        }
    }

    /**
     * Tells other devices a peer joined. Payload is PeerDTO only.
     */
    public void broadcastDeviceJoined(PeerDTO newPeer) {
        WsEnvelope<PeerDTO> envelope = WsEnvelope.of(WsMessageType.DEVICE_JOINED, newPeer);
        log.info("Broadcasting DEVICE_JOINED for device {}", newPeer.getId());

        for (Map.Entry<UUID, WebSocketSession> entry : sessions.entrySet()) {
            if (!entry.getKey().equals(newPeer.getId()) && isOpen(entry.getValue())) {
                send(entry.getValue(), envelope);
            }
        }
    }

    public void broadcastDeviceOffline(UUID offlineDeviceId) {
        Map<String, UUID> payload = Map.of("deviceId", offlineDeviceId);
        WsEnvelope<Map<String, UUID>> envelope = WsEnvelope.of(WsMessageType.DEVICE_OFFLINE, payload);
        log.info("Broadcasting DEVICE_OFFLINE for device {}", offlineDeviceId);

        for (Map.Entry<UUID, WebSocketSession> entry : sessions.entrySet()) {
            if (!entry.getKey().equals(offlineDeviceId) && isOpen(entry.getValue())) {
                send(entry.getValue(), envelope);
            }
        }
    }

    public void sendMessage(UUID deviceId, Object message) {
        WebSocketSession session = sessions.get(deviceId);
        if (isOpen(session)) {
            send(session, message);
        } else {
            log.debug("No open WS session for device {}, message not sent", deviceId);
        }
    }

    public boolean isConnected(UUID deviceId) {
        return isOpen(sessions.get(deviceId));
    }

    public WebSocketSession getSession(UUID deviceId) {
        return sessions.get(deviceId);
    }

    /**
     * Drops this session if it is still registered, then marks the device offline.
     */
    public void handleZombieCleanup(UUID deviceId, WebSocketSession session) {
        if (!sessions.remove(deviceId, session)) {
            return;
        }
        deviceSessionService.markOffline(deviceId);
        broadcastDeviceOffline(deviceId);
        log.warn("Zombie cleanup applied for device {}", deviceId);
    }

    private void resumePausedTransfers(UUID deviceId) {
        for (FileTransfer t : fileTransferService.findByTargetDeviceIdAndStatus(deviceId, TransferStatus.PAUSED)) {
            try {
                transferSenderService.resume(t.getTransferId());
                log.info("Resumed paused transfer {} for device {}", t.getTransferId(), deviceId);
            } catch (Exception e) {
                log.warn("Auto-resume failed for transfer {}: {}", t.getTransferId(), e.getMessage());
            }
        }
    }

    private List<PeerDTO> buildPeerList(UUID excludeDeviceId) {
        List<PeerDTO> peers = new ArrayList<>();
        peers.add(peerMapper.nexusPeer());
        deviceSessionService.findApproved().stream()
                .filter(a -> !a.getId().equals(excludeDeviceId))
                .map(peerMapper::fromDevice)
                .forEach(peers::add);
        return peers;
    }

    private List<UUID> connectedDevicesExcept(UUID excludeId) {
        return sessions.keySet().stream()
                .filter(id -> !id.equals(excludeId))
                .toList();
    }

    private void send(WebSocketSession session, Object payload) {
        if (!isOpen(session)) return;
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (IOException | JacksonException e) {
            log.error("WS send failed for session {}: {}", session.getId(), e.getMessage());
        }
    }

    private boolean isOpen(WebSocketSession session) {
        return session != null && session.isOpen();
    }

    /**
     * Closes one device session if it is still open.
     */
    public void closeSession(UUID deviceId) {
        WebSocketSession session = sessions.get(deviceId);
        if (session == null) {
            return;
        }
        sessions.remove(deviceId, session);
        closeQuietly(session, CloseStatus.GOING_AWAY);
    }

    /**
     * Closes every device WS session.
     */
    public void closeAllSessions() {
        for (Map.Entry<UUID, WebSocketSession> entry : List.copyOf(sessions.entrySet())) {
            if (sessions.remove(entry.getKey(), entry.getValue())) {
                closeQuietly(entry.getValue(), CloseStatus.GOING_AWAY);
            }
        }
        log.info("All device WS sessions closed.");
    }

    private void closeQuietly(WebSocketSession session) {
        closeQuietly(session, CloseStatus.POLICY_VIOLATION);
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close(status);
        } catch (IOException ignored) {
        }
    }

    private UUID extractDeviceId(WebSocketSession session) {
        Object attr = session.getAttributes().get("deviceId");
        return attr instanceof UUID id ? id : null;
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}