package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.dto.ApprovalPushRequest;
import com.atamanahmet.beamlink.agent.dto.HeartbeatPayload;
import com.atamanahmet.beamlink.agent.dto.PeerDTO;
import com.atamanahmet.beamlink.agent.dto.StatusUpdatePayload;
import com.atamanahmet.beamlink.agent.dto.TransferLogDTO;
import com.atamanahmet.beamlink.agent.event.WsConnectionEvent;
import com.atamanahmet.beamlink.agent.event.WsMessageEvent;
import com.atamanahmet.beamlink.agent.websocket.WsEnvelope;
import com.atamanahmet.beamlink.agent.websocket.enums.WsMessageType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexusStatusWebSocketService {

    private final ObjectMapper objectMapper;
    private final AgentService agentService;
    private final TransferLogService transferLogService;
    private final PeerCacheService peerCacheService;
    private final NexusConnectionStateService connectionState;
    private final NexusWebSocketService nexusWebSocketService;

    @EventListener
    public void onWsConnectionEvent(WsConnectionEvent event) {
        if (event.getType() == WsConnectionEvent.Type.CONNECTED) {
            connectionState.reportOnline();
        } else {
            connectionState.reportOffline();
        }
    }

    @EventListener
    public void onWsMessage(WsMessageEvent event) {
        handleMessage(event.getMessage());
    }

    @Scheduled(fixedRate = 15_000)
    public void sendHeartbeat() {
        if (!agentService.isApproved() || !nexusWebSocketService.isConnected()) return;

        WsEnvelope<HeartbeatPayload> envelope = WsEnvelope.of(
                WsMessageType.HEARTBEAT,
                new HeartbeatPayload(peerCacheService.getCurrentPeerListVersion())
        );
        nexusWebSocketService.send(envelope);
        log.debug("Heartbeat sent, peerVersion={}", peerCacheService.getCurrentPeerListVersion());
    }

    @Scheduled(fixedRate = 30_000)
    public void sendStatusUpdate() {
        if (!agentService.isApproved() || !nexusWebSocketService.isConnected()) return;

        StatusUpdatePayload payload = StatusUpdatePayload.builder()
                .ipAddress(agentService.getAgent().getIpAddress())
                .port(agentService.getAgent().getPort())
                .peerVersion(peerCacheService.getCurrentPeerListVersion())
                .build();

        nexusWebSocketService.send(WsEnvelope.of(WsMessageType.STATUS_UPDATE, payload));
        log.debug("Status update sent: {}:{}", payload.ipAddress(), payload.port());
    }

    @Scheduled(fixedRate = 60_000)
    public void syncLogs() {
        if (!agentService.isApproved() || !nexusWebSocketService.isConnected()) return;

        List<TransferLogDTO> unsyncedLogs = transferLogService.getUnsyncedLogs();
        if (unsyncedLogs.isEmpty()) return;

        nexusWebSocketService.send(WsEnvelope.of(WsMessageType.LOG_SYNC, unsyncedLogs));

        List<UUID> ids = unsyncedLogs.stream().map(TransferLogDTO::getId).toList();
        transferLogService.markAsSynced(ids);
        log.debug("Sent {} unsynced logs over WS", unsyncedLogs.size());
    }

    /**
     * Routes incoming WS message to correct handler by type.
     */
    private void handleMessage(WsEnvelope<JsonNode> envelope) {
        if (envelope == null || envelope.getType() == null) return;

        WsMessageType type;
        try {
            type = envelope.getType();
        } catch (IllegalArgumentException e) {
            log.warn("Unknown WS message type: {}", envelope.getType());
            return;
        }

        switch (type) {
            case APPROVAL_PUSH    -> handleApprovalPush(envelope);
            case PEER_LIST_UPDATE -> handlePeerListUpdate(envelope);
            case AGENT_JOINED     -> handleAgentJoined(envelope);
            case AGENT_OFFLINE    -> handleAgentOffline(envelope);
            case RENAME_PUSH      -> handleRenamePush(envelope);
            default               -> log.warn("Unhandled WS message type: {}", type);
        }
    }

    private void handleApprovalPush(WsEnvelope<JsonNode> envelope) {
        try {
            ApprovalPushRequest request = objectMapper.treeToValue(envelope.getPayload(), ApprovalPushRequest.class);
            agentService.applyNexusIdentity(request);
            log.info("Approval applied via WS. Name={}, Id={}", agentService.getAgentName(), agentService.getAgentId());
        } catch (Exception e) {
            log.error("Failed to apply approval push: {}", e.getMessage(), e);
        }
    }

    private void handlePeerListUpdate(WsEnvelope<JsonNode> envelope) {
        try {
            List<PeerDTO> peers = objectMapper.convertValue(
                    envelope.getPayload(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, PeerDTO.class)
            );
            long version = envelope.getVersion() != null ? envelope.getVersion() : 0L;
            peerCacheService.updatePeers(peers, version);
            log.debug("Peer list update applied: {} peers (version: {})", peers.size(), version);
        } catch (Exception e) {
            log.error("Failed to handle peer list update: {}", e.getMessage(), e);
        }
    }

    private void handleAgentJoined(WsEnvelope<JsonNode> envelope) {
        try {
            PeerDTO peer = objectMapper.treeToValue(envelope.getPayload(), PeerDTO.class);
            peerCacheService.addOrUpdatePeer(peer);
            log.info("Agent joined: {} ({})", peer.getAgentName(), peer.getAgentId());
        } catch (Exception e) {
            log.error("Failed to handle agent joined: {}", e.getMessage(), e);
        }
    }

    private void handleAgentOffline(WsEnvelope<JsonNode> envelope) {
        try {
            UUID agentId = UUID.fromString(envelope.getPayload().get("agentId").asText());
            peerCacheService.markOffline(agentId);
            log.info("Agent offline: {}", agentId);
        } catch (Exception e) {
            log.error("Failed to handle agent offline: {}", e.getMessage(), e);
        }
    }

    private void handleRenamePush(WsEnvelope<JsonNode> envelope) {
        try {
            String newName = envelope.getPayload().get("agentName").asText();
            if (newName != null && !newName.isBlank()) {
                agentService.updateAgentName(newName);
                log.info("Agent renamed via WS: {}", newName);
            }
        } catch (Exception e) {
            log.error("Failed to handle rename push: {}", e.getMessage(), e);
        }
    }
}