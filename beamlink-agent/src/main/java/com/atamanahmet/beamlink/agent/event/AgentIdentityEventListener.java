package com.atamanahmet.beamlink.agent.event;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.config.NexusAddressHolder;
import com.atamanahmet.beamlink.agent.domain.Agent;
import com.atamanahmet.beamlink.agent.service.AgentService;
import com.atamanahmet.beamlink.agent.service.SseEmitters;
import com.atamanahmet.beamlink.agent.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AgentIdentityEventListener {

    private final SseEmitters sseEmitters;
    private final AgentService agentService;
    private final AgentConfig agentConfig;
    private final NexusAddressHolder nexusAddressHolder;
    private final StorageService storageService;

    @EventListener
    public void onApproved(AgentApprovedEvent event) {
        broadcastIdentity();
        broadcastStatus();
    }

    @EventListener
    public void onIdentityChanged(AgentIdentityChangedEvent event) {
        broadcastIdentity();
        broadcastStatus();
    }

    private void broadcastIdentity() {
        Agent agent = agentService.getAgent();
        sseEmitters.broadcast("identity_updated", Map.of(
                        "agentId", agent.getId() != null ? agent.getId().toString() : "",
                        "agentName", agent.getAgentName() != null ? agent.getAgentName() : "",
                        "state", agent.getState() != null ? agent.getState().name() : "")
        );
    }

    private void broadcastStatus() {
        sseEmitters.broadcast("agent_status_updated", Map.of(
                        "agentId", agentService.getAgentId() != null ? agentService.getAgentId().toString() : "",
                        "name", agentService.getAgentName() != null ? agentService.getAgentName() : "",
                        "port", agentConfig.getAgentPort(), "uploadDirectory", storageService.getUploadsDir().toString(),
                        "fileCount", storageService.getFileCount(),
                        "status",
                        "online",
                        "nexusUrl", nexusAddressHolder.getNexusUrl() != null ? nexusAddressHolder.getNexusUrl() : "")
        );
    }
}