package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.dto.StatusUpdatePayload;
import com.atamanahmet.beamlink.nexus.exception.AgentNotFoundException;
import com.atamanahmet.beamlink.nexus.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentSessionService {

    private static final Duration ONLINE_THRESHOLD = Duration.ofMinutes(2L);

    private final AgentRepository agentRepository;

    public Agent findById(UUID agentId) {
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new AgentNotFoundException("Unknown agent: " + agentId));
    }

    public Agent save(Agent agent) {
        return agentRepository.save(agent);
    }

    public List<Agent> findApproved() {
        return agentRepository.findByState(AgentState.APPROVED);
    }

    @Transactional
    public void markOnline(UUID agentId) {
        agentRepository.findById(agentId).ifPresent(agent -> {
            agent.setLastSeenAt(Instant.now());
            agentRepository.save(agent);
        });
    }

    @Transactional
    public void markOffline(UUID agentId) {
        agentRepository.findById(agentId).ifPresent(agent -> {
            agent.setLastSeenAt(Instant.EPOCH);
            agentRepository.save(agent);
        });
    }

    @Transactional
    public void touchLastSeen(UUID agentId) {
        agentRepository.findById(agentId).ifPresent(agent -> {
            agent.setLastSeenAt(Instant.now());
            agentRepository.save(agent);
        });
    }

    @Transactional
    public boolean applyStatusUpdate(UUID agentId, StatusUpdatePayload payload) {
        Agent agent = findById(agentId);

        boolean addressChanged = payload.getIpAddress() != null
                && payload.getPort() > 0
                && (!payload.getIpAddress().equals(agent.getIpAddress())
                || payload.getPort() != agent.getPort());

        if (addressChanged) {
            agent.setIpAddress(payload.getIpAddress());
            agent.setPort(payload.getPort());
        }

        agent.setLastSeenAt(Instant.now());
        agentRepository.save(agent);
        return addressChanged;
    }

    /**
     * Check if agent was seen recently
     */
    public boolean isOnline(Agent agent) {
        Instant lastSeen = agent.getLastSeenAt();
        return lastSeen != null && lastSeen.isAfter(Instant.now().minus(ONLINE_THRESHOLD));
    }
}