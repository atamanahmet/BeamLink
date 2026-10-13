package com.atamanahmet.beamlink.nexus.event;

import com.atamanahmet.beamlink.nexus.dto.AgentDTO;

public record AgentApprovedEvent(AgentDTO agentDTO, String authToken, String publicToken) {}