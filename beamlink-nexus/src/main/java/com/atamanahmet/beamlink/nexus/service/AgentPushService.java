package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.config.RsaKeyConfig;
import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.dto.AgentDTO;
import com.atamanahmet.beamlink.nexus.dto.AgentRenameResponse;
import com.atamanahmet.beamlink.nexus.dto.ApprovalPushRequest;
import com.atamanahmet.beamlink.nexus.event.AgentApprovedEvent;
import com.atamanahmet.beamlink.nexus.event.AgentRenamedEvent;
import com.atamanahmet.beamlink.nexus.http.HttpSender;
import com.atamanahmet.beamlink.nexus.mapper.AgentMapper;
import com.atamanahmet.beamlink.nexus.repository.AgentRepository;
import com.atamanahmet.beamlink.nexus.security.AgentTokenService;
import com.atamanahmet.beamlink.nexus.websocket.AgentWebSocketHandler;
import com.atamanahmet.beamlink.nexus.websocket.WsEnvelope;
import com.atamanahmet.beamlink.nexus.websocket.enums.WsMessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPushService {

    private final AgentRepository agentRepository;
    private final AgentWebSocketHandler webSocketHandler;
    private final AgentTokenService agentTokenService;
    private final AgentMapper agentMapper;
    private final HttpSender httpSender;
    private final ObjectMapper objectMapper;
    private final RsaKeyConfig rsaKeyConfig;

    @Transactional
    @Scheduled(fixedDelay = 30000L)
    public void pushPendingApprovals() {
        List<Agent> waitingApprovals = agentRepository.findByStateAndApprovalPushedFalse(AgentState.APPROVED);
        if (waitingApprovals.isEmpty()) return;

        log.info("Retrying approval push to {} agent(s)", waitingApprovals.size());

        for (Agent agent : waitingApprovals) {
            if (agent.getPublicId() == null) {
                log.warn("Skipping approval push for agent {}, publicId is null", agent.getId());
                continue;
            }
            String authToken = agentTokenService.generateAuthToken(agent.getId());
            String publicToken = agentTokenService.generatePublicToken(agent.getId(), agent.getPublicId());
            pushApproval(agentMapper.toDTO(agent), authToken, publicToken);
        }
    }

    public void pushApproval(AgentDTO agentDTO, String authToken, String publicToken) {
        ApprovalPushRequest payload = ApprovalPushRequest.builder()
                .agentId(agentDTO.getId())
                .approvedName(agentDTO.getAgentName())
                .authToken(authToken)
                .publicToken(publicToken)
                .publicId(agentDTO.getPublicId())
                .state(AgentState.APPROVED)
                .nexusPublicKey(rsaKeyConfig.getPublicKeyBase64())
                .build();

        if (webSocketHandler.isConnected(agentDTO.getId())) {
            pushApprovalViaWs(agentDTO, payload);
        } else {
            pushApprovalViaHttp(agentDTO, payload);
        }
    }

    public void pushRename(AgentDTO agentDTO) {
        if (webSocketHandler.isConnected(agentDTO.getId())) {
            pushRenameViaWs(agentDTO);
        } else {
            pushRenameViaHttp(agentDTO);
        }
    }

    private void pushApprovalViaWs(AgentDTO agentDTO, ApprovalPushRequest payload) {
        try {
            webSocketHandler.sendMessage(agentDTO.getId(), WsEnvelope.of(WsMessageType.APPROVAL_PUSH, payload));
            markPushed(agentDTO.getId());
            log.info("Approval pushed via WS to agent {}", agentDTO.getId());
        } catch (Exception e) {
            log.warn("WS approval push failed for agent {}, will retry: {}", agentDTO.getId(), e.getMessage());
        }
    }

    private void pushApprovalViaHttp(AgentDTO agentDTO, ApprovalPushRequest payload) {
        String url = buildUrl(agentDTO, "/api/approval");
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            httpSender.send(request);
            markPushed(agentDTO.getId());
            log.info("Approval pushed via HTTP to agent {} at {}", agentDTO.getId(), url);
        } catch (Exception e) {
            log.warn("HTTP approval push failed for agent {} at {}, will retry: {}", agentDTO.getId(), url, e.getMessage());
        }
    }

    private void pushRenameViaWs(AgentDTO agentDTO) {
        try {
            webSocketHandler.sendMessage(
                    agentDTO.getId(),
                    WsEnvelope.of(WsMessageType.RENAME_PUSH, AgentRenameResponse.builder()
                            .agentName(agentDTO.getAgentName())
                            .build())
            );
            log.info("Rename pushed via WS to agent {}: {}", agentDTO.getId(), agentDTO.getAgentName());
        } catch (Exception e) {
            log.warn("WS rename push failed for agent {}, falling back to HTTP: {}", agentDTO.getId(), e.getMessage());
            pushRenameViaHttp(agentDTO);
        }
    }

    private void pushRenameViaHttp(AgentDTO agentDTO) {
        String url = buildUrl(agentDTO, "/api/agents/rename");
        try {
            String body = objectMapper.writeValueAsString(
                    AgentRenameResponse.builder()
                            .agentName(agentDTO.getAgentName())
                            .build()
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            httpSender.send(request);
            log.info("Rename pushed via HTTP to agent {} at {}", agentDTO.getId(), url);
        } catch (Exception e) {
            log.warn("HTTP rename push failed for agent {} at {}: {}", agentDTO.getId(), url, e.getMessage());
        }
    }

    public void markPushed(UUID agentId) {
        agentRepository.markApprovalPushed(agentId);
    }

    private String buildUrl(AgentDTO agentDTO, String path) {
        return "http://" + agentDTO.getIpAddress() + ":" + agentDTO.getPort() + path;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAgentApproved(AgentApprovedEvent event) {
        pushApproval(event.agentDTO(), event.authToken(), event.publicToken());
        webSocketHandler.broadcastAgentJoined(event.agentDTO());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAgentRenamed(AgentRenamedEvent event) {
        pushRename(event.agentDTO());
    }
}