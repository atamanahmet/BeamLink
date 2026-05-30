package com.atamanahmet.beamlink.nexus.websocket;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Detects agents that dropped without a clean WS close (power loss, network cut, sleep).
 * Heartbeat interval: 15s (agent side)
 * Zombie threshold:   35s - 2 missed heartbeats + buffer
 * This check runs:    every 20s
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentHeartbeatMonitor {

    private static final int ZOMBIE_THRESHOLD_SECONDS = 35;

    private final AgentWebSocketHandler webSocketHandler;
    private final AgentRepository agentRepository;

    /**
     * Session appears open but agent hasn't heartbeat within threshold.
     * Force-close it, afterConnectionClosed will handle the offline transition.
     */
    @Scheduled(fixedDelay = 20_000)
    public void detectZombieSessions() {
        Instant threshold = Instant.now().minus(ZOMBIE_THRESHOLD_SECONDS, ChronoUnit.SECONDS);

        List<Agent> stale = agentRepository.findByStateAndLastSeenAtBefore(
                AgentState.APPROVED, threshold);

        for (Agent agent : stale) {
            UUID agentId = agent.getId();

            if (!webSocketHandler.isConnected(agentId)) continue;


            log.warn("Zombie WS session detected for agent {}, last seen at {}. Force-closing.",
                    agentId, agent.getLastSeenAt());

            forceClose(agentId);
        }
    }
    /**
     * Closing the session triggers afterConnectionClosed in AgentWebSocketHandler,
     * which calls markOffline and broadcastAgentOffline
     */
    private void forceClose(UUID agentId) {

        WebSocketSession session = webSocketHandler.getSession(agentId);
        if (session == null) return;

        try {
            session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (IOException e) {
            log.warn("Failed to force-close zombie session for agent {}: {}", agentId, e.getMessage());

            webSocketHandler.handleZombieCleanup(agentId);
        }
    }
}