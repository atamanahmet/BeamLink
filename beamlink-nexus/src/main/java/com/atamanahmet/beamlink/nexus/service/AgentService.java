package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.dto.*;
import com.atamanahmet.beamlink.nexus.event.AgentApprovedEvent;
import com.atamanahmet.beamlink.nexus.event.AgentRenamedEvent;
import com.atamanahmet.beamlink.nexus.exception.AgentNotFoundException;
import com.atamanahmet.beamlink.nexus.exception.NameAlreadyInUseException;
import com.atamanahmet.beamlink.nexus.mapper.AgentMapper;
import com.atamanahmet.beamlink.nexus.mapper.PeerMapper;
import com.atamanahmet.beamlink.nexus.repository.AgentRepository;
import com.atamanahmet.beamlink.nexus.security.AgentTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private static final int OFFLINE_THRESHOLD_MINUTES = 2;

    private final AgentRepository agentRepository;
    private final PeerListService peerListService;
    private final AgentTokenService agentTokenService;
    private final AgentMapper agentMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AgentSessionService agentSessionService;
    private final PeerMapper peerMapper;

    @Transactional
    public AgentRegistrationResponse register(AgentRegistrationRequest request) {
        Optional<Agent> existing = agentRepository.findByIpAddressAndPort(request.getIpAddress(), request.getPort());
        if (existing.isPresent()) {
            Agent agent = existing.get();
            log.info("Agent already registered: {}. Returning existing record.", agent.getId());
            return new AgentRegistrationResponse(agent.getId(), agent.getState());
        }

        String name = request.getAgentName() != null && !request.getAgentName().isBlank()
                ? request.getAgentName()
                : request.getIpAddress() + ":" + request.getPort();

        Agent agent = Agent.builder()
                .name(name)
                .ipAddress(request.getIpAddress())
                .port(request.getPort())
                .state(AgentState.PENDING_APPROVAL)
                .registeredAt(Instant.now())
                .lastSeenAt(Instant.now())
                .build();

        try {
            Agent saved = agentRepository.saveAndFlush(agent);
            log.info("New agent registered: {} awaiting admin approval.", saved.getId());
            return new AgentRegistrationResponse(saved.getId(), saved.getState());
        } catch (DataIntegrityViolationException e) {
            log.warn("Registration issue for {}:{}, resolving", request.getIpAddress(), request.getPort());
            return resolveOldInfo(request.getIpAddress(), request.getPort());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected AgentRegistrationResponse resolveOldInfo(String ipAddress, int port) {
        Agent agent = agentRepository.findByIpAddressAndPort(ipAddress, port)
                .orElseThrow(() -> new IllegalStateException(
                        "Agent not found after constraint violation for " + ipAddress + ":" + port));
        log.info("Issue resolved: returning existing agent {}.", agent.getId());
        return new AgentRegistrationResponse(agent.getId(), agent.getState());
    }

    public Agent saveAgent(Agent agent) {
        return agentRepository.save(agent);
    }

    public Optional<Agent> getByIpAddressAndPort(String ipAddress, int port) {
        return agentRepository.findByIpAddressAndPort(ipAddress, port);
    }

    @Transactional
    public AgentStatusResponse updateAgentStatus(AgentStatusRequest request) {
        StatusUpdatePayload payload = StatusUpdatePayload.builder()
                .ipAddress(request.getIpAddress())
                .port(request.getPort())
                .peerVersion(request.getPeerVersion())
                .build();

        boolean addressChanged = agentSessionService.applyStatusUpdate(request.getAgentId(), payload);
        if (addressChanged) {
            peerListService.incrementVersion();
            log.info("Agent {} address changed via HTTP fallback, peer list version incremented.", request.getAgentId());
        }

        Agent agent = agentSessionService.findById(request.getAgentId());
        boolean peerOutdated = request.getPeerVersion() < peerListService.getCurrentVersion();

        List<PeerDTO> peers = null;
        if (peerOutdated && agent.getState() == AgentState.APPROVED) {
            peers = agentSessionService.findApproved().stream()
                    .map(peerMapper::fromAgent)
                    .collect(Collectors.toList());
            peers.add(0, peerMapper.nexusPeer());
        }

        return AgentStatusResponse.builder()
                .status(agent.getState().name())
                .peerOutdated(peerOutdated)
                .peers(peers)
                .build();
    }

    @Transactional
    public void deleteAgent(UUID agentId) {
        Agent agent = findByAgentId(agentId);
        agentRepository.delete(agent);
        peerListService.incrementVersion();
        log.info("Agent deleted: {}", agentId);
    }

    public Agent findByAgentId(UUID agentId) {
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new AgentNotFoundException("Unknown agent: " + agentId));
    }

    public List<Agent> getAllAgents() {
        return agentRepository.findAll();
    }

    public List<Agent> getAllApproved() {
        return getAgentsByState(AgentState.APPROVED);
    }

    public List<Agent> getAllPending() {
        return getAgentsByState(AgentState.PENDING_APPROVAL);
    }

    public List<Agent> getAgentsByState(AgentState state) {
        return agentRepository.findByState(state);
    }

    public List<Agent> getOnlineAgentsBefore(Instant threshold) {
        return agentRepository.findByLastSeenAtBefore(threshold);
    }

    public List<Agent> getOnlineAgents() {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(OFFLINE_THRESHOLD_MINUTES));
        return agentRepository.findByLastSeenAtAfter(threshold);
    }

    public AgentStats getAgentStats() {
        long total = agentRepository.count();
        Instant threshold = Instant.now().minus(Duration.ofMinutes(OFFLINE_THRESHOLD_MINUTES));
        long online = agentRepository.countByLastSeenAtAfter(threshold);
        long pending = agentRepository.countByState(AgentState.PENDING_APPROVAL);
        long pendingRename = agentRepository.countByStateAndRequestedNameIsNotNull(AgentState.APPROVED);
        long offline = total - online;
        return new AgentStats(total, online, offline, pending, pendingRename);
    }

    public List<Agent> getPendingRenames() {
        return agentRepository.findByStateAndRequestedNameIsNotNull(AgentState.APPROVED);
    }

    @Transactional
    public void approveAgent(UUID agentId) {
        Agent agent = findByAgentId(agentId);
        if (agent.getState() != AgentState.PENDING_APPROVAL) {
            throw new IllegalStateException("Agent is not pending approval");
        }

        agent.setState(AgentState.APPROVED);
        agent.setPublicId(UUID.randomUUID());
        agent.setApprovedAt(Instant.now());

        Agent saved = agentRepository.saveAndFlush(agent);
        String authToken = agentTokenService.generateAuthToken(saved.getId());
        String publicToken = agentTokenService.generatePublicToken(saved.getId(), saved.getPublicId());

        peerListService.incrementVersion();
        eventPublisher.publishEvent(new AgentApprovedEvent(agentMapper.toDTO(saved), authToken, publicToken));
        log.info("Agent approved: {}", saved.getId());
    }

    @Transactional
    public void rejectAgent(UUID agentId) {
        Agent agent = findByAgentId(agentId);
        if (agent.getState() != AgentState.PENDING_APPROVAL) {
            throw new IllegalStateException("Agent is not pending approval");
        }

        agentRepository.delete(agent);
        peerListService.incrementVersion();
        log.info("Agent rejected and removed: {}", agentId);
    }

    @Transactional
    public void requestRename(UUID agentId, String newName) {
        Agent agent = findByAgentId(agentId);
        if (agent.getState() != AgentState.APPROVED) {
            throw new IllegalStateException("Only approved agents can request rename");
        }

        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Invalid name");
        }

        if (agentRepository.existsByName(newName)) {
            throw new NameAlreadyInUseException("Name already in use");
        }

        agent.setRequestedName(newName);
        agent.setRenameRequestedAt(Instant.now());
        agentRepository.save(agent);
        log.info("Rename requested: {} -> {}", agent.getName(), newName);
    }

    @Transactional
    public void approveRename(UUID agentId) {
        Agent agent = findByAgentId(agentId);
        if (agent.getRequestedName() == null) {
            throw new IllegalStateException("No pending rename request");
        }

        if (agentRepository.existsByName(agent.getRequestedName())) {
            throw new NameAlreadyInUseException("Name already in use");
        }

        agent.setName(agent.getRequestedName());
        agent.setRequestedName(null);
        agent.setRenameRequestedAt(null);

        AgentDTO agentDTO = agentMapper.toDTO(agentRepository.save(agent));
        peerListService.incrementVersion();
        eventPublisher.publishEvent(new AgentRenamedEvent(agentDTO));
        log.info("Rename approved for agent {}", agentId);
    }

    @Transactional
    public void rejectRename(UUID agentId) {
        Agent agent = findByAgentId(agentId);
        if (agent.getRequestedName() == null) {
            throw new IllegalStateException("No pending rename request");
        }

        agent.setRequestedName(null);
        agent.setRenameRequestedAt(null);
        agentRepository.save(agent);
        log.info("Rename rejected for agent {}", agentId);
    }

    public List<AgentDTO> getOnlineAgentsExcluding(UUID excludeId) {
        return getOnlineAgents().stream()
                .filter(agent -> !agent.getId().equals(excludeId))
                .map(agentMapper::toDTO)
                .collect(Collectors.toList());
    }

    public List<AgentDTO> getAllAgentsExcluding(UUID excludeId) {
        return getAllAgents().stream()
                .filter(agent -> !agent.getId().equals(excludeId))
                .map(agentMapper::toDTO)
                .collect(Collectors.toList());
    }
}