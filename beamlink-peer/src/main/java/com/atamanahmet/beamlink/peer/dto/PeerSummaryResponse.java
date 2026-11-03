package com.atamanahmet.beamlink.peer.dto;

import java.util.UUID;

/**
 * REST peer list item matching frontend PeerSummary.
 */
public record PeerSummaryResponse(
        UUID id,
        String deviceName,
        String ipAddress,
        int port,
        boolean online
) {}
