package com.atamanahmet.beamlink.nexus.dto;

import java.util.UUID;

/**
 * REST peer list item for nexus UI. No tokens.
 */
public record PeerSummaryResponse(
        UUID id,
        String deviceName,
        String ipAddress,
        int port,
        boolean online,
        Integer tlsPort,
        String tlsFingerprint
) {}
