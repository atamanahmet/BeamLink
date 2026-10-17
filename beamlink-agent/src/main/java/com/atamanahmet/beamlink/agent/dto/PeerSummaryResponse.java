package com.atamanahmet.beamlink.agent.dto;

import java.util.UUID;

/**
 * REST peer list item matching frontend PeerSummary.
 */
public record PeerSummaryResponse(
        UUID id,
        String agentName,
        String ipAddress,
        int port,
        boolean online
) {}
