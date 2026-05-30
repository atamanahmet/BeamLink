package com.atamanahmet.beamlink.nexus.websocket;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.dto.AgentDTO;
import com.atamanahmet.beamlink.nexus.dto.LogSyncRequest;
import com.atamanahmet.beamlink.nexus.dto.PeerDTO;
import com.atamanahmet.beamlink.nexus.dto.StatusUpdatePayload;
import com.atamanahmet.beamlink.nexus.mapper.PeerMapper;
import com.atamanahmet.beamlink.nexus.repository.FileTransferRepository;
import com.atamanahmet.beamlink.nexus.service.AgentSessionService;
import com.atamanahmet.beamlink.nexus.service.NexusService;
import com.atamanahmet.beamlink.nexus.service.PeerListService;
import com.atamanahmet.beamlink.nexus.service.TransferLogService;
import com.atamanahmet.beamlink.nexus.service.TransferSenderService;
import com.atamanahmet.beamlink.nexus.websocket.enums.WsMessageType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class AgentWebSocketHandler implements WebSocketHandler {

    private final NexusConfig nexusConfig;
    private final AgentSessionService agentSessionService;
    private final PeerListService peerListService;
    private final TransferLogService transferLogService;
    private final TransferSenderService transferSenderService;
    private final FileTransferRepository fileTransferRepository;
    private final ObjectMapper objectMapper;
    private final NexusService nexusService;
    private final PeerMapper peerMapper;

    private final Map<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID agentId = extractAgentId(session);
        if (agentId == null) {
            closeQuietly(session);
            return;
        }

        sessions.put(agentId, session);
        agentSessionService.markOnline(agentId);
        log.info("Agent {} connected via WS", agentId);
        sendCurrentPeerList(session, agentId);
        resumePausedTransfers(agentId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        UUID agentId = extractAgentId(session);
        if (agentId == null) return;

        sessions.remove(agentId);
        agentSessionService.markOffline(agentId);
        log.info("Agent {} WS disconnected: {}", agentId, closeStatus);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        UUID agentId = extractAgentId(session);
        log.error("WS transport error for agent {}: {}", agentId, exception.getMessage());
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        UUID agentId = extractAgentId(session);
        if (agentId == null) return;

        try {
            WsEnvelope<JsonNode> envelope = objectMapper.readValue(
                    message.getPayload().toString(),
                    objectMapper.getTypeFactory().constructParametricType(WsEnvelope.class, JsonNode.class)
            );

            WsMessageType type = envelope.getType();

            switch (type) {
                case HEARTBEAT -> handleHeartbeat(session, agentId, envelope.getPayload());
                case STATUS_UPDATE -> handleStatusUpdate(session, agentId, envelope.getPayload());
                case LOG_SYNC -> handleLogSync(agentId, envelope.getPayload());
                default -> log.warn("Unhandled WS type '{}' from agent {}", type, agentId);
            }
        } catch (Exception e) {
            log.error("Failed to handle WS message from agent {}: {}", agentId, e.getMessage());
        }
    }

    private void handleHeartbeat(WebSocketSession session, UUID agentId, JsonNode payload) {
        agentSessionService.touchLastSeen(agentId);

        long peerVersion = payload != null && payload.has("peerVersion")
                ? payload.get("peerVersion").asLong(-1L)
                : -1L;

        if (peerVersion < peerListService.getCurrentVersion()) {
            sendCurrentPeerList(session, agentId);
        }

        log.debug("Heartbeat from agent {}", agentId);
    }

    private void handleStatusUpdate(WebSocketSession session, UUID agentId, JsonNode payload) {
        try {
            StatusUpdatePayload update = objectMapper.treeToValue(payload, StatusUpdatePayload.class);
            boolean addressChanged = agentSessionService.applyStatusUpdate(agentId, update);

            if (addressChanged) {
                peerListService.incrementVersion();
                log.info("Agent {} address changed, broadcasting peer list update", agentId);
                broadcastPeerListUpdate(agentId);
            }

            if (update.getPeerVersion() < peerListService.getCurrentVersion()) {
                sendCurrentPeerList(session, agentId);
            }
        } catch (Exception e) {
            log.error("Failed to handle status_update from agent {}: {}", agentId, e.getMessage());
        }
    }

    private void handleLogSync(UUID agentId, JsonNode payload) {
        try {
            List<LogSyncRequest> logs = objectMapper.treeToValue(
                    payload,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, LogSyncRequest.class)
            );

            if (logs == null || logs.isEmpty()) return;

            List<UUID> syncedIds = transferLogService.sync(agentId, logs);
            log.info("Log sync from agent {}: {}/{} logs accepted", agentId, syncedIds.size(), logs.size());
        } catch (Exception e) {
            log.error("Failed to handle log_sync from agent {}: {}", agentId, e.getMessage());
        }
    }

    private void sendCurrentPeerList(WebSocketSession session, UUID excludeAgentId) {
        try {
            List<PeerDTO> peers = buildPeerList(excludeAgentId);
            WsEnvelope<List<PeerDTO>> envelope = WsEnvelope.of(
                    WsMessageType.PEER_LIST_UPDATE,
                    peers,
                    peerListService.getCurrentVersion()
            );
            send(session, envelope);
            log.debug("Sent peer list ({} peers) to agent {}", peers.size(), excludeAgentId);
        } catch (Exception e) {
            log.error("Failed to send peer list to agent {}: {}", excludeAgentId, e.getMessage());
        }
    }

    public void broadcastPeerListUpdate(UUID excludeAgentId) {
        List<UUID> targets = connectedAgentsExcept(excludeAgentId);
        log.info("Broadcasting PEER_LIST_UPDATE to {} agent(s)", targets.size());

        for (UUID agentId : targets) {
            WebSocketSession session = sessions.get(agentId);
            if (isOpen(session)) {
                sendCurrentPeerList(session, agentId);
            }
        }
    }

    public void broadcastAgentJoined(AgentDTO newAgent) {
        WsEnvelope<AgentDTO> envelope = WsEnvelope.of(WsMessageType.AGENT_JOINED, newAgent);
        log.info("Broadcasting AGENT_JOINED for agent {}", newAgent.getId());

        for (Map.Entry<UUID, WebSocketSession> entry : sessions.entrySet()) {
            if (!entry.getKey().equals(newAgent.getId()) && isOpen(entry.getValue())) {
                send(entry.getValue(), envelope);
            }
        }
    }

    public void broadcastAgentOffline(UUID offlineAgentId) {
        Map<String, UUID> payload = Map.of("agentId", offlineAgentId);
        WsEnvelope<Map<String, UUID>> envelope = WsEnvelope.of(WsMessageType.AGENT_OFFLINE, payload);
        log.info("Broadcasting AGENT_OFFLINE for agent {}", offlineAgentId);

        for (Map.Entry<UUID, WebSocketSession> entry : sessions.entrySet()) {
            if (!entry.getKey().equals(offlineAgentId) && isOpen(entry.getValue())) {
                send(entry.getValue(), envelope);
            }
        }
    }

    public void sendMessage(UUID agentId, Object message) {
        WebSocketSession session = sessions.get(agentId);
        if (isOpen(session)) {
            send(session, message);
        } else {
            log.debug("No open WS session for agent {}, message not sent", agentId);
        }
    }

    public boolean isConnected(UUID agentId) {
        return isOpen(sessions.get(agentId));
    }

    public WebSocketSession getSession(UUID agentId) {
        return sessions.get(agentId);
    }

    public void handleZombieCleanup(UUID agentId) {
        sessions.remove(agentId);
        agentSessionService.markOffline(agentId);
        broadcastAgentOffline(agentId);
        log.warn("Zombie cleanup applied for agent {}", agentId);
    }

    private void resumePausedTransfers(UUID agentId) {
        for (FileTransfer t : fileTransferRepository.findByTargetAgentIdAndStatus(agentId, TransferStatus.PAUSED)) {
            try {
                transferSenderService.resume(t.getTransferId());
                log.info("Resumed paused transfer {} for agent {}", t.getTransferId(), agentId);
            } catch (Exception e) {
                log.warn("Auto-resume failed for transfer {}: {}", t.getTransferId(), e.getMessage());
            }
        }
    }

    private List<PeerDTO> buildPeerList(UUID excludeAgentId) {
        List<PeerDTO> peers = new ArrayList<>();
        peers.add(peerMapper.nexusPeer());
        agentSessionService.findApproved().stream()
                .filter(a -> !a.getId().equals(excludeAgentId))
                .map(peerMapper::fromAgent)
                .forEach(peers::add);
        return peers;
    }

    private List<UUID> connectedAgentsExcept(UUID excludeId) {
        return sessions.keySet().stream()
                .filter(id -> !id.equals(excludeId))
                .toList();
    }

    private void send(WebSocketSession session, Object payload) {
        if (!isOpen(session)) return;
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (IOException e) {
            log.error("WS send failed for session {}: {}", session.getId(), e.getMessage());
        }
    }

    private boolean isOpen(WebSocketSession session) {
        return session != null && session.isOpen();
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException ignored) {
        }
    }

    private UUID extractAgentId(WebSocketSession session) {
        Object attr = session.getAttributes().get("agentId");
        return attr instanceof UUID id ? id : null;
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}