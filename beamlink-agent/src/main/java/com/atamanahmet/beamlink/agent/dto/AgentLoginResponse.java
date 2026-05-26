package com.atamanahmet.beamlink.agent.dto;

import java.util.UUID;

public record AgentLoginResponse(
        String token,
        UUID agentId,
        String agentName,
        String state,
        String publicToken,
        String authToken
) {}