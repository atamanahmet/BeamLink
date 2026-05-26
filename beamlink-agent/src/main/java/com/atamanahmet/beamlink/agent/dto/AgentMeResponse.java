package com.atamanahmet.beamlink.agent.dto;

import java.util.UUID;

public record AgentMeResponse(
        UUID agentId,
        String agentName,
        String state,
        String publicToken,
        String authToken
) {}